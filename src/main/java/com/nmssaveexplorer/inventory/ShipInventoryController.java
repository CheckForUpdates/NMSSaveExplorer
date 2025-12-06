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

    protected JsonObject resolveShipInventory(JsonObject root) {
        JsonObject ship = resolveShipOwner(root);
        if (ship == null) {
            return null;
        }
        JsonObject inventory = null;
        if (ship.has(":No")) {
            inventory = ship;
        } else if (ship.has(";l5")) {
            inventory = ship.getAsJsonObject(";l5");
        }
        if (hasSlots(inventory)) {
            return inventory;
        }
        return inventory;
    }

    protected JsonObject resolveShipOwner(JsonObject root) {
        try {
            JsonObject base = root.getAsJsonObject("vLc");
            if (base == null) return null;
            JsonObject player = base.getAsJsonObject("6f=");
            if (player == null) return null;

            JsonArray ownership = player.getAsJsonArray("@Cs");
            if (ownership != null && ownership.size() > 0) {
                int activeIndex = player.has("aBE") ? player.get("aBE").getAsInt() : 0;
                JsonObject active = extractShipFromOwnership(ownership, activeIndex);
                if (active != null) {
                    JsonObject inventory = active.getAsJsonObject(";l5");
                    if (hasSlots(inventory)) {
                        return active;
                    }
                }
                // Fallback: first ship with any slots.
                for (JsonElement element : ownership) {
                    JsonObject candidate = extractShip(element);
                    if (candidate != null) {
                        JsonObject inventory = candidate.getAsJsonObject(";l5");
                        if (hasSlots(inventory)) {
                            return candidate;
                        }
                    }
                }
            }

            // Fallback to legacy ShipInventory node on the player.
            JsonObject shipInventory = player.getAsJsonObject("6<E");
            if (shipInventory != null && hasSlots(shipInventory)) {
                return shipInventory;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private JsonObject extractShipFromOwnership(JsonArray ownership, int index) {
        if (index < 0 || index >= ownership.size()) {
            index = 0;
        }
        JsonElement element = ownership.get(index);
        return extractShip(element);
    }

    private JsonObject extractShip(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        return element.getAsJsonObject();
    }

    protected boolean hasSlots(JsonObject inventory) {
        if (inventory == null) return false;
        JsonArray slots = inventory.getAsJsonArray(":No");
        return slots != null && slots.size() > 0;
    }
}
