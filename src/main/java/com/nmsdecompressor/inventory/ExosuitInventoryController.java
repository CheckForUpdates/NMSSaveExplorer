package com.nmsdecompressor.inventory;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class ExosuitInventoryController extends BaseInventoryController {
    @Override protected String getInventoryName() { return "Exosuit"; }

    @Override
    protected JsonArray getSlotsArray(JsonObject root) {
        try {
            JsonObject base = root.getAsJsonObject("vLc");
            if (base == null) return new JsonArray();
            JsonObject player = base.getAsJsonObject("6f=");
            if (player == null) return new JsonArray();
            JsonObject inventory = player.getAsJsonObject(";l5");
            if (inventory == null) return new JsonArray();
            JsonArray slots = inventory.getAsJsonArray(":No");
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
            JsonObject inventory = player.getAsJsonObject(";l5");
            if (inventory == null) return new JsonArray();
            JsonArray valid = inventory.getAsJsonArray("hl?");
            return valid != null ? valid : new JsonArray();
        } catch (Exception e) { return new JsonArray(); }
    }
}
