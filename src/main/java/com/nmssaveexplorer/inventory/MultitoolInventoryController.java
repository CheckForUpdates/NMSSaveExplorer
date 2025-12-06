package com.nmssaveexplorer.inventory;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class MultitoolInventoryController extends BaseInventoryController {
    @Override protected String getInventoryName() { return "Multitool"; }

    @Override
    protected JsonArray getSlotsArray(JsonObject root) {
        JsonObject weaponInventory = resolveWeaponInventory(root);
        if (weaponInventory == null) {
            return new JsonArray();
        }
        JsonArray slots = weaponInventory.getAsJsonArray(":No");
        return slots != null ? slots : new JsonArray();
    }

    @Override
    protected JsonArray getValidSlotsArray(JsonObject root) {
        JsonObject weaponInventory = resolveWeaponInventory(root);
        if (weaponInventory == null) {
            return new JsonArray();
        }
        JsonArray valid = weaponInventory.getAsJsonArray("hl?");
        return valid != null ? valid : new JsonArray();
    }

    protected JsonObject resolveWeaponInventory(JsonObject root) {
        try {
            JsonObject base = root.getAsJsonObject("vLc");
            if (base == null) return null;
            JsonObject player = base.getAsJsonObject("6f=");
            if (player == null) return null;
            return player.getAsJsonObject("Kgt");
        } catch (Exception e) {
            return null;
        }
    }
}
