package territories;

import ij.plugin.frame.Recorder;

import java.awt.Component;
import java.awt.Container;
import java.awt.TextArea;

/**
 * The macro line a dialog run adds to the Macro Recorder, taken back again if
 * the run does not complete.
 *
 * <p>The line has to be recorded before the run starts: result windows that
 * open during the run make ImageJ record {@code selectImage("...")} lines, and
 * a macro that selects its results before the command that makes them cannot
 * be replayed. ImageJ has no call to un-record, so a failed or cancelled run
 * removes its own line from the Recorder's text, leaving everything else
 * there untouched.
 */
final class RecordedRun {

    private final String line;

    private RecordedRun(String line) {
        this.line = line;
    }

    /** Records {@code line} when the Recorder is on; the result is never null. */
    static RecordedRun record(String line) {
        if (!Recorder.record) return new RecordedRun(null);
        // Stop ImageJ adding its own bare run("...") line when the command ends.
        Recorder.setCommand(null);
        Recorder.recordString(line);
        return new RecordedRun(line);
    }

    /** Removes the recorded line again; does nothing if nothing was recorded. */
    void takeBack() {
        if (line == null) return;
        Recorder recorder = Recorder.getInstance();
        if (recorder == null) return;
        TextArea area = textArea(recorder);
        if (area == null) return;
        String text = area.getText();
        String kept = withoutLastLine(text, line);
        if (!kept.equals(text)) area.setText(kept);
    }

    /**
     * {@code text} without the last occurrence of {@code line} (and its line
     * ending, {@code \n} or {@code \r\n}); {@code text} itself when absent.
     */
    static String withoutLastLine(String text, String line) {
        if (text == null || line == null) return text;
        String body = line.endsWith("\n") ? line.substring(0, line.length() - 1) : line;
        if (body.isEmpty()) return text;
        int start = text.lastIndexOf(body);
        if (start < 0) return text;
        if (start > 0 && text.charAt(start - 1) != '\n') return text;
        int end = start + body.length();
        if (text.startsWith("\r\n", end)) end += 2;
        else if (text.startsWith("\n", end)) end += 1;
        else if (end != text.length()) return text;
        return text.substring(0, start) + text.substring(end);
    }

    private static TextArea textArea(Container container) {
        for (Component component : container.getComponents()) {
            if (component instanceof TextArea) return (TextArea) component;
            if (component instanceof Container) {
                TextArea nested = textArea((Container) component);
                if (nested != null) return nested;
            }
        }
        return null;
    }
}
