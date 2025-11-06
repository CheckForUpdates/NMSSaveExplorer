package com.nmssaveexplorer.registry;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Maps item IDs to localisation tokens by parsing the product/technology/substance tables.
 */
public final class ItemNameRegistry {

    private static final Map<String, String> ID_TO_TOKEN = new HashMap<>();
    private static volatile boolean initialised = false;

    private ItemNameRegistry() {
    }

    private static void ensureLoaded() {
        if (initialised) {
            return;
        }
        synchronized (ItemNameRegistry.class) {
            if (initialised) {
                return;
            }
            loadTable("/data/proceduralproducttable.MXML", "GcProceduralProductData");
            loadTable("/data/nms_reality_gcproducttable.MXML", "GcProductData");
            loadTable("/data/nms_reality_gcsubstancetable.MXML", "GcRealitySubstanceData");
            loadTable("/data/nms_reality_gcproceduraltechnologytable.MXML", "GcProceduralTechnologyData");
            loadTable("/data/nms_reality_gctechnologytable.MXML", "GcTechnology");
            initialised = true;
        }
    }

    private static void loadTable(String resourcePath, String entryType) {
        try (InputStream stream = ItemNameRegistry.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                System.err.println("[ItemNameRegistry] Missing table resource: " + resourcePath);
                return;
            }
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setIgnoringComments(true);
            factory.setCoalescing(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(stream);
            Element root = document.getDocumentElement();
            traverse(root, entryType);
        } catch (Exception ex) {
            System.err.println("[ItemNameRegistry] Failed to load " + resourcePath + ": " + ex.getMessage());
        }
    }

    private static void traverse(Node node, String entryType) {
        if (!(node instanceof Element element)) {
            return;
        }
        if ("Property".equals(element.getTagName()) && entryType.equals(element.getAttribute("value"))) {
            String id = null;
            String token = null;
            NodeList children = element.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (!(child instanceof Element childElement)) {
                    continue;
                }
                if (!"Property".equals(childElement.getTagName())) {
                    continue;
                }
                String nameAttr = childElement.getAttribute("name");
                String valueAttr = childElement.getAttribute("value");
                if ("ID".equals(nameAttr)) {
                    id = valueAttr;
                } else if ("Name".equals(nameAttr)) {
                    token = valueAttr;
                }
            }
            if (id != null && token != null && !token.isEmpty()) {
                ID_TO_TOKEN.putIfAbsent(id.toUpperCase(Locale.ROOT), token);
            }
        }
        Node child = node.getFirstChild();
        while (child != null) {
            traverse(child, entryType);
            child = child.getNextSibling();
        }
    }

    public static String getDisplayName(String rawId) {
        if (rawId == null || rawId.isEmpty()) {
            return null;
        }
        ensureLoaded();
        String key = rawId.startsWith("^") ? rawId.substring(1) : rawId;
        String token = ID_TO_TOKEN.get(key.toUpperCase(Locale.ROOT));
        if (token == null || token.isEmpty()) {
            return null;
        }
        return LocalizationRegistry.resolve(token, null);
    }
}
