package com.nmssaveexplorer;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class SaveLoader {

    public static Map<String, JsonObject> loadAllSaves(String folderPath) throws Exception {
        Map<String, JsonObject> saves = new HashMap<>();

        File folder = new File(folderPath);
        if (!folder.exists() || !folder.isDirectory()) {
            throw new Exception("Save folder not found: " + folderPath);
        }

        File[] files = folder.listFiles();
        if (files == null) {
            throw new Exception("No files in folder: " + folderPath);
        }

        for (File file : files) {
            if (file.getName().endsWith(".hg")) {
                try {
                    String decompressed = SaveDecoder.decodeSave(file);
                    decompressed = cleanJson(decompressed);
                    JsonObject parsed = JsonParser.parseString(decompressed).getAsJsonObject();
                    saves.put(file.getName(), parsed);
                } catch (Exception e) {
                    System.out.println("Warning: Failed to load " + file.getName() + " - " + e.getMessage());
                }
            }
        }

        return saves;
    }

    public static Map<String, JsonObject> loadMostRecentSave(String folderPath) throws Exception {
        Map<String, JsonObject> saves = new HashMap<>();

        File folder = new File(folderPath);
        if (!folder.exists() || !folder.isDirectory()) {
            throw new Exception("Save folder not found: " + folderPath);
        }

        //File[] files = folder.listFiles((dir, name) -> name.endsWith(".hg"));
        // only grab saveX.hg, not mf_saveX.hg
        File[] files = folder.listFiles((dir, name) ->
                name.matches("save\\d+\\.hg")
        );
        if (files == null || files.length == 0) {
            throw new Exception("No .hg save files found in folder: " + folderPath);
        }

        // Find the most recently modified save file
        File latestFile = null;
        long latestModified = Long.MIN_VALUE;
        for (File file : files) {
            if (file.lastModified() > latestModified) {
                latestModified = file.lastModified();
                latestFile = file;
            }
        }

        if (latestFile == null) {
            throw new Exception("No valid save files found.");
        }

        try {
            String decompressed = SaveDecoder.decodeSave(latestFile);
            decompressed = cleanJson(decompressed);
            JsonObject parsed = JsonParser.parseString(decompressed).getAsJsonObject();
            saves.put(latestFile.getName(), parsed);
        } catch (Exception e) {
            System.out.println("Warning: Failed to load " + latestFile.getName() + " - " + e.getMessage());
        }

        return saves;
    }

    private static String cleanJson(String json) {
        json = json.replace("\\u0000", "");
        int lastGood = Math.max(json.lastIndexOf('}'), json.lastIndexOf(']'));
        if (lastGood != -1) {
            json = json.substring(0, lastGood + 1);
        }
        return json;
    }
}
