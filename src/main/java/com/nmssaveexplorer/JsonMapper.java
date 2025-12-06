package com.nmssaveexplorer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class JsonMapper {

    // internal storage
    private static final Map<String, String> mapping = new HashMap<>();
    private static boolean loaded = false;

    /** Load mapping file (handles both flattened and legacy formats) */
    public static void loadMapping(File mappingFile) throws IOException {
        try (InputStream in = Files.newInputStream(mappingFile.toPath())) {
            loadMapping(in);
        }
    }

    /** Load mapping from any input stream (e.g., classpath resource in a fat jar). */
    public static void loadMapping(InputStream inputStream) throws IOException {
        try (InputStream in = inputStream) {
            mapping.clear();
            loaded = false;

            String jsonText = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            JsonElement root = JsonParser.parseString(jsonText);

            if (root.isJsonObject()) {
                JsonObject obj = root.getAsJsonObject();

                // Case 1: flattened map (no "Mapping" array)
                if (!obj.has("Mapping")) {
                    for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                        mapping.put(entry.getKey(), entry.getValue().getAsString());
                    }
                }
                // Case 2: legacy format with "Mapping" array
                else {
                    JsonArray arr = obj.getAsJsonArray("Mapping");
                    for (JsonElement e : arr) {
                        JsonObject pair = e.getAsJsonObject();
                        String key = pair.get("Key").getAsString();
                        String val = pair.get("Value").getAsString();
                        mapping.put(key, val);
                    }
                }
            }

            loaded = true;
            System.out.println("[JsonMapper] Flattened mapping entries: " + mapping.size());
        }
    }

    /** Map short → readable key; returns original if missing */
    public static String mapKey(String shortKey) {
        if (!loaded) return shortKey;
        return mapping.getOrDefault(shortKey, shortKey);
    }

    /** Returns true if a mapping file has been loaded */
    public static boolean isLoaded() {
        return loaded;
    }

    /** Returns total entries */
    public static int size() {
        return mapping.size();
    }

    /** Returns the full map (for reverse building) */
    public static Map<String, String> getMapping() {
        return mapping;
    }

    /** Simple debug printout */
    public static void debugReport(String tag) {
        System.out.printf("[JsonMapper:%s] loaded=%s entries=%d%n", tag, loaded, mapping.size());
    }
}
