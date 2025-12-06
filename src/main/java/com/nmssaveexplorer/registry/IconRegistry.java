package com.nmssaveexplorer.registry;

import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javafx.scene.image.Image;

/**
 * Provides icon images for item identifiers based on {@code localisation_map.json}.
 */
public final class IconRegistry {

    private static final Map<String, Image> IMAGE_CACHE = new ConcurrentHashMap<>();

    private IconRegistry() {
    }

    public static Image getIcon(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return null;
        }
        ItemDefinitionRegistry.ItemDefinition definition = ItemDefinitionRegistry.getDefinition(itemId);
        if (definition == null || definition.icon() == null || definition.icon().isEmpty()) {
            return null;
        }
        String resourcePath = "/icons/" + definition.icon();
        String cacheKey = resourcePath.toLowerCase(Locale.ROOT);
        return IMAGE_CACHE.computeIfAbsent(cacheKey, key -> {
            InputStream stream = IconRegistry.class.getResourceAsStream(resourcePath);
            if (stream == null) {
                System.err.println("[IconRegistry] Missing icon resource: " + resourcePath);
                return null;
            }
            return new Image(stream, 64, 64, true, true);
        });
    }

    public static String getIconResourcePath(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return null;
        }
        ItemDefinitionRegistry.ItemDefinition definition = ItemDefinitionRegistry.getDefinition(itemId);
        if (definition == null || definition.icon() == null || definition.icon().isEmpty()) {
            return null;
        }
        return "/icons/" + definition.icon();
    }
}
