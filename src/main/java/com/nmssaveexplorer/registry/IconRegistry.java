package com.nmssaveexplorer.registry;

import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import javafx.scene.image.Image;

/**
 * Resolves item IDs to their UI icon images by parsing the EXML/MXML data tables.
 */
public final class IconRegistry {

    private static final String ICON_PREFIX = "TEXTURES/UI/FRONTEND/ICONS/";
    private static final Map<String, String> ID_TO_RESOURCE = new HashMap<>();
    private static final Map<String, Image> IMAGE_CACHE = new ConcurrentHashMap<>();
    private static volatile boolean initialised = false;

    private IconRegistry() {
    }

    private static void ensureLoaded() {
        if (initialised) {
            return;
        }
        synchronized (IconRegistry.class) {
            if (initialised) {
                return;
            }
            List<String> tables = List.of(
                    "/data/nms_basepartproducts.MXML",
                    "/data/nms_reality_gcproducttable.MXML",
                    "/data/nms_reality_gctechnologytable.MXML",
                    "/data/nms_reality_gcsubstancetable.MXML",
                    "/data/nms_reality_gcproceduraltechnologytable.MXML",
                    "/data/nms_modularcustomisationproducts.MXML"
            );
            for (String table : tables) {
                loadTable(table);
            }
            System.out.println("[IconRegistry] mapped icons: " + ID_TO_RESOURCE.size());
            initialised = true;
        }
    }

    private static void loadTable(String resourcePath) {
        try (InputStream stream = IconRegistry.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                System.err.println("[IconRegistry] Missing resource: " + resourcePath);
                return;
            }
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setIgnoringComments(true);
            factory.setCoalescing(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(stream);
            Element root = document.getDocumentElement();
            traverse(root);
        } catch (Exception e) {
            System.err.println("[IconRegistry] Failed to load " + resourcePath + ": " + e.getMessage());
        }
    }

    private static void traverse(Node node) {
        if (!(node instanceof Element element)) {
            return;
        }

        String tag = element.getTagName();
        if ("Property".equals(tag)) {
            String valueType = element.getAttribute("value");
            if ("GcProductData".equals(valueType)
                    || "GcTechnologyData".equals(valueType)
                    || "GcRealitySubstanceData".equals(valueType)
                    || "GcProceduralTechnologyData".equals(valueType)) {
                String id = extractId(element);
                String iconPath = extractIconFilename(element);
                if (id != null && iconPath != null) {
                    mapIcon(id, iconPath);
                }
            }
        }

        Node child = node.getFirstChild();
        while (child != null) {
            traverse(child);
            child = child.getNextSibling();
        }
    }

    private static String extractId(Element element) {
        String id = element.getAttribute("_id");
        if (id != null && !id.isEmpty()) {
            return id;
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (!(child instanceof Element childElement)) {
                continue;
            }
            if (!"Property".equals(childElement.getTagName())) {
                continue;
            }
            String name = childElement.getAttribute("name");
            if ("ID".equals(name) || "Name".equals(name)) {
                String value = childElement.getAttribute("value");
                if (value != null && !value.isEmpty()) {
                    return value;
                }
            }
        }
        return null;
    }

    private static String extractIconFilename(Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (!(child instanceof Element childElement)) {
                continue;
            }
            if (!"Property".equals(childElement.getTagName())) {
                continue;
            }
            if (!"Icon".equals(childElement.getAttribute("name"))) {
                continue;
            }
            NodeList iconNodes = childElement.getChildNodes();
            for (int j = 0; j < iconNodes.getLength(); j++) {
                Node iconChild = iconNodes.item(j);
                if (!(iconChild instanceof Element iconElement)) {
                    continue;
                }
                if ("Property".equals(iconElement.getTagName()) && "Filename".equals(iconElement.getAttribute("name"))) {
                    String value = iconElement.getAttribute("value");
                    if (value != null && !value.isEmpty()) {
                        return value;
                    }
                }
            }
        }
        return null;
    }

    private static void mapIcon(String id, String filename) {
        if (filename == null || filename.isEmpty()) {
            return;
        }
        String trimmed = filename.trim();
        if (!trimmed.regionMatches(true, 0, ICON_PREFIX, 0, ICON_PREFIX.length())) {
            // Only handle UI front-end icons for now.
            return;
        }
        String relative = trimmed.substring(ICON_PREFIX.length());
        relative = relative.replace('\\', '/');

        String lower = relative.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".dds")) {
            lower = lower.substring(0, lower.length() - 4) + ".png";
        }

        String resourcePath = "/icons/" + lower;
        URL resource = IconRegistry.class.getResource(resourcePath);
        if (resource == null) {
            // try preserving original casing but swapping extension
            String originalCase = relative;
            if (originalCase.endsWith(".DDS")) {
                originalCase = originalCase.substring(0, originalCase.length() - 4) + ".png";
            } else {
                originalCase = originalCase.replace(".dds", ".png");
            }
            resourcePath = "/icons/" + originalCase;
            resource = IconRegistry.class.getResource(resourcePath);
        }

        if (resource == null) {
            // No matching icon asset in resources; skip.
            System.err.println("[IconRegistry] Missing icon resource for id=" + id + " path=" + relative);
            return;
        }

        ID_TO_RESOURCE.put(id.toUpperCase(Locale.ROOT), resourcePath);
    }

    /**
     * Returns the icon image for the given item identifier, or {@code null} if none is available.
     */
    public static Image getIcon(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return null;
        }
        ensureLoaded();
        String key = itemId.toUpperCase(Locale.ROOT);
        String resourcePath = ID_TO_RESOURCE.get(key);
        if (resourcePath == null && key.startsWith("^")) {
            resourcePath = ID_TO_RESOURCE.get(key.substring(1));
        }
        if (resourcePath == null) {
            return null;
        }
        return IMAGE_CACHE.computeIfAbsent(resourcePath, path -> {
            InputStream stream = IconRegistry.class.getResourceAsStream(path);
            if (stream == null) {
                return null;
            }
            return new Image(stream, 64, 64, true, true);
        });
    }

    public static String getIconResourcePath(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return null;
        }
        ensureLoaded();
        String key = itemId.toUpperCase(Locale.ROOT);
        String resourcePath = ID_TO_RESOURCE.get(key);
        if (resourcePath == null && key.startsWith("^")) {
            resourcePath = ID_TO_RESOURCE.get(key.substring(1));
        }
        return resourcePath;
    }
}
