package com.nmssaveexplorer;

/**
 * Plain Java entry point that delegates to the JavaFX {@link Main} class.
 * Using a non-JavaFX launcher keeps the JVM from short-circuiting with the
 * "JavaFX runtime components are missing" error when running the shaded JAR.
 */
public final class AppLauncher {

    private AppLauncher() {
        // no instances
    }

    public static void main(String[] args) {
        Main.main(args);
    }
}
