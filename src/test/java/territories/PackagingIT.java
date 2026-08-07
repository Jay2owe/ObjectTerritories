package territories;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class PackagingIT {

    @Test
    public void packagedJarContainsPrivateCoreLicenceAndPluginEntries() throws Exception {
        File project = new File(requiredProperty("territories.project.basedir"));
        File jarPath = new File(requiredProperty("territories.project.jar"));
        assertTrue(jarPath.isFile());

        byte[] expectedLicence = Files.readAllBytes(new File(project, "LICENSE").toPath());
        JarFile jar = new JarFile(jarPath);
        try {
            JarEntry licence = jar.getJarEntry("META-INF/LICENSE");
            assertNotNull(licence);
            assertArrayEquals(expectedLicence, read(jar.getInputStream(licence)));
            assertNotNull(jar.getJarEntry("plugins.config"));
            assertNotNull(jar.getJarEntry("territories/Object_Territories.class"));
            assertNotNull(jar.getJarEntry("territories/Object_Territories_Batch.class"));
            assertNotNull(jar.getJarEntry(
                    "territories/internal/core/io/RegexGroupDiscovery.class"));
            assertTrue(jar.getJarEntry(
                    "sc/fiji/oc3d/core/io/RegexGroupDiscovery.class") == null);
            assertNotNull(jar.getJarEntry(
                    "territories/internal/shaded/jts/geom/Geometry.class"));
            assertTrue(jar.getJarEntry("org/locationtech/jts/geom/Geometry.class") == null);
            assertTrue(jar.getJarEntry("ij/IJ.class") == null);

            Attributes attributes = jar.getManifest().getMainAttributes();
            assertEquals(gitHead(project), attributes.getValue("Implementation-Build"));
        } finally {
            jar.close();
        }
    }

    @Test
    public void packagedJarRunsBatchPreviewWithoutExternalCoreOrJtsJars() throws Exception {
        File jarPath = new File(requiredProperty("territories.project.jar"));
        File imageJ = new File(ij.IJ.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        File root = Files.createTempDirectory("territories-packaged-batch").toFile();
        File input = new File(root, "input");
        File output = new File(root, "output");
        File regions = new File(root, "regions.roi");
        assertTrue(input.mkdir());
        assertTrue(output.mkdir());
        assertTrue(regions.createNewFile());
        assertTrue(new File(input, "sample_A.tif").createNewFile());
        assertTrue(new File(input, "sample_B.tif").createNewFile());

        URLClassLoader loader = new URLClassLoader(
                new URL[]{jarPath.toURI().toURL(), imageJ.toURI().toURL()}, null);
        try {
            Class<?> parametersClass = loader.loadClass(
                    "territories.batch.ObjectTerritoriesBatchParameters");
            Object builder = parametersClass.getMethod(
                            "builder", File.class, String.class, Integer.TYPE,
                            File.class, File.class)
                    .invoke(null, input, "(.+)_([AB])\\.tif", 2, regions, output);
            builder.getClass().getMethod("recursive", Boolean.TYPE)
                    .invoke(builder, false);
            Object parameters = builder.getClass().getMethod("build").invoke(builder);
            Class<?> runner = loader.loadClass(
                    "territories.batch.ObjectTerritoriesBatchRunner");
            String preview = (String) runner.getMethod("preview", parametersClass)
                    .invoke(null, parameters);

            assertTrue(preview.contains("1 group(s), 1 runnable, 2 files"));
            assertTrue(preview.contains("[A] sample_A.tif"));
            assertNotNull(loader.loadClass(
                    "territories.internal.core.io.RegexGroupDiscovery"));
            assertNotNull(loader.loadClass(
                    "territories.internal.shaded.jts.geom.Geometry"));
        } finally {
            loader.close();
            delete(root);
        }
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("Required test property is missing: " + name);
        }
        return value;
    }

    private static String gitHead(File project) throws Exception {
        Process process = new ProcessBuilder("git", "rev-parse", "HEAD")
                .directory(project)
                .redirectErrorStream(true)
                .start();
        String output;
        try {
            output = new String(read(process.getInputStream()), "UTF-8").trim();
        } finally {
            process.getInputStream().close();
        }
        assertEquals(0, process.waitFor());
        return output;
    }

    private static byte[] read(InputStream stream) throws Exception {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = stream.read(buffer)) >= 0) {
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } finally {
            stream.close();
        }
    }

    private static void delete(File file) throws Exception {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) delete(child);
            }
        }
        Files.deleteIfExists(file.toPath());
    }
}
