package territories.equivalence;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The immutable pre-extraction golden store.
 *
 * <p>Goldens are captured once, from the build that exists before any class
 * moves, and are never regenerated to make a difference disappear. A golden
 * later found to be wrong is a bug report against the shipped plugin and is
 * fixed as its own change with its own release note.
 *
 * <p>The complete configuration cross-product is gated by a digest per case —
 * a digest over the full dump, so it is exactly as strict as comparing the
 * dumps themselves. The curated canonical subset additionally keeps its full
 * dump on disk, so a digest mismatch can be localised to a line without
 * needing the old build back.
 *
 * <p>Capture mode is deliberately awkward to reach: pass
 * {@code -Dterritories.golden.capture=true}. It refuses to overwrite an
 * existing golden file.
 */
final class Golden {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private final String setName;
    private final Map<String, String> digests = new LinkedHashMap<String, String>();
    private final Map<String, String> dumps = new LinkedHashMap<String, String>();
    private final String requestedDump;

    Golden(String setName) {
        this.setName = setName;
        this.requestedDump = System.getProperty("territories.golden.dump");
    }

    static boolean capturing() {
        return Boolean.getBoolean("territories.golden.capture");
    }

    static File root() {
        String configured = System.getProperty("territories.project.basedir");
        File base = new File(
                configured == null || configured.trim().isEmpty()
                        ? System.getProperty("user.dir") : configured);
        if (!new File(base, "pom.xml").isFile()) {
            throw new IllegalStateException(
                    "cannot locate the project directory from " + base.getAbsolutePath()
                            + "; set -Dterritories.project.basedir");
        }
        return new File(new File(base, "golden"), "pre-extraction");
    }

    /** Records one case. {@code keepDump} stores the readable full dump too. */
    void record(String caseName, String dump, boolean keepDump) {
        if (digests.put(caseName, digest(dump)) != null) {
            throw new IllegalStateException("duplicate golden case name: " + caseName);
        }
        if (keepDump) dumps.put(caseName, dump);
        if (caseName.equals(requestedDump)) {
            System.out.println("---- " + caseName + " ----");
            System.out.println(dump);
        }
    }

    /**
     * Writes the goldens in capture mode, or verifies every recorded case
     * against them. Every mismatch is collected before failing, so one run
     * gives the whole picture rather than the first difference.
     */
    List<String> verifyOrCapture() throws IOException {
        File directory = root();
        File digestFile = new File(directory, "digests-" + setName + ".txt");
        File dumpDirectory = new File(directory, "dumps");
        if (capturing()) {
            capture(directory, digestFile, dumpDirectory);
            return new ArrayList<String>();
        }
        if (!digestFile.isFile()) {
            throw new IllegalStateException(
                    "golden file is missing: " + digestFile.getAbsolutePath()
                            + " — capture it from the pre-extraction build with"
                            + " -Dterritories.golden.capture=true");
        }

        Map<String, String> expected = readDigests(digestFile);
        ArrayList<String> failures = new ArrayList<String>();
        Iterator<Map.Entry<String, String>> actual = digests.entrySet().iterator();
        while (actual.hasNext()) {
            Map.Entry<String, String> entry = actual.next();
            String name = entry.getKey();
            String golden = expected.remove(name);
            if (golden == null) {
                failures.add("case not present in the golden set: " + name);
                continue;
            }
            if (golden.equals(entry.getValue())) continue;
            failures.add(describe(name, dumpDirectory));
        }
        Iterator<String> missing = expected.keySet().iterator();
        while (missing.hasNext()) {
            failures.add("golden case was not produced by this build: " + missing.next());
        }
        return failures;
    }

    private String describe(String caseName, File dumpDirectory) {
        File dumpFile = new File(dumpDirectory, caseName + ".txt");
        String candidate = dumps.get(caseName);
        if (candidate == null || !dumpFile.isFile()) {
            return "output moved (digest only): " + caseName
                    + " — re-run with -Dterritories.golden.dump=" + caseName
                    + " to print the current dump";
        }
        String golden;
        try {
            golden = new String(Files.readAllBytes(dumpFile.toPath()), UTF8);
        } catch (IOException unreadable) {
            return "output moved: " + caseName + " (golden dump unreadable: "
                    + unreadable.getMessage() + ")";
        }
        String[] goldenLines = golden.split("\n", -1);
        String[] candidateLines = candidate.split("\n", -1);
        int limit = Math.min(goldenLines.length, candidateLines.length);
        for (int i = 0; i < limit; i++) {
            if (goldenLines[i].equals(candidateLines[i])) continue;
            return "output moved: " + caseName + " line " + (i + 1)
                    + "\n    golden: " + goldenLines[i]
                    + "\n    now   : " + candidateLines[i];
        }
        return "output moved: " + caseName + " (line count "
                + goldenLines.length + " -> " + candidateLines.length + ")";
    }

    private void capture(File directory, File digestFile, File dumpDirectory)
            throws IOException {
        if (digestFile.isFile()) {
            throw new IllegalStateException(
                    "goldens are immutable and already exist: "
                            + digestFile.getAbsolutePath());
        }
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("could not create " + directory.getAbsolutePath());
        }
        if (!dumpDirectory.isDirectory() && !dumpDirectory.mkdirs()) {
            throw new IOException("could not create " + dumpDirectory.getAbsolutePath());
        }
        StringBuilder builder = new StringBuilder(digests.size() * 80);
        Iterator<Map.Entry<String, String>> entries = digests.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<String, String> entry = entries.next();
            builder.append(entry.getKey()).append(' ').append(entry.getValue()).append('\n');
        }
        Files.write(digestFile.toPath(), builder.toString().getBytes(UTF8));
        Iterator<Map.Entry<String, String>> stored = dumps.entrySet().iterator();
        while (stored.hasNext()) {
            Map.Entry<String, String> entry = stored.next();
            Files.write(
                    new File(dumpDirectory, entry.getKey() + ".txt").toPath(),
                    entry.getValue().getBytes(UTF8));
        }
        System.out.println("captured " + digests.size() + " golden digests and "
                + dumps.size() + " dumps into " + directory.getAbsolutePath());
    }

    private static Map<String, String> readDigests(File file) throws IOException {
        LinkedHashMap<String, String> result = new LinkedHashMap<String, String>();
        List<String> lines = Files.readAllLines(file.toPath(), UTF8);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) continue;
            int space = line.lastIndexOf(' ');
            if (space < 0) {
                throw new IOException("malformed golden line " + (i + 1) + " in " + file);
            }
            result.put(line.substring(0, space), line.substring(space + 1));
        }
        return result;
    }

    static String digest(String value) {
        try {
            return Dump.hex(MessageDigest.getInstance("SHA-256").digest(Dump.utf8(value)));
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException("SHA-256 is required", missing);
        }
    }

    int size() {
        return digests.size();
    }
}
