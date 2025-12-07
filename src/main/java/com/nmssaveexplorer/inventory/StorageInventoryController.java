package com.nmssaveexplorer.inventory;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class StorageInventoryController extends BaseInventoryController {
    
    private final int containerIndex;
    
    public StorageInventoryController(int containerIndex) {
        this.containerIndex = containerIndex;
    }

    @Override
    protected String getInventoryName() {
        return "Storage Container " + containerIndex;
    }

    @Override
    protected JsonArray getSlotsArray(JsonObject root) {
        JsonObject inventory = resolveContainerInventory(root);
        if (inventory == null) {
            return new JsonArray();
        }
        // ":No" is the key for "Slots"
        return inventory.has(":No") ? inventory.getAsJsonArray(":No") : new JsonArray();
    }

    @Override
    protected JsonArray getValidSlotsArray(JsonObject root) {
        JsonObject inventory = resolveContainerInventory(root);
        if (inventory == null) {
            return new JsonArray();
        }
        // "hl?" is the key for "ValidSlotIndices"
        return inventory.has("hl?") ? inventory.getAsJsonArray("hl?") : new JsonArray();
    }
    
    private JsonObject resolveContainerInventory(JsonObject root) {
        
        // Check mapping.json for the keys.
        // Chest1Inventory -> "3Nc"
        // Chest2Inventory -> "IDc"
        // Chest3Inventory -> "M=:"
        // Chest4Inventory -> "iYp"
        // Chest5Inventory -> "<IP"
        // Chest6Inventory -> "qYJ"
        // Chest7Inventory -> "@e5"
        // Chest8Inventory -> "5uh"
        // Chest9Inventory -> "5Tg"
        // Chest10Inventory -> "Bq<"
        
        String[] chestKeys = {
            "3Nc", // 0 (Chest 1)
            "IDc", // 1 (Chest 2)
            "M=:", // 2
            "iYp", // 3
            "<IP", // 4
            "qYJ", // 5
            "@e5", // 6
            "5uh", // 7
            "5Tg", // 8
            "Bq<"  // 9 (Chest 10)
        };
        
        if (containerIndex < 0 || containerIndex >= chestKeys.length) {
            return null;
        }
        
        String key = chestKeys[containerIndex];
        
        if (root == null) return null;
        
        JsonObject playerState = null;
        
        if (root.has("vLc")) {
            JsonObject base = root.getAsJsonObject("vLc");
            if (base.has("6f=")) {
                playerState = base.getAsJsonObject("6f=");
            }
        } else if (root.has("6f=")) {
            playerState = root.getAsJsonObject("6f=");
        } else {
            if (root.has(key)) {
                return root.getAsJsonObject(key);
            }
        }
        
        if (playerState != null && playerState.has(key)) {
            return playerState.getAsJsonObject(key);
        }
        
        return null;
    }
}
