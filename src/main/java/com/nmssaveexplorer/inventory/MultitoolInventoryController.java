package com.nmssaveexplorer.inventory;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class MultitoolInventoryController extends BaseInventoryController {
    @Override protected String getInventoryName() { return "Multitool"; }

    @Override
    protected JsonArray getSlotsArray(JsonObject root) {
        try {
            JsonObject base = root.getAsJsonObject("vLc");
            if (base == null) return new JsonArray();
            JsonObject player = base.getAsJsonObject("6f=");
            if (player == null) return new JsonArray();
            JsonObject weaponInventory = player.getAsJsonObject("Kgt");
            if (weaponInventory == null) return new JsonArray();
            JsonArray slots = weaponInventory.getAsJsonArray(":No");
            return slots != null ? slots : new JsonArray();
        } catch (Exception e) { return new JsonArray(); }
    }

    @Override
    protected JsonArray getValidSlotsArray(JsonObject root) {
        try {
            JsonObject base = root.getAsJsonObject("vLc");
            if (base == null) return new JsonArray();
            JsonObject player = base.getAsJsonObject("6f=");
            if (player == null) return new JsonArray();
            JsonObject weaponInventory = player.getAsJsonObject("Kgt");
            if (weaponInventory == null) return new JsonArray();
            JsonArray valid = weaponInventory.getAsJsonArray("hl?");
            return valid != null ? valid : new JsonArray();
        } catch (Exception e) { return new JsonArray(); }
    }
}
