package com.nmssaveexplorer.inventory;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class MultitoolTechInventoryController extends MultitoolInventoryController {

    @Override
    protected String getInventoryName() {
        return "Multitool Technology";
    }

    @Override
    protected JsonArray getSlotsArray(JsonObject root) {
        JsonObject techInventory = resolveWeaponTechInventory(root);
        if (techInventory == null) {
            return new JsonArray();
        }
        JsonArray slots = techInventory.getAsJsonArray(":No");
        return slots != null ? slots : new JsonArray();
    }

    @Override
    protected JsonArray getValidSlotsArray(JsonObject root) {
        JsonObject techInventory = resolveWeaponTechInventory(root);
        if (techInventory == null) {
            return new JsonArray();
        }
        JsonArray valid = techInventory.getAsJsonArray("hl?");
        return valid != null ? valid : new JsonArray();
    }

    private JsonObject resolveWeaponTechInventory(JsonObject root) {
        JsonObject weaponInventory = resolveWeaponInventory(root);
        if (weaponInventory == null) {
            return null;
        }
        return weaponInventory.getAsJsonObject("PMT");
    }
}
