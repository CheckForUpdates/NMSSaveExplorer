package com.nmssaveexplorer.inventory;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class ShipTechInventoryController extends ShipInventoryController {

    @Override
    protected String getInventoryName() {
        return "Ship Technology";
    }

    @Override
    protected JsonArray getSlotsArray(JsonObject root) {
        JsonObject techInventory = resolveShipTechInventory(root);
        if (techInventory == null) {
            return new JsonArray();
        }
        JsonArray slots = techInventory.getAsJsonArray(":No");
        return slots != null ? slots : new JsonArray();
    }

    @Override
    protected JsonArray getValidSlotsArray(JsonObject root) {
        JsonObject techInventory = resolveShipTechInventory(root);
        if (techInventory == null) {
            return new JsonArray();
        }
        JsonArray valid = techInventory.getAsJsonArray("hl?");
        return valid != null ? valid : new JsonArray();
    }

    private JsonObject resolveShipTechInventory(JsonObject root) {
        JsonObject ship = resolveShipOwner(root);
        if (ship == null) {
            return null;
        }
        JsonObject tech = ship.getAsJsonObject("PMT");
        if (tech == null && ship.has("0wS")) {
            tech = ship.getAsJsonObject("0wS");
        }
        return tech;
    }
}
