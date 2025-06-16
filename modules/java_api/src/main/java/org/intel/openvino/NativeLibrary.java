package org.intel.openvino;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.util.logging.Logger;

/** A utility class to load the OpenVINO native libraries required for the OpenVINO Runtime API. */
public final class NativeLibrary {

    public static final String NATIVE_LIBRARY_NAME = "inference_engine_java_api";
    private static final Logger logger = Logger.getLogger(NativeLibrary.class.getName());

    static {
        try {
            System.loadLibrary(NATIVE_LIBRARY_NAME);
        } catch (UnsatisfiedLinkError e) {
            try {
                loadNativeLibs();
            } catch (Exception ex) {
                logger.warning("Failed to load OpenVINO native libraries");
                throw ex;
            }
        }
    }

    private static String getNativesFolder() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            return "windows-x86_64";
        }

        if (osName.contains("mac")) {
            return "macos-arm64";
        }

        return "linux-x86_64";
    }

    private static String getLibraryName(String name) {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            return ("tbb".equals(name) ? name + "12" : name) + ".dll";
        }

        if (osName.contains("mac")) {
            return "lib" + name + ".dylib";
        }

        return "lib" + name + ".so";
    }

    /**
     * Use this method to initialize native libraries. Other files like plugins.xml and *.mvcmd will
     * be also copied to temporal location which makes them visible in runtime. To Do: fix for
     * Android!
     */
    public static void loadNativeLibs() {
        // A set of required libraries which are listed in dependency order.
        final String[] nativeLibs = {"tbb", "tbbmalloc", "openvino", "inference_engine_java_api"};

        InputStream resources_list = null;
        try {
            String nativesFolder = getNativesFolder();
            // Get a list of all native resources (libraries, plugins and other files).
            resources_list =
                    NativeLibrary.class.getClassLoader().getResourceAsStream(nativesFolder + "/resources_list.txt");
            BufferedReader r = new BufferedReader(new InputStreamReader(resources_list));

            // Create a temporal folder to unpack native files.
            File tmpDir = Files.createTempDirectory("openvino-native").toFile();
            tmpDir.deleteOnExit();

            String file;
            while ((file = r.readLine()) != null) {
                // Check that file name is valid
                if (!file.chars()
                        .allMatch(
                                c ->
                                        Character.isLetterOrDigit(c)
                                                || c == '.'
                                                || c == '_'
                                                || c == '-')) {
                    throw new IOException("Invalid file path: " + file);
                }

                URL url = NativeLibrary.class.getClassLoader().getResource(nativesFolder + "/" + file);
                if (url == null) {
                    logger.warning("Resource not found: " + file);
                    continue;
                }
                File nativeLibTmpFile = new File(tmpDir, file);
                nativeLibTmpFile.deleteOnExit();
                try (InputStream in = url.openStream()) {
                    Files.copy(in, nativeLibTmpFile.toPath());
                }
            }

            // Load native libraries.
            for (String lib : nativeLibs) {
                lib = getLibraryName(lib);
                File nativeLibTmpFile = new File(tmpDir, lib);
                try {
                    System.load(nativeLibTmpFile.getAbsolutePath());
                } catch (UnsatisfiedLinkError ex) {
                    logger.warning("Failed to load library " + file + ": " + ex);
                }
            }
        } catch (IOException ex) {
            logger.warning("Failed to load native Inference Engine libraries: " + ex.getMessage());
        } finally {
            if (resources_list != null) {
                try {
                    resources_list.close();
                } catch (IOException ex) {
                    logger.warning("Failed to read native libraries list");
                }
            }
        }
    }
}
