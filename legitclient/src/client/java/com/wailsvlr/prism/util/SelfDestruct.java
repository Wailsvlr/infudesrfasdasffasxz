package com.wailsvlr.prism.util;

import com.wailsvlr.prism.PrismClient;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Finds and deletes the mod JAR and all config/settings traces.
 * The JVM keeps loaded classes in memory, so the game continues running.
 */
public final class SelfDestruct {
    private static Path jarPath;
    private static boolean jarDeleted;

    private SelfDestruct() {}

    /** Locates the JAR this class was loaded from. Call once at init. */
    public static void init() {
        try {
            var location = SelfDestruct.class.getProtectionDomain().getCodeSource().getLocation();
            if (location != null) {
                Path p = Path.of(location.toURI());
                if (Files.isRegularFile(p) && p.toString().endsWith(".jar")) {
                    jarPath = p;
                }
            }
        } catch (URISyntaxException | SecurityException e) {
            PrismClient.LOG.debug("Could not resolve JAR path");
        }
    }

    /** Deletes the mod JAR from the mods folder. Returns true if deleted or already gone. */
    public static boolean deleteJar() {
        if (jarDeleted) return true;
        if (jarPath == null) return false;
        try {
            Files.deleteIfExists(jarPath);
            jarDeleted = true;
            PrismClient.LOG.info("JAR removed");
            return true;
        } catch (IOException e) {
            // On Windows the JAR may be locked; schedule delete on exit
            try {
                jarPath.toFile().deleteOnExit();
                jarDeleted = true;
                return true;
            } catch (Exception ignored) {}
            return false;
        }
    }

    /** Deletes all config/settings files for the mod. */
    public static void deleteConfig() {
        try {
            Path configDir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir();

            // config/prismclient.json (main config saved by Config.java)
            Files.deleteIfExists(configDir.resolve("prismclient.json"));

            // Any other prism/limp files
            deleteMatching(configDir, "prism*");
            deleteMatching(configDir, "limp*");

        } catch (Exception e) {
            PrismClient.LOG.debug("Config cleanup incomplete");
        }
    }

    private static void deleteMatching(Path dir, String glob) throws IOException {
        if (!Files.isDirectory(dir)) return;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, glob)) {
            for (Path p : stream) {
                if (Files.isRegularFile(p)) Files.deleteIfExists(p);
            }
        }
    }

    public static boolean isJarDeleted() { return jarDeleted; }
    public static Path getJarPath() { return jarPath; }
}
