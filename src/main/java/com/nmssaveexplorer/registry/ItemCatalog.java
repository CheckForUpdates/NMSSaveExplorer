package com.nmssaveexplorer.registry;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Builds an in-memory catalogue of product, substance, and technology definitions that can be
 * queried when the user wants to add an item to an inventory slot.
 */
public final class ItemCatalog {

    private static final String PRODUCT_TABLE = "/data/nms_reality_gcproducttable.MXML";
    private static final String SUBSTANCE_TABLE = "/data/nms_reality_gcsubstancetable.MXML";
    private static final String TECHNOLOGY_TABLE = "/data/nms_reality_gctechnologytable.MXML";

    private static final Map<ItemType, Integer> BASE_STACKS = Map.of(
            ItemType.PRODUCT, 10,
            ItemType.SUBSTANCE, 9999
    );

    private static final List<ItemEntry> ALL_ITEMS = new ArrayList<>();
    private static volatile boolean initialised = false;
    private static final Object LOAD_LOCK = new Object();

    private ItemCatalog() {
    }

    public static List<ItemEntry> getItemsForTypes(Set<ItemType> allowedTypes) {
        ensureLoaded();
        if (allowedTypes == null || allowedTypes.isEmpty()) {
            return Collections.unmodifiableList(ALL_ITEMS);
        }
        List<ItemEntry> matches = new ArrayList<>();
        for (ItemEntry entry : ALL_ITEMS) {
            if (allowedTypes.contains(entry.type())) {
                matches.add(entry);
            }
        }
        return Collections.unmodifiableList(matches);
    }

    private static void ensureLoaded() {
        if (initialised) {
            return;
        }
        synchronized (LOAD_LOCK) {
            if (initialised) {
                return;
            }
            loadCatalog();
            initialised = true;
        }
    }

    private static void loadCatalog() {
        Map<String, ItemMetadata> metadata = new HashMap<>();
        parseProductTable(metadata);
        parseSubstanceTable(metadata);
        parseTechnologyTable(metadata);

        Map<String, ItemDefinitionRegistry.ItemDefinition> definitions = ItemDefinitionRegistry.getAllDefinitions();
        for (Map.Entry<String, ItemMetadata> entry : metadata.entrySet()) {
            String id = entry.getKey();
            ItemMetadata data = entry.getValue();
            ItemDefinitionRegistry.ItemDefinition definition = definitions.get(id);
            String displayName = (definition != null && definition.name() != null && !definition.name().isBlank())
                    ? definition.name()
                    : id;
            ALL_ITEMS.add(new ItemEntry(id, displayName, data.type(), data.maxStack()));
        }
        ALL_ITEMS.sort(Comparator.comparing(ItemEntry::displayName, String.CASE_INSENSITIVE_ORDER));
    }

    private static void parseProductTable(Map<String, ItemMetadata> metadata) {
        Document document = readDocument(PRODUCT_TABLE);
        if (document == null) {
            return;
        }
        NodeList nodes = document.getElementsByTagName("Property");
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (!(node instanceof Element element)) {
                continue;
            }
            if (!"GcProductData".equals(element.getAttribute("value"))) {
                continue;
            }
            String id = normaliseId(element.getAttribute("_id"));
            if (id.isEmpty()) {
                continue;
            }
            Element stackNode = findDirectChild(element, "StackMultiplier");
            int multiplier = readIntAttribute(stackNode, 1);
            int base = BASE_STACKS.getOrDefault(ItemType.PRODUCT, 1);
            metadata.put(id, new ItemMetadata(ItemType.PRODUCT, multiplier * base));
        }
    }

    private static void parseSubstanceTable(Map<String, ItemMetadata> metadata) {
        Document document = readDocument(SUBSTANCE_TABLE);
        if (document == null) {
            return;
        }
        NodeList nodes = document.getElementsByTagName("Property");
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (!(node instanceof Element element)) {
                continue;
            }
            if (!"GcRealitySubstanceData".equals(element.getAttribute("value"))) {
                continue;
            }
            String id = normaliseId(element.getAttribute("_id"));
            if (id.isEmpty()) {
                continue;
            }
            Element stackNode = findDirectChild(element, "StackMultiplier");
            int multiplier = readIntAttribute(stackNode, 1);
            int base = BASE_STACKS.getOrDefault(ItemType.SUBSTANCE, 1);
            metadata.put(id, new ItemMetadata(ItemType.SUBSTANCE, multiplier * base));
        }
    }

    private static void parseTechnologyTable(Map<String, ItemMetadata> metadata) {
        Document document = readDocument(TECHNOLOGY_TABLE);
        if (document == null) {
            return;
        }
        NodeList nodes = document.getElementsByTagName("Property");
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (!(node instanceof Element element)) {
                continue;
            }
            if (!"GcTechnology".equals(element.getAttribute("value"))) {
                continue;
            }
            String id = normaliseId(element.getAttribute("_id"));
            if (id.isEmpty()) {
                continue;
            }
            Element chargeNode = findDirectChild(element, "ChargeAmount");
            int chargeAmount = readIntAttribute(chargeNode, 1);
            if (chargeAmount <= 0) {
                chargeAmount = 1;
            }
            metadata.put(id, new ItemMetadata(ItemType.TECHNOLOGY, chargeAmount));
        }
    }

    private static Document readDocument(String resourcePath) {
        try (InputStream stream = ItemCatalog.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                System.err.println("[ItemCatalog] Missing resource: " + resourcePath);
                return null;
            }
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setNamespaceAware(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(stream);
        } catch (Exception ex) {
            System.err.println("[ItemCatalog] Failed to parse " + resourcePath + ": " + ex.getMessage());
            return null;
        }
    }

    private static Element findDirectChild(Element parent, String propertyName) {
        if (parent == null) {
            return null;
        }
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element element && "Property".equals(element.getTagName())) {
                if (propertyName.equals(element.getAttribute("name"))) {
                    return element;
                }
            }
        }
        return null;
    }

    private static int readIntAttribute(Element element, int fallback) {
        if (element == null) {
            return fallback;
        }
        String raw = element.getAttribute("value");
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return (int) Math.round(Double.parseDouble(raw));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String normaliseId(String rawId) {
        return rawId == null ? "" : rawId.trim().toUpperCase(Locale.ROOT);
    }

    public enum ItemType {
        SUBSTANCE("Substance"),
        PRODUCT("Product"),
        TECHNOLOGY("Technology"),
        UNKNOWN("Unknown");

        private final String label;

        ItemType(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public int defaultSuggestedAmount() {
            return switch (this) {
                case SUBSTANCE -> 250;
                case PRODUCT -> 1;
                case TECHNOLOGY -> 1;
                default -> 1;
            };
        }

        public String inventoryValue() {
            return label;
        }

        public static ItemType fromInventoryValue(String value) {
            if (value == null) {
                return UNKNOWN;
            }
            return switch (value.toLowerCase(Locale.ROOT)) {
                case "substance" -> SUBSTANCE;
                case "product" -> PRODUCT;
                case "technology" -> TECHNOLOGY;
                default -> UNKNOWN;
            };
        }
    }

    public record ItemEntry(String id, String displayName, ItemType type, int maxStack) {}

    private record ItemMetadata(ItemType type, int maxStack) {}
}
