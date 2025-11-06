package com.nmssaveexplorer.inventory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class ShipInventoryController extends BaseInventoryController {
    @Override protected String getInventoryName() { return "Ship"; }

    @Override
    protected JsonArray getSlotsArray(JsonObject root) {
        JsonObject inventory = resolveShipInventory(root);
        if (inventory == null) {
            return new JsonArray();
        }
        JsonArray slots = inventory.getAsJsonArray(":No");
        return slots != null ? slots : new JsonArray();
    }

    @Override
    protected JsonArray getValidSlotsArray(JsonObject root) {
        JsonObject inventory = resolveShipInventory(root);
        if (inventory == null) {
            return new JsonArray();
        }
        JsonArray valid = inventory.getAsJsonArray("hl?");
        return valid != null ? valid : new JsonArray();
    }

    private JsonObject resolveShipInventory(JsonObject root) {
        try {
            JsonObject base = root.getAsJsonObject("vLc");
            if (base == null) return null;
            JsonObject player = base.getAsJsonObject("6f=");
            if (player == null) return null;

            JsonArray ownership = player.getAsJsonArray("@Cs");
            if (ownership != null && ownership.size() > 0) {
                int activeIndex = player.has("aBE") ? player.get("aBE").getAsInt() : 0;
                JsonObject active = extractInventoryFromOwnership(ownership, activeIndex);
                if (active != null && hasSlots(active)) {
                    return active;
                }
                // Fallback: first ship with any slots.
                for (JsonElement element : ownership) {
                    JsonObject candidate = extractInventory(element);
                    if (candidate != null && hasSlots(candidate)) {
                        return candidate;
                    }
                }
            }

            // Fallback to legacy ShipInventory node on the player.
            JsonObject shipInventory = player.getAsJsonObject("6<E");
            if (shipInventory != null && hasSlots(shipInventory)) {
                return shipInventory;
            }
            return shipInventory;
        } catch (Exception e) {
            return null;
        }
    }

    private JsonObject extractInventoryFromOwnership(JsonArray ownership, int index) {
        if (index < 0 || index >= ownership.size()) {
            index = 0;
        }
        JsonElement element = ownership.get(index);
        return extractInventory(element);
    }

    private JsonObject extractInventory(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        JsonObject owner = element.getAsJsonObject();
        JsonObject inventory = owner.getAsJsonObject(";l5");
        return inventory;
    }

    private boolean hasSlots(JsonObject inventory) {
        if (inventory == null) return false;
        JsonArray slots = inventory.getAsJsonArray(":No");
        return slots != null && slots.size() > 0;
    }
}
