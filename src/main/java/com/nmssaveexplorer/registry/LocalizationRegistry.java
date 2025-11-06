package com.nmssaveexplorer.registry;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Loads localisation tables (e.g. nms_loc4_usenglish.MXML) and exposes token → string lookups.
 */
public final class LocalizationRegistry {

    private static final Map<String, String> TOKEN_TO_TEXT = new HashMap<>();
    private static volatile boolean initialised = false;

    private LocalizationRegistry() {
    }

    private static void ensureLoaded() {
        if (initialised) {
            return;
        }
        synchronized (LocalizationRegistry.class) {
            if (initialised) {
                return;
            }
            List<String> locFiles = List.of(
                "/data/nms_loc1_usenglish.MXML",
                "/data/nms_loc4_usenglish.MXML",
                "/data/nms_loc5_usenglish.MXML",
                "/data/nms_loc6_usenglish.MXML",
                "/data/nms_loc7_usenglish.MXML",
                "/data/nms_loc8_usenglish.MXML",
                "/data/nms_loc9_usenglish.MXML",
                "/data/nms_update3_usenglish.MXML"
            );
            for (String path : locFiles) {
                loadLocalization(path);
            }
            initialised = true;
        }
    }

    private static void loadLocalization(String resourcePath) {
        try (InputStream stream = LocalizationRegistry.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                System.err.println("[LocalizationRegistry] Missing localisation resource: " + resourcePath);
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
        } catch (Exception ex) {
            System.err.println("[LocalizationRegistry] Failed to load " + resourcePath + ": " + ex.getMessage());
        }
    }

    private static void traverse(Node node) {
        if (!(node instanceof Element element)) {
            return;
        }
        if ("Property".equals(element.getTagName()) && "TkLocalisationEntry".equals(element.getAttribute("value"))) {
            String id = null;
            String text = null;
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
                String value = childElement.getAttribute("value");
                if ("Id".equals(name)) {
                    id = value;
                } else if ("English".equals(name) || "USEnglish".equals(name)) {
                    text = value;
                }
            }
            if (id != null && text != null && !text.isEmpty()) {
                TOKEN_TO_TEXT.putIfAbsent(id.toUpperCase(Locale.ROOT), text);
            }
        }
        Node child = node.getFirstChild();
        while (child != null) {
            traverse(child);
            child = child.getNextSibling();
        }
    }

    public static String get(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        ensureLoaded();
        return TOKEN_TO_TEXT.get(token.toUpperCase(Locale.ROOT));
    }

    public static String resolve(String token, String fallback) {
        String resolved = get(token);
        return (resolved != null && !resolved.isEmpty()) ? resolved : fallback;
    }
}
