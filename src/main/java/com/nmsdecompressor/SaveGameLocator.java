package com.nmsdecompressor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Utility to discover No Man's Sky save files across supported platforms.
 */
public final class SaveGameLocator {

    private static final int SEARCH_DEPTH = 4;

    private SaveGameLocator() {
        // utility
    }

    public static List<SaveFile> discoverSaves() {
        Map<Path, SaveFile> result = new LinkedHashMap<>();

        for (Path root : candidateRoots()) {
            if (root == null || !Files.isDirectory(root)) {
                continue;
            }

            try (Stream<Path> stream = Files.walk(root, SEARCH_DEPTH)) {
                stream.filter(Files::isRegularFile)
                      .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".hg"))
                      .sorted()
                      .forEach(path -> result.putIfAbsent(path.toAbsolutePath(), new SaveFile(path.toAbsolutePath(), root)));
            } catch (Exception ignored) {
                // Ignore inaccessible roots; caller can present fallback options.
            }
        }

        return new ArrayList<>(result.values());
    }

    private static List<Path> candidateRoots() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String userHome = System.getProperty("user.home", "");

        List<Path> roots = new ArrayList<>();

        if (osName.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isBlank()) {
                roots.add(Paths.get(appData, "HelloGames", "NMS"));
            }
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null && !localAppData.isBlank()) {
                roots.add(Paths.get(localAppData, "HelloGames", "NMS"));
            }
        } else if (osName.contains("mac")) {
            roots.add(Paths.get(userHome, "Library", "Application Support", "HelloGames", "NMS"));
        } else {
            // Default to Linux/Steam Proton layout
            Path protonRoot = Paths.get(userHome, ".local", "share", "Steam", "steamapps", "compatdata", "275850", "pfx", "drive_c", "users");
            if (Files.isDirectory(protonRoot)) {
                try (Stream<Path> users = Files.list(protonRoot)) {
                    Set<Path> userDirs = users.collect(Collectors.toSet());
                    for (Path user : userDirs) {
                        roots.add(user.resolve(Paths.get("AppData", "Roaming", "HelloGames", "NMS")));
                    }
                } catch (Exception ignored) {
                    // ignore
                }
            }
            roots.add(Paths.get(userHome, ".config", "HelloGames", "NMS"));
            roots.add(Paths.get(userHome, ".local", "share", "HelloGames", "NMS"));
        }

        return roots.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public record SaveFile(Path path, Path root) {
        public String displayName() {
            if (root != null && path.startsWith(root)) {
                Path relative = root.relativize(path);
                if (relative.getNameCount() > 0) {
                    return relative.toString();
                }
            }
            return path.getFileName().toString();
        }

        public String rootDisplay() {
            return (root != null) ? root.toString() : "";
        }
    }
}
