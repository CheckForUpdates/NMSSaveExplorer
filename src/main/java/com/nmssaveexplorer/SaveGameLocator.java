package com.nmssaveexplorer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
        OsType osType = OsType.detect();
        Map<String, SaveFile> result = new LinkedHashMap<>();
        boolean caseInsensitiveFs = osType == OsType.WINDOWS || osType == OsType.MAC;

        for (Path root : candidateRoots(osType)) {
            if (root == null || !Files.isDirectory(root)) {
                continue;
            }

            scanForSaves(root, root, caseInsensitiveFs, result);
            scanSteamProfileFolders(root, caseInsensitiveFs, result);
        }

        return new ArrayList<>(result.values());
    }

    private static List<Path> candidateRoots(OsType osType) {
        LinkedHashSet<Path> roots = new LinkedHashSet<>();

        switch (osType) {
            case WINDOWS -> roots.addAll(windowsCandidates());
            case MAC -> roots.addAll(macCandidates());
            case LINUX -> roots.addAll(linuxCandidates());
            case UNKNOWN -> {}
        }

        return roots.stream()
                .filter(Objects::nonNull)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    private static List<Path> windowsCandidates() {
        List<Path> roots = new ArrayList<>();
        addEnvPath(roots, "APPDATA", "HelloGames", "NMS");
        addEnvPath(roots, "LOCALAPPDATA", "HelloGames", "NMS");

        String userHome = System.getProperty("user.home", "");
        if (!userHome.isBlank()) {
            Path home = Paths.get(userHome);
            roots.add(home.resolve(Paths.get("Saved Games", "HelloGames", "NMS")));
            roots.add(home.resolve(Paths.get("Documents", "HelloGames", "NMS")));
        }

        return roots;
    }

    private static List<Path> macCandidates() {
        String userHome = System.getProperty("user.home", "");
        List<Path> roots = new ArrayList<>();
        if (!userHome.isBlank()) {
            roots.add(Paths.get(userHome, "Library", "Application Support", "HelloGames", "NMS"));
        }
        return roots;
    }

    private static List<Path> linuxCandidates() {
        List<Path> roots = new ArrayList<>();
        String userHome = System.getProperty("user.home", "");
        if (!userHome.isBlank()) {
            Path home = Paths.get(userHome);
            roots.add(home.resolve(Paths.get(".local", "share", "HelloGames", "NMS")));
            roots.add(home.resolve(Paths.get(".config", "HelloGames", "NMS")));

            List<Path> steamRoots = List.of(
                    home.resolve(Paths.get(".steam", "steam")),
                    home.resolve(Paths.get(".steam", "root")),
                    home.resolve(Paths.get(".local", "share", "Steam"))
            );
            for (Path steamRoot : steamRoots) {
                roots.addAll(protonCandidates(steamRoot));
            }
        }

        return roots;
    }

    private static List<Path> protonCandidates(Path steamRoot) {
        List<Path> roots = new ArrayList<>();
        if (steamRoot == null) {
            return roots;
        }

        Path compatData = steamRoot.resolve(Paths.get("steamapps", "compatdata", "275850"));
        if (!Files.isDirectory(compatData)) {
            return roots;
        }

        Path usersRoot = compatData.resolve(Paths.get("pfx", "drive_c", "users"));
        if (!Files.isDirectory(usersRoot)) {
            return roots;
        }

        try (Stream<Path> users = Files.list(usersRoot)) {
            users.filter(Files::isDirectory)
                 .map(user -> user.resolve(Paths.get("Application Data", "HelloGames", "NMS")))
                 .forEach(roots::add);
        } catch (IOException ignored) {
            // best effort only
        }

        return roots;
    }

    private static void addEnvPath(List<Path> roots, String envVar, String... more) {
        String base = System.getenv(envVar);
        if (base != null && !base.isBlank()) {
            roots.add(Paths.get(base, more));
        }
    }

    private static void scanSteamProfileFolders(Path root, boolean caseInsensitiveFs, Map<String, SaveFile> result) {
        try (Stream<Path> children = Files.list(root)) {
            children.filter(Files::isDirectory)
                    .filter(child -> {
                        String name = child.getFileName().toString().toLowerCase(Locale.ROOT);
                        return name.startsWith("st_");
                    })
                    .forEach(profileDir -> scanForSaves(profileDir, root, caseInsensitiveFs, result));
        } catch (IOException ignored) {
            // Optional roots; ignore errors.
        }
    }

    private static void scanForSaves(Path searchRoot, Path displayRoot, boolean caseInsensitiveFs, Map<String, SaveFile> result) {
        try (Stream<Path> stream = Files.walk(searchRoot, SEARCH_DEPTH)) {
            stream.filter(Files::isRegularFile)
                  .filter(path -> {
                      String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                      return name.endsWith(".hg") && name.startsWith("save");
                  })
                  .sorted()
                  .forEach(path -> {
                      Path canonical = canonicalPath(path);
                      String key = pathKey(canonical, caseInsensitiveFs);
                      if (key != null) {
                          result.putIfAbsent(key, new SaveFile(canonical, displayRoot));
                      }
                  });
        } catch (Exception ignored) {
            // Ignore inaccessible roots; caller can present fallback options.
        }
    }

    private static Path canonicalPath(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException ex) {
            return path.toAbsolutePath().normalize();
        }
    }

    private static String pathKey(Path path, boolean caseInsensitive) {
        if (path == null) {
            return null;
        }
        String key = path.toString();
        return caseInsensitive ? key.toLowerCase(Locale.ROOT) : key;
    }

    private enum OsType {
        WINDOWS, MAC, LINUX, UNKNOWN;

        static OsType detect() {
            String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (osName.contains("win")) {
                return WINDOWS;
            }
            if (osName.contains("mac")) {
                return MAC;
            }
            if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix") || osName.contains("linux")) {
                return LINUX;
            }
            return UNKNOWN;
        }
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
