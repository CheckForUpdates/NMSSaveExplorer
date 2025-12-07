package com.nmssaveexplorer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import com.nmssaveexplorer.inventory.BaseInventoryController;
import com.nmssaveexplorer.inventory.ExosuitInventoryController;
import com.nmssaveexplorer.inventory.ExosuitTechController;
import com.nmssaveexplorer.inventory.MultitoolInventoryController;
import com.nmssaveexplorer.inventory.MultitoolTechInventoryController;
import com.nmssaveexplorer.inventory.ShipInventoryController;
import com.nmssaveexplorer.inventory.ShipTechInventoryController;
import com.nmssaveexplorer.inventory.StorageInventoryController;
import com.nmssaveexplorer.registry.IconRegistry;
import com.nmssaveexplorer.registry.ItemCatalog;
import com.nmssaveexplorer.registry.ItemDefinitionRegistry;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Separator;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;


public class SaveExplorerController {

    @FXML
    private TreeView<String> jsonTree;
    @FXML
    private Label statusLabel;
    @FXML
    private StackPane editorContainer;

    private CodeArea codeArea;
    private boolean mappingLoaded = false;
    private JsonElement rootJsonElement;
    private final Map<TreeItem<String>, JsonElement> nodeToElementMap = new HashMap<>();
    private final Map<String, String> reverseMapping = new HashMap<>();
    private final Map<TreeItem<String>, JsonElement> originalNodeValues = new HashMap<>();
    private final Set<TreeItem<String>> modifiedNodes = new HashSet<>();
    private File currentSaveFile = null;
    private TreeItem<String> currentEditedNode = null;
    private boolean inventoryModified = false;
    private boolean usingExpeditionContext = false;
    private Stage lastInventoryStage;
    private final Map<String, Image> expeditionIconCache = new HashMap<>();
    private static final Set<String> loggedMissingIcons = new HashSet<>();
    private static final int INVENTORY_ICON_SIZE = 72;
    private static final double EXPEDITION_ICON_SIZE = 48;
    private static final String KEY_COMMON_STATE = "<h0";
    private static final String KEY_SEASON_DATA = "Rol";
    private static final String KEY_SEASON_STAGES = "3Mw";
    private static final String KEY_STAGE_MILESTONES = "kr6";
    private static final String KEY_MISSION_NAME = "p0c";
    private static final String KEY_MISSION_AMOUNT = "1o9";
    private static final String KEY_ICON = "DhC";
    private static final String KEY_ICON_FILENAME = "93M";
    private static final String KEY_SEASON_STATE = "qYy";
    private static final String KEY_ACTIVE_CONTEXT = "XTp";
    private static final String KEY_EXPEDITION_CONTEXT = "2YS";
    private static final String KEY_PLAYER_STATE = "vLc";
    private static final String CONTEXT_MAIN = "Main";
    private static final String KEY_MILESTONE_VALUES = "psf";
    private static final String KEY_UNITS = "wGS";
    private static final String KEY_NANITES = "7QL";
    private static final String KEY_QUICKSILVER = "kN;";
    private static final String ICON_UNITS = "UNITS";
    private static final String ICON_NANITE = "TECHFRAG";
    private static final String ICON_QUICKSILVER = "QUICKSILVER";
    private static final String APPLICATION_STYLESHEET =
            Objects.requireNonNull(SaveExplorerController.class.getResource("/styles/application.css"),
                    "Missing stylesheet /styles/application.css").toExternalForm();
    private static final String INVENTORY_GRID_CLASS = "inventory-grid";
    private static final String INVENTORY_PLACEHOLDER_CLASS = "inventory-slot-placeholder";
    private static final String INVENTORY_PANE_CLASS = "inventory-slot-pane";
    private static final String INVENTORY_NAME_CLASS = "inventory-slot-name";
    private static final String INVENTORY_AMOUNT_CLASS = "inventory-slot-amount";
    private static final String INVENTORY_SCROLL_CLASS = "inventory-scroll";
    private static final String INVENTORY_HIGHLIGHT_CLASS = "inventory-slot-highlight";
    private static final String TREE_MODIFIED_CLASS = "json-tree-cell-modified";

    @FXML
    private void initialize() {
        setupTree();
        setupEditor();
        statusLabel.setText("Status: Ready.");
    }

    private void setupTree() {
        TreeItem<String> root = new TreeItem<>("root");
        root.getChildren().add(new TreeItem<>("Open a save file to begin."));
        root.setExpanded(true);
        jsonTree.setRoot(root);

        jsonTree.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> {

            // When switching nodes, try to commit the old edit first
            if (oldItem != null && currentEditedNode == oldItem) {
                String editedText = codeArea.getText().trim();
                if (!editedText.isEmpty()) {
                    try {
                        JsonElement newFragment = JsonParser.parseString(editedText);
                        JsonElement remappedFragment = remapElement(newFragment, reverseMapping);

                        // Compare with current JSON value; skip if identical
                        JsonElement currentValue = nodeToElementMap.get(oldItem);
                        String newJson = new Gson().toJson(remappedFragment);
                        String currentJson = (currentValue != null) ? new Gson().toJson(currentValue) : "";

                        if (!newJson.equals(currentJson)) {
                            updateJsonNode(oldItem, remappedFragment);
                            System.out.println("[AutoCommit] Updated node before switching: " + oldItem.getValue());
                        } else {
                            System.out.println("[AutoCommit] No change detected for: " + oldItem.getValue());
                        }
                    } catch (JsonSyntaxException ex) {
                        statusLabel.setText("Invalid JSON, not applied: " + ex.getMessage());
                    }

                }
            }

            if (newItem != null) {
                currentEditedNode = null; // Prevent codeArea listener from tagging the old node during UI refresh
                showJsonForItem(newItem);
                currentEditedNode = newItem;
            } else {
                currentEditedNode = null;
            }

            if (newItem != null) {
                String path = getNodePath(newItem);
                statusLabel.setText(path);
            }
        });


        // right-click menu for undo
        jsonTree.setCellFactory(tv -> {
            TreeCell<String> cell = new TreeCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setContextMenu(null);
                        getStyleClass().remove(TREE_MODIFIED_CLASS);
                    } else {
                        setText(item);
                        TreeItem<String> treeItem = getTreeItem();
                        if (modifiedNodes.contains(treeItem)) {
                            if (!getStyleClass().contains(TREE_MODIFIED_CLASS)) {
                                getStyleClass().add(TREE_MODIFIED_CLASS);
                            }
                        } else {
                            getStyleClass().remove(TREE_MODIFIED_CLASS);
                        }
                        ContextMenu menu = new ContextMenu();
                        MenuItem revertItem = new MenuItem("Undo Change");
                        revertItem.setOnAction(ev -> revertNode(treeItem));
                        menu.getItems().add(revertItem);
                        setContextMenu(menu);
                    }
                }
            };
            return cell;
        });
    }

    private void setupEditor() {
        codeArea = new CodeArea();
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        codeArea.setWrapText(false);
        codeArea.setEditable(true);
        codeArea.getStylesheets().add(
                getClass().getResource("/styles/editor.css").toExternalForm()
        );
        org.fxmisc.flowless.VirtualizedScrollPane<CodeArea> vsPane =
                new org.fxmisc.flowless.VirtualizedScrollPane<>(codeArea);
        editorContainer.getChildren().add(vsPane);
        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            if (currentEditedNode == null) return;
            JsonElement currentElement = nodeToElementMap.get(currentEditedNode);
            if (currentElement == null) return;

            // Compare serialized JSON forms
            String originalJson = new Gson().toJson(mapJsonElement(currentElement));
            if (!originalJson.equals(newText.trim())) {
                markNodeModified(currentEditedNode);
                inventoryModified = true;
            } else {
                unmarkNodeModified(currentEditedNode);
                inventoryModified = false;
            }
        });
        setupFindShortcut();
    }


    @FXML
    private void onOpenSaveFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open Save File");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("No Man's Sky Saves (*.hg)", "*.hg"),
                new FileChooser.ExtensionFilter("JSON Files (*.json)", "*.json"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        File file = chooser.showOpenDialog(null);
        if (file == null) {
            statusLabel.setText("No file selected.");
            return;
        }

        try {
            loadSaveFile(file);
        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("Error: " + e.getMessage());
        }
    }

    public void loadSaveFile(File file) throws Exception {
        if (file == null) {
            throw new IllegalArgumentException("file must not be null");
        }

        statusLabel.setText("Opening: " + file.getName());
        currentSaveFile = file;

        ensureMappingLoaded();

        String content;
        if (file.getName().toLowerCase().endsWith(".hg")) {
            content = SaveDecoder.decodeSave(file);
        } else {
            content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        }

        rootJsonElement = JsonParser.parseString(content);
        this.rootJsonElement = rootJsonElement;
        this.currentSaveFile = file;
        updateActivePlayerContext(rootJsonElement.getAsJsonObject());
        populateJsonTree(rootJsonElement, file.getName());
        modifiedNodes.clear();
        showTemporaryStatus("Loaded: " + file.getName() + " (" + currentContextLabel() + ")");
    }

    public boolean hasLoadedSave() {
        return rootJsonElement != null && currentSaveFile != null;
    }

    private void updateActivePlayerContext(JsonObject root) {
        usingExpeditionContext = shouldUseExpeditionContext(root);
        System.out.println("[Context] Active=" + (root != null && root.has(KEY_ACTIVE_CONTEXT)
                ? root.get(KEY_ACTIVE_CONTEXT).getAsString()
                : "unknown")
                + " usingExpedition=" + usingExpeditionContext);
    }

    private boolean shouldUseExpeditionContext(JsonObject root) {
        if (root == null || !root.has(KEY_EXPEDITION_CONTEXT)) {
            return false;
        }
        JsonElement contextElement = root.get(KEY_ACTIVE_CONTEXT);
        String contextValue = contextElement != null && contextElement.isJsonPrimitive()
                ? contextElement.getAsString()
                : "";
        if (contextValue == null) {
            contextValue = "";
        }
        String normalized = contextValue.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.equals(CONTEXT_MAIN.toLowerCase(Locale.ROOT))) {
            return false;
        }
        JsonObject expedition = root.getAsJsonObject(KEY_EXPEDITION_CONTEXT);
        return expedition != null && expedition.has("6f=");
    }

    private JsonObject resolveInventoryRoot(JsonObject root) {
        if (!usingExpeditionContext || root == null) {
            return root;
        }
        JsonObject expedition = root.getAsJsonObject(KEY_EXPEDITION_CONTEXT);
        if (expedition == null) {
            return root;
        }
        JsonObject wrapper = new JsonObject();
        wrapper.add(KEY_PLAYER_STATE, expedition);
        return wrapper;
    }

    private String currentContextLabel() {
        return usingExpeditionContext ? "Expedition" : "Primary";
    }

    private JsonObject getActivePlayerState(JsonObject root) {
        if (root == null) {
            return null;
        }
        if (usingExpeditionContext) {
            JsonObject expedition = root.getAsJsonObject(KEY_EXPEDITION_CONTEXT);
            if (expedition != null && expedition.has("6f=")) {
                return expedition.getAsJsonObject("6f=");
            }
        }
        if (root.has(KEY_PLAYER_STATE)) {
            JsonObject base = root.getAsJsonObject(KEY_PLAYER_STATE);
            if (base != null && base.has("6f=")) {
                return base.getAsJsonObject("6f=");
            }
        }
        return null;
    }

    private void ensureMappingLoaded() {
        if (mappingLoaded) {
            return;
        }

        try (var stream = getClass().getResourceAsStream("/mapping.json")) {
            if (stream == null) {
                System.err.println("[Controller] mapping.json not found in resources.");
                return;
            }
            JsonMapper.loadMapping(stream);
            mappingLoaded = JsonMapper.isLoaded();
            reverseMapping.clear();
            for (Map.Entry<String, String> entry : JsonMapper.getMapping().entrySet()) {
                reverseMapping.put(entry.getValue(), entry.getKey());
            }
            System.out.println("[Mapping] Loaded " + reverseMapping.size() + " reverse entries.");
        } catch (Exception ex) {
            throw new RuntimeException("Failed to load mapping.json", ex);
        }
    }

    /**
     * ====================== TREE & NODE MANAGEMENT ======================
     **/

    private void populateJsonTree(JsonElement jsonElement, String fileName) {
        TreeItem<String> root = new TreeItem<>(fileName);
        nodeToElementMap.clear();
        originalNodeValues.clear();
        buildTree(root, jsonElement);
        root.setExpanded(true);
        jsonTree.setRoot(root);
    }

    private void buildTree(TreeItem<String> parent, JsonElement element) {
        nodeToElementMap.put(parent, element);
        originalNodeValues.put(parent, element.deepCopy());

        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            for (var entry : obj.entrySet()) {
                String key = JsonMapper.mapKey(entry.getKey());
                TreeItem<String> child = new TreeItem<>(key);
                parent.getChildren().add(child);
                buildTree(child, entry.getValue());
            }
        } else if (element.isJsonArray()) {
            JsonArray arr = element.getAsJsonArray();
            for (int i = 0; i < arr.size(); i++) {
                TreeItem<String> child = new TreeItem<>("[" + i + "]");
                parent.getChildren().add(child);
                buildTree(child, arr.get(i));
            }
        }
    }

    private void showJsonForItem(TreeItem<String> item) {
        JsonElement element = nodeToElementMap.get(item);
        if (element == null) {
            codeArea.replaceText("// No data for this node.");
            return;
        }
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        codeArea.replaceText(gson.toJson(mapJsonElement(element)));
        inventoryModified = false;
    }

    private JsonElement mapJsonElement(JsonElement element) {
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            JsonObject mapped = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                String key = JsonMapper.mapKey(entry.getKey());
                mapped.add(key, mapJsonElement(entry.getValue()));
            }
            return mapped;
        } else if (element.isJsonArray()) {
            JsonArray arr = element.getAsJsonArray();
            JsonArray mappedArr = new JsonArray();
            for (JsonElement e : arr) mappedArr.add(mapJsonElement(e));
            return mappedArr;
        } else {
            return element.deepCopy();
        }
    }

    private JsonElement remapElement(JsonElement readable, Map<String, String> reverse) {
        if (readable == null) {
            return null;
        }
        if (reverse == null || reverse.isEmpty()) {
            return readable.deepCopy();
        }
        if (readable.isJsonObject()) {
            JsonObject obj = readable.getAsJsonObject();
            JsonObject out = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                String shortKey = reverse.getOrDefault(entry.getKey(), entry.getKey());
                out.add(shortKey, remapElement(entry.getValue(), reverse));
            }
            return out;
        }
        if (readable.isJsonArray()) {
            JsonArray arr = new JsonArray();
            for (JsonElement element : readable.getAsJsonArray()) {
                arr.add(remapElement(element, reverse));
            }
            return arr;
        }
        return readable.deepCopy();
    }

    private JsonObject remapJson(JsonObject readable, Map<String, String> reverse) {
        JsonElement remapped = remapElement(readable, reverse);
        return remapped != null && remapped.isJsonObject() ? remapped.getAsJsonObject() : new JsonObject();
    }

    private void updateJsonNode(TreeItem<String> item, JsonElement newValue) {
        TreeItem<String> parent = item.getParent();
        if (parent == null) return;
        JsonElement parentElem = nodeToElementMap.get(parent);
        if (parentElem == null || !parentElem.isJsonObject()) return;

        JsonObject parentObj = parentElem.getAsJsonObject();
        String key = item.getValue().replace("*", "");
        String shortKey = reverseMapping.getOrDefault(key, key);

        parentObj.add(shortKey, newValue);
        nodeToElementMap.put(item, newValue);
        modifiedNodes.add(item);
    }

    private void revertNode(TreeItem<String> item) {
        JsonElement original = originalNodeValues.get(item);
        if (original == null) {
            statusLabel.setText("No original value stored for this node.");
            return;
        }

        TreeItem<String> parent = item.getParent();
        if (parent == null) return;
        JsonObject parentObj = findParentObject(rootJsonElement.getAsJsonObject(), parent);
        if (parentObj == null) return;

        String key = item.getValue().replace("*", "");
        String shortKey = reverseMapping.getOrDefault(key, key);

        parentObj.add(shortKey, original.deepCopy());
        nodeToElementMap.put(item, original.deepCopy());
        modifiedNodes.remove(item);
        item.setValue(key);
        showTemporaryStatus("Reverted node: " + key);
    }

    private JsonObject findParentObject(JsonObject current, TreeItem<String> parentNode) {
        for (Map.Entry<String, JsonElement> e : current.entrySet()) {
            String readableKey = JsonMapper.mapKey(e.getKey());
            if (readableKey.equals(parentNode.getValue()) && e.getValue().isJsonObject()) {
                return e.getValue().getAsJsonObject();
            }
            if (e.getValue().isJsonObject()) {
                JsonObject found = findParentObject(e.getValue().getAsJsonObject(), parentNode);
                if (found != null) return found;
            } else if (e.getValue().isJsonArray()) {
                for (JsonElement arrElem : e.getValue().getAsJsonArray()) {
                    if (arrElem.isJsonObject()) {
                        JsonObject found = findParentObject(arrElem.getAsJsonObject(), parentNode);
                        if (found != null) return found;
                    }
                }
            }
        }
        return null;
    }

    @FXML
    private void onSaveChanges() {
        performSave();
    }

    public boolean saveChanges() {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("saveChanges must be called on the JavaFX Application Thread");
        }
        return performSave();
    }

    private boolean performSave() {
        statusLabel.setText("Saving changes...");
        if (!hasLoadedSave()) {
            statusLabel.setText("No save loaded.");
            return false;
        }

        try {
            JsonObject fullRoot = (rootJsonElement != null && rootJsonElement.isJsonObject())
                    ? rootJsonElement.getAsJsonObject()
                    : new JsonObject();

            JsonObject encoded = currentSaveFile.getName().endsWith(".json")
                    ? remapJson(fullRoot, reverseMapping)
                    : fullRoot;

            File backup = new File(currentSaveFile.getAbsolutePath() + ".bak");
            Files.copy(currentSaveFile.toPath(), backup.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            SaveEncoder.encodeSave(currentSaveFile, encoded);
            inventoryModified = false;
            statusLabel.setText("Inventory changes saved.");
            clearModifiedIndicators();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("Save failed: " + e.getMessage());
            return false;
        }
    }


    @FXML
    private void onSaveAs() {
        statusLabel.setText("Saving as new file...");
        if (rootJsonElement == null) {
            statusLabel.setText("No save loaded.");
            return;
        }

        try {
            JsonObject fullRoot = (rootJsonElement.isJsonObject())
                    ? rootJsonElement.getAsJsonObject()
                    : new JsonObject();
            JsonObject encoded = remapJson(fullRoot, reverseMapping);

            FileChooser chooser = new FileChooser();
            chooser.setTitle("Save As .hg File");
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("No Man's Sky Saves (*.hg)", "*.hg"));
            File outFile = chooser.showSaveDialog(null);
            if (outFile == null) {
                statusLabel.setText("Save cancelled.");
                return;
            }

            SaveEncoder.encodeSave(outFile, encoded);
            statusLabel.setText("New file encoded: " + outFile.getName());
        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("Save As failed: " + e.getMessage());
        }
    }

    @FXML
    private void onExportJson() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export JSON");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files", "*.json"));
        File file = chooser.showSaveDialog(null);
        if (file != null) {
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(codeArea.getText());
                statusLabel.setText("Exported JSON to: " + file.getName());
            } catch (IOException e) {
                statusLabel.setText("Export failed: " + e.getMessage());
            }
        }
    }

    @FXML
    private void onExitApp() {
        Platform.exit();
    }

    @FXML
    private void onExpandAll() {
        expandCollapseAll(jsonTree.getRoot(), true);
    }

    @FXML
    private void onCollapseAll() {
        expandCollapseAll(jsonTree.getRoot(), false);
    }

    private void expandCollapseAll(TreeItem<?> item, boolean expand) {
        if (item == null) return;
        item.setExpanded(expand);
        for (TreeItem<?> child : item.getChildren()) expandCollapseAll(child, expand);
    }

    @FXML
    private void onShowAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About Save Explorer");
        alert.setHeaderText("Save Explorer - No Man’s Sky");
        alert.setContentText("Displays mapped JSON save data with editable viewer.\nBuilt using JavaFX + RichTextFX.");
        alert.showAndWait();
    }

    /**
     * Clears all modified-node indicators after a successful save.
     */
    private void clearModifiedIndicators() {
        for (TreeItem<String> item : modifiedNodes) {
            String label = item.getValue();
            if (label.endsWith("*")) {
                item.setValue(label.substring(0, label.length() - 1));
            }
        }
        modifiedNodes.clear();
        showTemporaryStatus("Save complete. All changes committed.");
        jsonTree.refresh(); // force UI refresh to remove bold styling
    }

    /**
     * Builds the readable path of a node, e.g. save.hg/BaseContext/PlayerStateData/Specials
     */
    private String getNodePath(TreeItem<String> node) {
        if (node == null) return "";
        StringBuilder sb = new StringBuilder(node.getValue());
        TreeItem<String> parent = node.getParent();
        while (parent != null) {
            sb.insert(0, parent.getValue() + "/");
            parent = parent.getParent();
        }
        return sb.toString();
    }

    private java.util.Timer statusTimer = new java.util.Timer(true);
    private java.util.TimerTask revertTask;

    /**
     * Shows a temporary message for a few seconds, then restores node path.
     */
    private void showTemporaryStatus(String message) {
        statusLabel.setText(message);
        if (revertTask != null) revertTask.cancel();

        revertTask = new java.util.TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> {
                    String path = getNodePath(currentEditedNode);
                    if (!path.isEmpty()) statusLabel.setText(path);
                });
            }
        };
        statusTimer.schedule(revertTask, 5000); // 5 seconds
    }

    private void markNodeModified(TreeItem<String> item) {
        if (item == null) return;
        if (!item.getValue().endsWith("*")) {
            item.setValue(item.getValue() + "*");
        }
        modifiedNodes.add(item);
        jsonTree.refresh();
    }

    private void unmarkNodeModified(TreeItem<String> item) {
        if (item == null) return;
        String value = item.getValue();
        if (value.endsWith("*")) {
            item.setValue(value.substring(0, value.length() - 1));
        }
        modifiedNodes.remove(item);
        jsonTree.refresh();
    }

    private void setupFindShortcut() {
        KeyCombination findKey = new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN);

        codeArea.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (findKey.match(e)) {
                e.consume();
                showFindDialog();
            }
        });
    }

    private int lastFindIndex = -1;
    private String lastSearchText = "";

    private void showFindDialog() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Find in JSON");
        dialog.setHeaderText(null);

        TextField searchField = new TextField();
        searchField.setPromptText("Enter text to find...");
        dialog.getDialogPane().setContent(searchField);

        ButtonType findBtn = new ButtonType("Find Next", ButtonBar.ButtonData.OK_DONE);
        ButtonType closeBtn = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(findBtn, closeBtn);

        dialog.setResultConverter(bt -> bt == findBtn ? searchField.getText() : null);
        Platform.runLater(searchField::requestFocus);

        dialog.show();

        // on Enter key, perform search
        searchField.setOnAction(ev -> {
            String text = searchField.getText();
            if (text == null || text.isEmpty()) return;
            performFind(text);
        });
    }

    private void performFind(String search) {
        String content = codeArea.getText();
        if (!search.equals(lastSearchText)) {
            lastFindIndex = -1;
        }

        int nextIndex = content.indexOf(search, lastFindIndex + 1);
        if (nextIndex == -1 && lastFindIndex > 0) {
            // wrap around
            nextIndex = content.indexOf(search);
        }

        if (nextIndex >= 0) {
            codeArea.selectRange(nextIndex, nextIndex + search.length());
            codeArea.requestFollowCaret();
            lastFindIndex = nextIndex;
            lastSearchText = search;
            showTemporaryStatus("Found \"" + search + "\"");
        } else {
            showTemporaryStatus("No more matches for \"" + search + "\"");
        }
    }

    @FXML
    private void onShowExosuitGrid() {
        showExosuitInventory();
    }

    public void showExosuitInventory() {
        Stage ownerStage = null;
        if (statusLabel != null && statusLabel.getScene() != null && statusLabel.getScene().getWindow() instanceof Stage stage) {
            ownerStage = stage;
        }
        showExosuitInventory(ownerStage);
    }

    public void showExosuitInventory(Stage ownerStage) {
        if (rootJsonElement == null) {
            statusLabel.setText("No save loaded.");
            return;
        }

        try {
            JsonObject root = rootJsonElement.getAsJsonObject();
            JsonObject inventoryRoot = resolveInventoryRoot(root);

            Map<String, InventoryPayload> inventories = new LinkedHashMap<>();

            List<BaseInventoryController> controllers = Arrays.asList(
                    new ExosuitInventoryController(),
                    new ExosuitTechController(),
                    new ShipInventoryController(),
                    new ShipTechInventoryController(),
                    new MultitoolInventoryController(),
                    new MultitoolTechInventoryController()
            );

            for (BaseInventoryController controller : controllers) {
                JsonArray slots = controller.resolveSlots(inventoryRoot);
                JsonArray valid = controller.resolveValidSlots(inventoryRoot);
                inventories.put(controller.inventoryName(), new InventoryPayload(slots, valid));
            }

            // Freighter inventory (no dedicated controller yet)
            JsonArray freighterSlots = null;
            JsonArray freighterValid = null;
            JsonObject contextHolder = inventoryRoot != null ? inventoryRoot : root;
            if (contextHolder != null && contextHolder.has("vLc")) {
                JsonObject base = contextHolder.getAsJsonObject("vLc");
                if (base.has("6f=")) {
                    JsonObject playerState = base.getAsJsonObject("6f=");
                    if (playerState.has("D3F")) {
                        JsonObject freighter = playerState.getAsJsonObject("D3F");
                        if (freighter.has(":No")) {
                            freighterSlots = freighter.getAsJsonArray(":No");
                            freighterValid = findValidSlotIndicesObfuscated(freighter);
                            inventories.put("Freighter", new InventoryPayload(freighterSlots, freighterValid));
                        }
                    }
                }
            }

            if (inventories.isEmpty()) {
                statusLabel.setText("No inventories found in current save.");
                return;
            }

            Stage invStage = new Stage();
            if (ownerStage != null) {
                invStage.initOwner(ownerStage);
            }
            invStage.setTitle("Inventory Editor (" + currentContextLabel() + ")");
            TabPane tabs = new TabPane();

            inventories.forEach((name, payload) -> {
                JsonArray slots = payload.slots();
                JsonArray valid = payload.validSlots();

                ScrollPane scroll = new ScrollPane();
                scroll.setFitToWidth(true);
                scroll.getStyleClass().add(INVENTORY_SCROLL_CLASS);

                GridPane grid = buildInventoryGrid(name, slots, valid != null ? valid : new JsonArray());
                scroll.setContent(grid);

                Tab tab = new Tab(name, scroll);
                ContextMenu contextMenu = new ContextMenu();
                MenuItem openWindow = new MenuItem("Open in New Window");
                openWindow.setOnAction(evt -> openInventoryWindow(invStage, name, slots, valid));
                contextMenu.getItems().add(openWindow);
                tab.setContextMenu(contextMenu);

                tabs.getTabs().add(tab);
            });

            // Add Storage Containers to map (for Manager Tab usage) without creating individual tabs
            for (int i = 0; i < 10; i++) {
                StorageInventoryController sc = new StorageInventoryController(i);
                JsonArray slots = sc.resolveSlots(inventoryRoot);
                JsonArray valid = sc.resolveValidSlots(inventoryRoot);
                inventories.put(sc.inventoryName(), new InventoryPayload(slots, valid));
            }

            Tab currencyTab = buildCurrencyTab(root);
            if (currencyTab != null) {
                tabs.getTabs().add(currencyTab);
            }

            Tab expeditionTab = buildExpeditionTab(root);
            if (expeditionTab != null) {
                tabs.getTabs().add(expeditionTab);
            }

            // Storage Manager Tab
            Tab storageTab = buildStorageManagerTab(inventories, invStage);
            tabs.getTabs().add(storageTab);

            if (tabs.getTabs().isEmpty()) {
                statusLabel.setText("Nothing available for display.");
                return;
            }

            Button saveButton = new Button("Save Changes");
            Label infoLabel = new Label("Ready.");
            saveButton.setOnAction(ev -> {
                boolean success = saveChanges();
                if (success) {
                    infoLabel.setText("Saved " + (currentSaveFile != null ? currentSaveFile.getName() : "changes") + ".");
                } else {
                    infoLabel.setText("Save failed. Check editor status.");
                }
            });

            Button closeButton = new Button("Close");
            closeButton.setOnAction(ev -> invStage.close());

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            HBox actionRow = new HBox(10, saveButton, spacer, closeButton);
            actionRow.setAlignment(Pos.CENTER_LEFT);
            actionRow.setPadding(new Insets(10, 10, 0, 10));

            VBox footer = new VBox(6, actionRow, infoLabel);
            footer.setPadding(new Insets(0, 10, 10, 10));

            BorderPane container = new BorderPane();
            container.setCenter(tabs);
            container.setBottom(footer);

            Scene scene = new Scene(container, 1120, 700);
            applyApplicationStyles(scene);
            invStage.setScene(scene);
            invStage.show();
            lastInventoryStage = invStage;
            invStage.setOnHidden(e -> lastInventoryStage = null);

            statusLabel.setText("Inventory editor opened (" + currentContextLabel() + ")");
        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("Failed to open inventory editor: " + e.getMessage());
        }
    }

    public Stage getLastInventoryStage() {
        return lastInventoryStage;
    }

    private void openInventoryWindow(Stage ownerStage, String name, JsonArray slots, JsonArray validSlotIndices) {
        Stage stage = new Stage();
        if (ownerStage != null) {
            stage.initOwner(ownerStage);
        }
        stage.setTitle(name + " Inventory (" + currentContextLabel() + ")");

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add(INVENTORY_SCROLL_CLASS);
        GridPane grid = buildInventoryGrid(name, slots, validSlotIndices != null ? validSlotIndices : new JsonArray());
        scrollPane.setContent(grid);

        Button saveButton = new Button("Save Changes");
        Label infoLabel = new Label("Ready.");
        saveButton.setOnAction(ev -> {
            boolean success = saveChanges();
            if (success) {
                infoLabel.setText("Saved " + (currentSaveFile != null ? currentSaveFile.getName() : "changes") + ".");
            } else {
                infoLabel.setText("Save failed. Check editor status.");
            }
        });

        Button closeButton = new Button("Close");
        closeButton.setOnAction(ev -> stage.close());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox actionRow = new HBox(10, saveButton, spacer, closeButton);
        actionRow.setAlignment(Pos.CENTER_LEFT);
        actionRow.setPadding(new Insets(10, 10, 0, 10));

        VBox footer = new VBox(6, actionRow, infoLabel);
        footer.setPadding(new Insets(0, 10, 10, 10));

        BorderPane container = new BorderPane();
        container.setCenter(scrollPane);
        container.setBottom(footer);

        Scene scene = new Scene(container, 1120, 700);
        applyApplicationStyles(scene);
        stage.setScene(scene);
        stage.show();
    }

    private Tab buildCurrencyTab(JsonObject root) {
        JsonObject playerState = getActivePlayerState(root);
        if (playerState == null) {
            return null;
        }

        VBox content = new VBox(18);
        content.setFillWidth(true);
        content.getStyleClass().add("currency-editor");

        GridPane grid = new GridPane();
        grid.getStyleClass().add("currency-grid");
        grid.setHgap(18);
        grid.setVgap(18);
        grid.setMaxWidth(Double.MAX_VALUE);

        ColumnConstraints columnOne = new ColumnConstraints();
        columnOne.setPercentWidth(33.3333);
        columnOne.setHgrow(Priority.ALWAYS);

        ColumnConstraints columnTwo = new ColumnConstraints();
        columnTwo.setPercentWidth(33.3333);
        columnTwo.setHgrow(Priority.ALWAYS);

        ColumnConstraints columnThree = new ColumnConstraints();
        columnThree.setPercentWidth(33.3334);
        columnThree.setHgrow(Priority.ALWAYS);

        grid.getColumnConstraints().addAll(columnOne, columnTwo, columnThree);

        Node units = buildCurrencyRow("Units", KEY_UNITS, ICON_UNITS, playerState);
        Node nanites = buildCurrencyRow("Nanites", KEY_NANITES, ICON_NANITE, playerState);
        Node quicksilver = buildCurrencyRow("Quicksilver", KEY_QUICKSILVER, ICON_QUICKSILVER, playerState);

        GridPane.setHgrow(units, Priority.ALWAYS);
        GridPane.setHgrow(nanites, Priority.ALWAYS);
        GridPane.setHgrow(quicksilver, Priority.ALWAYS);

        grid.add(units, 0, 0);
        grid.add(nanites, 0, 1);
        grid.add(quicksilver, 0, 2);

        content.getChildren().add(grid);
        VBox.setVgrow(grid, Priority.ALWAYS);

        Tab tab = new Tab("Currencies", content);
        tab.setClosable(false);
        return tab;
    }

    private Node buildCurrencyRow(String labelText, String jsonKey, String iconId, JsonObject playerState) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setFillHeight(true);
        row.setMaxWidth(Double.MAX_VALUE);
        row.getStyleClass().add("currency-row");

        Image icon = IconRegistry.getIcon(iconId);
        if (icon != null) {
            ImageView iconView = new ImageView(icon);
            iconView.setFitWidth(32);
            iconView.setFitHeight(32);
            iconView.setPreserveRatio(true);
            iconView.getStyleClass().add("currency-icon");
            row.getChildren().add(iconView);
        }

        Label label = new Label(labelText);
        label.getStyleClass().add("currency-label");
        row.getChildren().add(label);

        TextField field = new TextField(readCurrencyValue(playerState, jsonKey));
        field.getStyleClass().add("currency-field");
        field.setPrefColumnCount(12);
        field.setMaxWidth(Double.MAX_VALUE);
        UnaryOperator<TextFormatter.Change> filter = change -> {
            String next = change.getControlNewText();
            return next.matches("\\d{0,12}") ? change : null;
        };
        field.setTextFormatter(new TextFormatter<>(filter));
        field.setOnAction(evt -> commitCurrencyChange(field, playerState, jsonKey));
        field.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                commitCurrencyChange(field, playerState, jsonKey);
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        row.getChildren().add(spacer);
        row.getChildren().add(field);
        return row;
    }

    private String readCurrencyValue(JsonObject playerState, String key) {
        if (playerState != null && playerState.has(key)) {
            return String.valueOf(playerState.get(key).getAsLong());
        }
        return "0";
    }

    private void commitCurrencyChange(TextField field, JsonObject playerState, String key) {
        if (playerState == null) {
            return;
        }
        String text = field.getText();
        if (text == null || text.isBlank()) {
            text = "0";
        }
        try {
            long value = Long.parseLong(text);
            playerState.addProperty(key, value);
            markInventoryDirty();
        } catch (NumberFormatException ex) {
            field.setText(readCurrencyValue(playerState, key));
        }
    }

    /**
     * ====================== EXPEDITION EDITOR ======================
     **/

    private Tab buildExpeditionTab(JsonObject root) {
        if (root == null || !root.has(KEY_COMMON_STATE)) {
            return null;
        }

        JsonObject commonState = root.getAsJsonObject(KEY_COMMON_STATE);
        JsonObject seasonData = (commonState != null && commonState.has(KEY_SEASON_DATA))
                ? commonState.getAsJsonObject(KEY_SEASON_DATA)
                : null;
        if (seasonData == null || !seasonData.has(KEY_SEASON_STAGES)) {
            return null;
        }

        JsonArray stages = seasonData.getAsJsonArray(KEY_SEASON_STAGES);
        if (stages == null || stages.isEmpty()) {
            return null;
        }

        JsonObject seasonState = commonState.has(KEY_SEASON_STATE)
                ? commonState.getAsJsonObject(KEY_SEASON_STATE)
                : new JsonObject();
        if (!commonState.has(KEY_SEASON_STATE)) {
            commonState.add(KEY_SEASON_STATE, seasonState);
        }

        int totalMilestones = 0;
        for (JsonElement stageElement : stages) {
            if (!stageElement.isJsonObject()) {
                continue;
            }
            JsonObject stageObj = stageElement.getAsJsonObject();
            JsonArray stageMilestones = stageObj.has(KEY_STAGE_MILESTONES)
                    ? stageObj.getAsJsonArray(KEY_STAGE_MILESTONES)
                    : null;
            if (stageMilestones != null) {
                totalMilestones += stageMilestones.size();
            }
        }
        if (totalMilestones == 0) {
            return null;
        }

        JsonArray milestoneValues = seasonState.has(KEY_MILESTONE_VALUES)
                ? seasonState.getAsJsonArray(KEY_MILESTONE_VALUES)
                : new JsonArray();
        ensureMilestoneCapacity(milestoneValues, totalMilestones);
        seasonState.add(KEY_MILESTONE_VALUES, milestoneValues);

        VBox content = new VBox(18);
        content.setPadding(new Insets(16));
        content.getStyleClass().add("expedition-content");

        int milestoneOffset = 0;
        for (int stageIdx = 0; stageIdx < Math.min(5, stages.size()); stageIdx++) {
            JsonElement stageElement = stages.get(stageIdx);
            if (!stageElement.isJsonObject()) {
                continue;
            }
            JsonObject stageObj = stageElement.getAsJsonObject();
            JsonArray stageMilestones = stageObj.has(KEY_STAGE_MILESTONES)
                    ? stageObj.getAsJsonArray(KEY_STAGE_MILESTONES)
                    : null;
            Node stageSection = buildStageSection(stageObj, stageMilestones, stageIdx, milestoneValues, milestoneOffset);
            content.getChildren().add(stageSection);
            if (stageMilestones != null) {
                milestoneOffset += stageMilestones.size();
            }
        }

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add(INVENTORY_SCROLL_CLASS);

        Tab tab = new Tab("Expedition", scrollPane);
        tab.setClosable(false);
        return tab;
    }

    private void ensureMilestoneCapacity(JsonArray values, int requiredSize) {
        if (values == null) {
            return;
        }
        while (values.size() < requiredSize) {
            values.add(0);
        }
    }

    private Node buildStageSection(JsonObject stageObj, JsonArray stageMilestones, int stageIndex,
            JsonArray milestoneValues, int milestoneOffset) {
        VBox stageBox = new VBox(8);
        stageBox.getStyleClass().add("expedition-stage");

        String stageName = stageObj != null && stageObj.has("8wT")
                ? formatExpeditionToken(stageObj.get("8wT").getAsString())
                : "";
        String titleText = "Stage " + (stageIndex + 1);
        if (!stageName.isBlank()) {
            titleText += " – " + stageName;
        }
        Label title = new Label(titleText);
        title.getStyleClass().add("expedition-stage-title");
        stageBox.getChildren().add(title);

        if (stageMilestones == null || stageMilestones.isEmpty()) {
            Label empty = new Label("No missions found for this stage.");
            empty.getStyleClass().add("expedition-stage-empty");
            stageBox.getChildren().add(empty);
            return stageBox;
        }

        GridPane grid = new GridPane();
        grid.getStyleClass().add("expedition-grid");
        grid.setHgap(12);
        grid.setVgap(8);

        ColumnConstraints iconCol = new ColumnConstraints(EXPEDITION_ICON_SIZE + 12);
        iconCol.setHalignment(HPos.CENTER);
        ColumnConstraints nameCol = new ColumnConstraints();
        nameCol.setHgrow(Priority.ALWAYS);
        ColumnConstraints amountCol = new ColumnConstraints(120);
        amountCol.setHalignment(HPos.CENTER);
        ColumnConstraints valueCol = new ColumnConstraints(120);
        valueCol.setHalignment(HPos.CENTER);
        ColumnConstraints doneCol = new ColumnConstraints(80);
        doneCol.setHalignment(HPos.CENTER);
        grid.getColumnConstraints().addAll(iconCol, nameCol, amountCol, valueCol, doneCol);

        Label missionHeader = new Label("Mission");
        missionHeader.getStyleClass().add("expedition-header");
        grid.add(missionHeader, 1, 0);
        Label goalHeader = new Label("Goal");
        goalHeader.getStyleClass().add("expedition-header");
        grid.add(goalHeader, 2, 0);
        Label progressHeader = new Label("Progress");
        progressHeader.getStyleClass().add("expedition-header");
        grid.add(progressHeader, 3, 0);
        Label doneHeader = new Label("Done");
        doneHeader.getStyleClass().add("expedition-header");
        grid.add(doneHeader, 4, 0);

        for (int i = 0; i < stageMilestones.size(); i++) {
            JsonElement milestoneElement = stageMilestones.get(i);
            if (!milestoneElement.isJsonObject()) {
                continue;
            }
            JsonObject milestone = milestoneElement.getAsJsonObject();
            int rowIndex = i + 1;
            int milestoneIndex = milestoneOffset + i;

            Node iconNode = buildMilestoneIconNode(milestone);
            grid.add(iconNode, 0, rowIndex);

            String missionName = milestone.has(KEY_MISSION_NAME)
                    ? formatExpeditionToken(milestone.get(KEY_MISSION_NAME).getAsString())
                    : "Mission " + (i + 1);
            Label missionLabel = new Label(missionName);
            missionLabel.getStyleClass().add("expedition-mission-name");
            grid.add(missionLabel, 1, rowIndex);

            double goalValue = milestone.has(KEY_MISSION_AMOUNT)
                    ? milestone.get(KEY_MISSION_AMOUNT).getAsDouble()
                    : 0;
            Label amountLabel = new Label(formatQuantity(goalValue));
            amountLabel.getStyleClass().add("expedition-amount");
            grid.add(amountLabel, 2, rowIndex);

            CheckBox doneCheck = new CheckBox();
            doneCheck.setAllowIndeterminate(false);
            doneCheck.getStyleClass().add("expedition-done-check");

            final boolean[] updatingDone = {false};
            Consumer<Double> doneUpdater = newValue -> {
                boolean complete = Double.compare(newValue, goalValue) == 0;
                updatingDone[0] = true;
                doneCheck.setSelected(complete);
                updatingDone[0] = false;
            };

            TextField progressField = createProgressField(milestoneValues, milestoneIndex, doneUpdater);
            grid.add(progressField, 3, rowIndex);

            doneCheck.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
                if (updatingDone[0]) {
                    return;
                }
                if (isSelected) {
                    progressField.setText(formatQuantity(goalValue));
                    double updated = commitMilestoneValue(milestoneValues, milestoneIndex, progressField);
                    doneUpdater.accept(updated);
                }
            });
            grid.add(doneCheck, 4, rowIndex);
        }

        stageBox.getChildren().add(grid);
        return stageBox;
    }

    private TextField createProgressField(JsonArray milestoneValues, int index, Consumer<Double> postCommit) {
        double storedValue = getMilestoneValue(milestoneValues, index);
        TextField field = new TextField(formatQuantity(storedValue));
        field.setPrefWidth(80);
        field.getStyleClass().add("expedition-progress-field");
        field.setTextFormatter(new TextFormatter<>(change -> {
            String next = change.getControlNewText();
            return next.matches("\\d{0,7}") ? change : null;
        }));

        Runnable commit = () -> {
            double updated = commitMilestoneValue(milestoneValues, index, field);
            if (postCommit != null) {
                postCommit.accept(updated);
            }
        };
        field.focusedProperty().addListener((obs, oldFocus, newFocus) -> {
            if (!newFocus) {
                commit.run();
            }
        });
        field.setOnAction(evt -> commit.run());
        if (postCommit != null) {
            postCommit.accept(storedValue);
        }
        return field;
    }

    private double commitMilestoneValue(JsonArray milestoneValues, int index, TextField field) {
        if (milestoneValues == null || index < 0 || index >= milestoneValues.size()) {
            return 0;
        }
        double currentValue = milestoneValues.get(index).getAsDouble();
        String text = field.getText().trim();
        if (text.isEmpty()) {
            field.setText(formatQuantity(currentValue));
            return currentValue;
        }
        try {
            int parsed = Integer.parseInt(text);
            if (Double.compare(currentValue, parsed) != 0) {
                milestoneValues.set(index, new JsonPrimitive(parsed));
                currentValue = parsed;
                inventoryModified = true;
                statusLabel.setText("Expedition progress updated — remember to Save!");
            }
            field.setText(formatQuantity(currentValue));
            return currentValue;
        } catch (NumberFormatException ex) {
            field.setText(formatQuantity(currentValue));
        }
        return currentValue;
    }

    // ================= STORAGE MANAGER TAB =================

    private Tab buildStorageManagerTab(Map<String, InventoryPayload> inventories, Stage ownerStage) {
        VBox content = new VBox(14);
        content.setPadding(new Insets(16));
        content.setFillWidth(true);
        content.getStyleClass().add("storage-manager");

        Label header = new Label("Storage Container Management");
        header.getStyleClass().add("section-header");
        content.getChildren().add(header);

        // 1. Container Management Section
        HBox containerRow = new HBox(12);
        containerRow.setAlignment(Pos.CENTER_LEFT);

        ComboBox<String> containerCombo = new ComboBox<>();
        // Filter keys to only storage containers
        List<String> storageKeys = inventories.keySet().stream()
                .filter(k -> k.startsWith("Storage Container "))
                .sorted(Comparator.comparingInt(this::extractContainerNumber))
                .toList();

        containerCombo.setItems(FXCollections.observableArrayList(storageKeys));
        if (!storageKeys.isEmpty()) {
            containerCombo.getSelectionModel().select(0);
        }
        containerCombo.setPrefWidth(200);

        Button openButton = new Button("Open Container");
        openButton.setOnAction(e -> {
            String selected = containerCombo.getValue();
            if (selected != null && inventories.containsKey(selected)) {
                InventoryPayload p = inventories.get(selected);
                openInventoryWindow(ownerStage, selected, p.slots(), p.validSlots());
            }
        });

        containerRow.getChildren().addAll(new Label("Select Container:"), containerCombo, openButton);
        content.getChildren().add(containerRow);

        content.getChildren().add(new Separator());

        // 2. Global Search Section
        Label searchHeader = new Label("Global Item Search");
        searchHeader.getStyleClass().add("section-sub-header");
        content.getChildren().add(searchHeader);

        HBox searchRow = new HBox(12);
        searchRow.setAlignment(Pos.CENTER_LEFT);
        TextField searchField = new TextField();
        searchField.setPromptText("Search item name...");
        searchField.setPrefWidth(300);
        Button searchButton = new Button("Search");
        searchRow.getChildren().addAll(searchField, searchButton);
        content.getChildren().add(searchRow);

        ListView<HBox> resultsList = new ListView<>();
        resultsList.setPrefHeight(300);
        VBox.setVgrow(resultsList, Priority.ALWAYS);
        content.getChildren().add(resultsList);

        // Search Action
        Runnable doSearch = () -> {
            resultsList.getItems().clear();
            String query = searchField.getText().trim().toLowerCase();
            if (query.isEmpty()) return;

            for (String key : storageKeys) {
                InventoryPayload inventory = inventories.get(key);
                JsonArray slots = inventory.slots();
                for (JsonElement elem : slots) {
                    if (!elem.isJsonObject()) continue;
                    JsonObject slotObj = elem.getAsJsonObject();

                    // Extract Item details using same logic as buildInventoryGrid
                    String id = slotObj.has("b2n") ? slotObj.get("b2n").getAsString() : "?";
                    // Using ItemDefinitionRegistry would be ideal but it's static?
                    // In buildInventoryGrid: ItemDefinitionRegistry.getDisplayName(id)
                    String name = ItemDefinitionRegistry.getDisplayName(id);
                    if (name == null || name.isBlank()) {
                         // Fallback logic from buildInventoryGrid
                         // (Usually formatDisplayName(id))
                         name = id; // simplified for search logic or replicate formatDisplayName
                    }

                    if (name.toLowerCase().contains(query) || id.toLowerCase().contains(query)) {
                        // Found match
                        HBox itemRow = new HBox(10);
                        itemRow.setAlignment(Pos.CENTER_LEFT);
                        itemRow.setPadding(new Insets(4));
                        
                        // Icon
                        Image icon = IconRegistry.getIcon(id);
                        if (icon == null && id.startsWith("^") && id.length() > 1) {
                             icon = IconRegistry.getIcon(id.substring(1));
                        }
                        if (icon != null) {
                            ImageView iv = new ImageView(icon);
                            iv.setFitWidth(32);
                            iv.setFitHeight(32);
                            itemRow.getChildren().add(iv);
                        }

                        // Text
                        int amount = slotObj.has("1o9") ? slotObj.get("1o9").getAsInt() : 0;
                        Label info = new Label(name + " (" + amount + ") in " + key);
                        itemRow.getChildren().add(info);

                        // Click action
                        itemRow.setOnMouseClicked(ev -> {
                            if (ev.getClickCount() == 2) {
                                openInventoryWindow(ownerStage, key, inventory.slots(), inventory.validSlots());
                            }
                        });

                        resultsList.getItems().add(itemRow);
                    }
                }
            }

            if (resultsList.getItems().isEmpty()) {
                resultsList.getItems().add(new HBox(new Label("No results found in storage containers.")));
            }
        };

        searchButton.setOnAction(e -> doSearch.run());
        // Live search listener
        searchField.textProperty().addListener((obs, oldVal, newVal) -> doSearch.run());

        Tab tab = new Tab("Storage Manager", content);
        tab.setClosable(false);
        return tab;
    }

    private int extractContainerNumber(String containerName) {
        try {
            return Integer.parseInt(containerName.replace("Storage Container ", ""));
        } catch (NumberFormatException e) {
            return 999;
        }
    }

    private double getMilestoneValue(JsonArray milestoneValues, int index) {
        if (milestoneValues == null || index < 0 || index >= milestoneValues.size()) {
            return 0;
        }
        return milestoneValues.get(index).getAsDouble();
    }

    private Node buildMilestoneIconNode(JsonObject milestone) {
        String iconFilename = null;
        if (milestone != null && milestone.has(KEY_ICON)) {
            JsonObject iconObj = milestone.getAsJsonObject(KEY_ICON);
            if (iconObj != null && iconObj.has(KEY_ICON_FILENAME)) {
                iconFilename = iconObj.get(KEY_ICON_FILENAME).getAsString();
            }
        }
        Image icon = loadExpeditionIcon(iconFilename);
        if (icon != null) {
            ImageView imageView = new ImageView(icon);
            imageView.setFitWidth(EXPEDITION_ICON_SIZE);
            imageView.setFitHeight(EXPEDITION_ICON_SIZE);
            imageView.setPreserveRatio(true);
            return imageView;
        }
        StackPane placeholder = new StackPane();
        placeholder.setPrefSize(EXPEDITION_ICON_SIZE, EXPEDITION_ICON_SIZE);
        placeholder.getStyleClass().add("expedition-icon-placeholder");
        Label symbol = new Label("◆");
        symbol.getStyleClass().add("expedition-icon-placeholder-symbol");
        placeholder.getChildren().add(symbol);
        return placeholder;
    }

    private Image loadExpeditionIcon(String iconFilename) {
        if (iconFilename == null || iconFilename.isBlank()) {
            return null;
        }
        String normalised = iconFilename.replace('\\', '/');
        int lastSlash = normalised.lastIndexOf('/');
        String leafName = lastSlash >= 0 ? normalised.substring(lastSlash + 1) : normalised;
        String pngName = leafName.replace(".DDS", ".png").replace(".dds", ".png");
        if (pngName.isBlank()) {
            return null;
        }
        String resourcePath = "/icons/expedition/" + pngName.toLowerCase(Locale.ROOT);
        return expeditionIconCache.computeIfAbsent(resourcePath, key -> {
            var stream = getClass().getResourceAsStream(key);
            if (stream == null) {
                return null;
            }
            return new Image(stream, EXPEDITION_ICON_SIZE, EXPEDITION_ICON_SIZE, true, true);
        });
    }

    private String formatExpeditionToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String value = raw.startsWith("^") ? raw.substring(1) : raw;
        value = value.replace('_', ' ').trim();
        if (value.isEmpty()) {
            return raw;
        }
        return value;
    }

    private String formatQuantity(double value) {
        double rounded = Math.rint(value);
        if (Math.abs(value - rounded) < 0.0001) {
            return Long.toString(Math.round(rounded));
        }
        return Double.toString(value);
    }

    /**
     * Simple container tying a set of slot/valid arrays together.
     */
    private static final class InventoryPayload {
        private final JsonArray slots;
        private final JsonArray validSlots;

        InventoryPayload(JsonArray slots, JsonArray validSlots) {
            this.slots = (slots != null) ? slots : new JsonArray();
            this.validSlots = validSlots;
        }

        JsonArray slots() {
            return slots;
        }

        JsonArray validSlots() {
            return validSlots;
        }
    }

    /**
     * Find :Nq or equivalent obfuscated ValidSlotIndices array
     */
    private JsonArray findValidSlotIndicesObfuscated(JsonObject inventoryObj) {
        if (inventoryObj.has(":Nq") && inventoryObj.get(":Nq").isJsonArray()) {
            return inventoryObj.getAsJsonArray(":Nq");
        }
        // fallback: search any array of objects containing (=Tb, N9>)
        for (Map.Entry<String, JsonElement> e : inventoryObj.entrySet()) {
            JsonElement val = e.getValue();
            if (val.isJsonArray()) {
                JsonArray arr = val.getAsJsonArray();
                if (arr.size() == 0) continue;
                JsonElement first = arr.get(0);
                if (first.isJsonObject()) {
                    JsonObject obj = first.getAsJsonObject();
                    if (obj.has("=Tb") && obj.has("N9>")) return arr;
                }
            }
        }
        return new JsonArray();
    }


    private GridPane buildInventoryGrid(String title, JsonArray slotsArray, JsonArray validSlotIndices) {
        GridPane grid = new GridPane();
        grid.setPadding(new Insets(10));
        grid.setHgap(1);
        grid.setVgap(1);
        grid.setAlignment(Pos.TOP_LEFT);
        grid.getStyleClass().add(INVENTORY_GRID_CLASS);

        final int CELL_SIZE = 100;
        final int GRID_WIDTH = 10;


        // Determine grid height
        int maxY = 0;
        for (JsonElement elem : validSlotIndices) {
            if (!elem.isJsonObject()) continue;
            JsonObject idx = elem.getAsJsonObject();
            int y = idx.has("XJ>") ? idx.get("XJ>").getAsInt() : 0;
            maxY = Math.max(maxY, y);
        }
        for (JsonElement elem : slotsArray) {
            if (!elem.isJsonObject()) continue;
            JsonObject item = elem.getAsJsonObject();
            JsonObject idx = item.has("3ZH") ? item.getAsJsonObject("3ZH") : null;
            if (idx != null && idx.has("XJ>")) {
                maxY = Math.max(maxY, idx.get("XJ>").getAsInt());
            }
        }

        // Lock row/column constraints
        grid.getColumnConstraints().clear();
        grid.getRowConstraints().clear();

        for (int i = 0; i < GRID_WIDTH; i++) {
            ColumnConstraints cc = new ColumnConstraints(CELL_SIZE, CELL_SIZE, CELL_SIZE);
            cc.setHalignment(HPos.CENTER);
            cc.setFillWidth(true);
            grid.getColumnConstraints().add(cc);
        }

        for (int i = 0; i <= maxY; i++) {
            RowConstraints rc = new RowConstraints(CELL_SIZE, CELL_SIZE, CELL_SIZE);
            rc.setValignment(VPos.CENTER);
            rc.setFillHeight(true);
            grid.getRowConstraints().add(rc);
        }

        // Draw base grid
        for (int y = 0; y <= maxY; y++) {
            for (int x = 0; x < GRID_WIDTH; x++) {
                StackPane slot = new StackPane();
                slot.setPrefSize(CELL_SIZE, CELL_SIZE);
                slot.setMinSize(CELL_SIZE, CELL_SIZE);
                slot.setMaxSize(CELL_SIZE, CELL_SIZE);
                slot.getStyleClass().add(INVENTORY_PLACEHOLDER_CLASS);
                final int cx = x;
                final int cy = y;
                attachSlotContextMenu(slot, title, grid, slotsArray, validSlotIndices, cx, cy);
                grid.add(slot, x, y);
            }
        }

        // Add items
        for (int i = 0; i < slotsArray.size(); i++) {
            JsonObject item = slotsArray.get(i).getAsJsonObject();
            JsonObject idx = item.has("3ZH") ? item.getAsJsonObject("3ZH") : null;
            int x = (idx != null && idx.has(">Qh")) ? idx.get(">Qh").getAsInt() : i % GRID_WIDTH;
            int y = (idx != null && idx.has("XJ>")) ? idx.get("XJ>").getAsInt() : i / GRID_WIDTH;

            // --- Build item cell ---
            BorderPane cell = new BorderPane();
            cell.setPrefSize(CELL_SIZE, CELL_SIZE);
            cell.setMinSize(CELL_SIZE, CELL_SIZE);
            cell.setMaxSize(CELL_SIZE, CELL_SIZE);
            cell.getStyleClass().add(INVENTORY_PANE_CLASS);

            //JsonObject typeObj = item.has("Vn8") ? item.getAsJsonObject("Vn8") : null;
            //String type = (typeObj != null && typeObj.has("elv")) ? typeObj.get("elv").getAsString() : "Unknown";
            String id = item.has("b2n") ? item.get("b2n").getAsString() : "Unknown";
            int amount = item.has("1o9") ? item.get("1o9").getAsInt() : 1;
            int max = item.has("F9q") ? item.get("F9q").getAsInt() : 0;

            String displayName = ItemDefinitionRegistry.getDisplayName(id);
            if (displayName == null || displayName.isBlank()) {
                displayName = formatDisplayName(id);
            }
            Text nameText = new Text(displayName);
            nameText.setTextAlignment(TextAlignment.CENTER);
            nameText.getStyleClass().add(INVENTORY_NAME_CLASS);
            nameText.setVisible(false);
            Text amountText = new Text(amount + (max > 0 ? "/" + max : ""));
            amountText.getStyleClass().add(INVENTORY_AMOUNT_CLASS);

            VBox content = new VBox();
            content.setAlignment(Pos.CENTER);
            content.setSpacing(4);
            content.setPrefSize(CELL_SIZE, CELL_SIZE);
            Image icon = IconRegistry.getIcon(id);
            if (icon == null && id.startsWith("^") && id.length() > 1) {
                icon = IconRegistry.getIcon(id.substring(1));
            }
            if (icon != null) {
                ImageView imageView = new ImageView(icon);
                imageView.setFitWidth(INVENTORY_ICON_SIZE);
                imageView.setFitHeight(INVENTORY_ICON_SIZE);
                imageView.setPreserveRatio(true);
                content.getChildren().add(imageView);
            } else if (loggedMissingIcons.add(id)) {
                System.err.println("[InventoryIcon] Missing icon for " + id);
            }
            content.getChildren().add(amountText);

            StackPane nameLayer = new StackPane(nameText);
            nameLayer.setMouseTransparent(true);
            nameLayer.setPickOnBounds(false);
            nameLayer.setVisible(false);
            StackPane.setAlignment(nameText, Pos.TOP_CENTER);
            StackPane.setAlignment(nameLayer, Pos.TOP_CENTER);

            StackPane layeredContent = new StackPane(content, nameLayer);
            cell.setCenter(layeredContent);

            cell.hoverProperty().addListener((obs, wasHovering, isHovering) -> {
                nameLayer.setVisible(isHovering);
                nameText.setVisible(isHovering);
            });

            final int startX = x;
            final int startY = y;

            // --- Enable drag ---
            cell.setOnDragDetected(e -> {
                Dragboard db = cell.startDragAndDrop(TransferMode.MOVE);
                ClipboardContent cc = new ClipboardContent();
                cc.putString(startX + "," + startY);
                db.setContent(cc);
                cell.setOpacity(0.5);
                e.consume();
            });

            cell.setOnDragDone(e -> cell.setOpacity(1.0));

            Node existing = getNodeFromGrid(grid, x, y);
            if (existing instanceof StackPane sp) {
                sp.getChildren().setAll(cell);
                bindSlotContextMenu(sp, cell);
            } else {
                StackPane sp = new StackPane(cell);
                sp.setPrefSize(CELL_SIZE, CELL_SIZE);
                sp.getStyleClass().add(INVENTORY_PLACEHOLDER_CLASS);
                 attachSlotContextMenu(sp, title, grid, slotsArray, validSlotIndices, x, y);
                 bindSlotContextMenu(sp, cell);
                grid.add(sp, x, y);
            }
        }
        // ---------------------------------------------------------
        for (Node n : grid.getChildren()) {
            n.setOnDragOver(evt -> {
                if (evt.getGestureSource() != n && evt.getDragboard().hasString()) {
                    evt.acceptTransferModes(TransferMode.MOVE);
                }
                evt.consume();
            });

            n.setOnDragDropped(evt -> {
                Dragboard db = evt.getDragboard();
                if (!db.hasString()) return;

                String[] parts = db.getString().split(",");
                int srcX = Integer.parseInt(parts[0]);
                int srcY = Integer.parseInt(parts[1]);
                Integer destX = GridPane.getColumnIndex(n);
                Integer destY = GridPane.getRowIndex(n);
                if (destX == null || destY == null) return;

                moveOrSwapItem(slotsArray, srcX, srcY, destX, destY);
                inventoryModified = true;
                statusLabel.setText("Pending changes — remember to Save!");
                refreshGridContents(grid, title, slotsArray, validSlotIndices);

                evt.setDropCompleted(true);
                evt.consume();
            });
        }

        grid.setMinWidth((CELL_SIZE + grid.getHgap()) * GRID_WIDTH);
        return grid;
    }

    private void moveOrSwapItem(JsonArray slots, int srcX, int srcY, int destX, int destY) {
        JsonObject srcItem = null;
        JsonObject destItem = null;
        int srcIndex = -1, destIndex = -1;

        for (int i = 0; i < slots.size(); i++) {
            JsonObject obj = slots.get(i).getAsJsonObject();
            if (!obj.has("3ZH")) continue;
            JsonObject idx = obj.getAsJsonObject("3ZH");
            if (!idx.has(">Qh") || !idx.has("XJ>")) continue;

            int x = idx.get(">Qh").getAsInt();
            int y = idx.get("XJ>").getAsInt();

            if (x == srcX && y == srcY) {
                srcItem = obj;
                srcIndex = i;
            } else if (x == destX && y == destY) {
                destItem = obj;
                destIndex = i;
            }
        }

        if (srcItem == null) return;

        if (destItem != null) {
            // --- Swap two items ---
            srcItem.getAsJsonObject("3ZH").addProperty(">Qh", destX);
            srcItem.getAsJsonObject("3ZH").addProperty("XJ>", destY);
            destItem.getAsJsonObject("3ZH").addProperty(">Qh", srcX);
            destItem.getAsJsonObject("3ZH").addProperty("XJ>", srcY);
            slots.set(srcIndex, destItem);
            slots.set(destIndex, srcItem);
        } else {
            // --- Move to empty slot ---
            srcItem.getAsJsonObject("3ZH").addProperty(">Qh", destX);
            srcItem.getAsJsonObject("3ZH").addProperty("XJ>", destY);
        }
    }

    // Refresh grid visuals in place (no rebuild, no resizing bugs).
    private void refreshGridContents(GridPane grid, String title, JsonArray slotsArray, JsonArray validSlotIndices) {
        final int CELL_SIZE = 100;
        final int GRID_WIDTH = 10;

        // Clear all children from StackPanes but preserve sizing and style
        for (Node n : grid.getChildren()) {
            if (n instanceof StackPane sp) {
                sp.getChildren().clear();
                sp.setPrefSize(CELL_SIZE, CELL_SIZE);
                sp.setMinSize(CELL_SIZE, CELL_SIZE);
                sp.setMaxSize(CELL_SIZE, CELL_SIZE);
                if (!sp.getStyleClass().contains(INVENTORY_PLACEHOLDER_CLASS)) {
                    sp.getStyleClass().add(INVENTORY_PLACEHOLDER_CLASS);
                }
                sp.getStyleClass().remove(INVENTORY_HIGHLIGHT_CLASS);
            }
        }

        // Re-add each item to its assigned slot
        for (int i = 0; i < slotsArray.size(); i++) {
            JsonObject item = slotsArray.get(i).getAsJsonObject();
            JsonObject idx = item.has("3ZH") ? item.getAsJsonObject("3ZH") : null;
            if (idx == null) continue;

            int x = idx.has(">Qh") ? idx.get(">Qh").getAsInt() : i % GRID_WIDTH;
            int y = idx.has("XJ>") ? idx.get("XJ>").getAsInt() : i / GRID_WIDTH;

            BorderPane cell = new BorderPane();
            cell.setPrefSize(CELL_SIZE, CELL_SIZE);
            cell.setMinSize(CELL_SIZE, CELL_SIZE);
            cell.setMaxSize(CELL_SIZE, CELL_SIZE);
            cell.getStyleClass().add(INVENTORY_PANE_CLASS);

            //JsonObject typeObj = item.has("Vn8") ? item.getAsJsonObject("Vn8") : null;
            //String type = (typeObj != null && typeObj.has("elv")) ? typeObj.get("elv").getAsString() : "Unknown";
            String id = item.has("b2n") ? item.get("b2n").getAsString() : "Unknown";
            int amount = item.has("1o9") ? item.get("1o9").getAsInt() : 1;
            int max = item.has("F9q") ? item.get("F9q").getAsInt() : 0;

            String displayName = ItemDefinitionRegistry.getDisplayName(id);
            if (displayName == null || displayName.isBlank()) {
                displayName = formatDisplayName(id);
            }

            Text nameText = new Text(displayName);
            nameText.setTextAlignment(TextAlignment.CENTER);
            nameText.getStyleClass().add(INVENTORY_NAME_CLASS);
            nameText.setVisible(false);
            Text amountText = new Text(amount + (max > 0 ? "/" + max : ""));
            amountText.getStyleClass().add(INVENTORY_AMOUNT_CLASS);

            VBox box = new VBox();
            box.setAlignment(Pos.CENTER);
            box.setSpacing(4);
            box.setPrefSize(CELL_SIZE, CELL_SIZE);
            Image icon = IconRegistry.getIcon(id);
            if (icon == null && id.startsWith("^") && id.length() > 1) {
                icon = IconRegistry.getIcon(id.substring(1));
            }
            if (icon != null) {
                ImageView imageView = new ImageView(icon);
                imageView.setFitWidth(INVENTORY_ICON_SIZE);
                imageView.setFitHeight(INVENTORY_ICON_SIZE);
                imageView.setPreserveRatio(true);
                box.getChildren().add(imageView);
            } else if (loggedMissingIcons.add(id)) {
                System.err.println("[InventoryIcon] Missing icon for " + id);
            }
            box.getChildren().add(amountText);

            StackPane nameLayer = new StackPane(nameText);
            nameLayer.setMouseTransparent(true);
            nameLayer.setPickOnBounds(false);
            nameLayer.setVisible(false);
            StackPane.setAlignment(nameText, Pos.TOP_CENTER);
            StackPane.setAlignment(nameLayer, Pos.TOP_CENTER);

            StackPane layeredContent = new StackPane(box, nameLayer);
            cell.setCenter(layeredContent);

            cell.hoverProperty().addListener((obs, wasHovering, isHovering) -> {
                nameLayer.setVisible(isHovering);
                nameText.setVisible(isHovering);
            });

            final int startX = x;
            final int startY = y;
            cell.setOnDragDetected(e -> {
                Dragboard db = cell.startDragAndDrop(TransferMode.MOVE);
                ClipboardContent cc = new ClipboardContent();
                cc.putString(startX + "," + startY);
                db.setContent(cc);
                e.consume();
            });
            cell.setOnDragDone(e -> {});

            Node existing = getNodeFromGrid(grid, x, y);
            if (existing instanceof StackPane sp) {
                sp.getChildren().setAll(cell);
                bindSlotContextMenu(sp, cell);
            }
        }

        // Force a clean layout pass
        grid.requestLayout();
    }


    /**
     * Get a node from grid coordinates.
     */
    private Node getNodeFromGrid(GridPane grid, int col, int row) {
        for (Node node : grid.getChildren()) {
            Integer c = GridPane.getColumnIndex(node);
            Integer r = GridPane.getRowIndex(node);
            if (c != null && r != null && c == col && r == row) return node;
        }
        return null;
    }

    private String formatDisplayName(String rawId) {
        if (rawId == null || rawId.isEmpty()) {
            return "Unknown";
        }
        String cleaned = rawId.startsWith("^") ? rawId.substring(1) : rawId;
        cleaned = cleaned.replace('_', ' ').trim();
        return cleaned;
    }

    /**
     * Moves an item between coordinates in the same inventory.
     */
    private void moveItem(JsonArray slots, int srcX, int srcY, int destX, int destY) {
        JsonObject movingItem = null;
        int srcIndex = -1;

        for (int i = 0; i < slots.size(); i++) {
            JsonObject obj = slots.get(i).getAsJsonObject();
            if (!obj.has("3ZH")) continue;
            JsonObject idx = obj.getAsJsonObject("3ZH");
            if (idx.has(">Qh") && idx.has("XJ>")) {
                if (idx.get(">Qh").getAsInt() == srcX && idx.get("XJ>").getAsInt() == srcY) {
                    movingItem = obj.deepCopy();
                    srcIndex = i;
                    break;
                }
            }
        }

        if (movingItem == null) return;

        // Update the item's coordinates
        movingItem.getAsJsonObject("3ZH").addProperty(">Qh", destX);
        movingItem.getAsJsonObject("3ZH").addProperty("XJ>", destY);

        // Replace or move
        if (srcIndex != -1) {
            slots.remove(srcIndex);
        }
        slots.add(movingItem);
    }

    /**
     * Utility: clear and rebuild the grid after a change.
     */
    private void rebuildGrid(String title, GridPane grid, JsonArray slotsArray, JsonArray validSlotIndices) {
        grid.getChildren().clear();
        GridPane rebuilt = buildInventoryGrid(title, slotsArray, validSlotIndices);
        grid.getChildren().addAll(rebuilt.getChildren());
    }

    /**
     * Shows edit dialog for a single item and updates JSON + UI immediately.
     */
    private void showEditDialog(String inventoryName, JsonArray slots, int index, Text idLabel, Text amountLabel) {
        JsonObject item = slots.get(index).getAsJsonObject();
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit " + inventoryName + " Item");
        dialog.setHeaderText("Modify slot " + index);

        TextField idField = new TextField(item.has("b2n") ? item.get("b2n").getAsString() : "");
        TextField amtField = new TextField(item.has("1o9") ? String.valueOf(item.get("1o9").getAsInt()) : "");

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.setPadding(new Insets(20, 10, 10, 10));
        form.addRow(0, new Label("Item ID:"), idField);
        form.addRow(1, new Label("Amount:"), amtField);

        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(bt -> {
            if (bt == ButtonType.OK) {
                String newId = idField.getText().trim();
                String newAmt = amtField.getText().trim();

                if (!newId.isEmpty()) item.addProperty("b2n", newId);
                try {
                    int amt = Integer.parseInt(newAmt);
                    item.addProperty("1o9", amt);
                } catch (NumberFormatException ignored) {
                }

                // update visible labels
                idLabel.setText(newId);
                amountLabel.setText(newAmt);
            }
            return null;
        });

        dialog.showAndWait();
    }

    private void attachSlotContextMenu(StackPane slot, String inventoryTitle, GridPane grid,
                                       JsonArray slotsArray, JsonArray validSlotIndices,
                                       int x, int y) {
        ContextMenu menu = new ContextMenu();
        menu.setStyle("-fx-background-color: black;");
        MenuItem changeAmount = new MenuItem("Change Amount…");
        changeAmount.setOnAction(evt -> changeItemAmount(inventoryTitle, grid, slotsArray, validSlotIndices, x, y));
        MenuItem deleteItem = new MenuItem("Delete Item");
        deleteItem.setOnAction(evt -> deleteItemAt(inventoryTitle, grid, slotsArray, validSlotIndices, x, y));
        MenuItem addItem = new MenuItem("Add Item…");
        addItem.setOnAction(evt -> addItemToSlot(inventoryTitle, grid, slotsArray, validSlotIndices, x, y));
        menu.getItems().setAll(changeAmount, deleteItem, addItem);

        slot.addEventHandler(ContextMenuEvent.CONTEXT_MENU_REQUESTED, evt -> {
            JsonObject existing = findItemAt(slotsArray, x, y);
            boolean hasItem = existing != null;
            changeAmount.setDisable(!hasItem);
            deleteItem.setDisable(!hasItem);
            addItem.setDisable(hasItem);
            menu.show(slot, evt.getScreenX(), evt.getScreenY());
            evt.consume();
        });
    }

    private void bindSlotContextMenu(StackPane slot, Node child) {
        if (slot == null || child == null) {
            return;
        }
        child.addEventHandler(ContextMenuEvent.CONTEXT_MENU_REQUESTED, evt -> {
            slot.fireEvent(evt.copyFor(slot, slot));
            evt.consume();
        });
    }

    private void changeItemAmount(String inventoryTitle, GridPane grid, JsonArray slotsArray,
                                  JsonArray validSlotIndices, int x, int y) {
        JsonObject item = findItemAt(slotsArray, x, y);
        if (item == null) {
            return;
        }
        String id = item.has("b2n") ? item.get("b2n").getAsString() : "";
        int currentAmount = item.has("1o9") ? item.get("1o9").getAsInt() : 1;

        TextInputDialog dialog = new TextInputDialog(String.valueOf(currentAmount));
        dialog.setTitle("Change Amount");
        dialog.setHeaderText("Set amount for " + formatDisplayName(id));
        dialog.setContentText("Amount:");
        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty()) {
            return;
        }
        try {
            int updatedAmount = Integer.parseInt(result.get().trim());
            if (updatedAmount <= 0) {
                return;
            }
            item.addProperty("1o9", updatedAmount);
            int currentMax = item.has("F9q") ? item.get("F9q").getAsInt() : 0;
            if (updatedAmount > currentMax) {
                item.addProperty("F9q", updatedAmount);
            }
            markInventoryDirty();
            refreshGridContents(grid, inventoryTitle, slotsArray, validSlotIndices);
        } catch (NumberFormatException ignored) {
        }
    }

    private void deleteItemAt(String inventoryTitle, GridPane grid, JsonArray slotsArray, JsonArray validSlotIndices, int x, int y) {
        for (int i = 0; i < slotsArray.size(); i++) {
            JsonObject obj = slotsArray.get(i).getAsJsonObject();
            JsonObject idx = obj.has("3ZH") ? obj.getAsJsonObject("3ZH") : null;
            if (idx == null) {
                continue;
            }
            if (idx.has(">Qh") && idx.has("XJ>") && idx.get(">Qh").getAsInt() == x && idx.get("XJ>").getAsInt() == y) {
                slotsArray.remove(i);
                markInventoryDirty();
                refreshGridContents(grid, inventoryTitle, slotsArray, validSlotIndices);
                return;
            }
        }
    }

    private void addItemToSlot(String inventoryTitle, GridPane grid, JsonArray slotsArray,
                               JsonArray validSlotIndices, int x, int y) {
        Window owner = grid.getScene() != null ? grid.getScene().getWindow() : null;
        Optional<ItemSelection> selection = showFindItemDialog(inventoryTitle, slotsArray, owner);
        if (selection.isEmpty()) {
            return;
        }
        ItemSelection chosen = selection.get();
        JsonObject newItem = createInventoryItem(chosen.entry(), chosen.amount(), x, y);
        slotsArray.add(newItem);
        markInventoryDirty();
        refreshGridContents(grid, inventoryTitle, slotsArray, validSlotIndices);
    }

    private JsonObject createInventoryItem(ItemCatalog.ItemEntry entry, int amount, int x, int y) {
        JsonObject newItem = new JsonObject();
        String id = entry.id();
        if (!id.startsWith("^")) {
            id = "^" + id;
        }
        newItem.addProperty("b2n", id);

        JsonObject type = new JsonObject();
        type.addProperty("elv", entry.type().inventoryValue());
        newItem.add("Vn8", type);

        newItem.addProperty("1o9", amount);
        int suggestedMax = entry.maxStack() > 0 ? entry.maxStack() : amount;
        newItem.addProperty("F9q", Math.max(amount, suggestedMax));
        newItem.addProperty("eVk", 0.0d);
        newItem.addProperty("b76", true);

        JsonObject index = new JsonObject();
        index.addProperty(">Qh", x);
        index.addProperty("XJ>", y);
        newItem.add("3ZH", index);
        return newItem;
    }

    private Optional<ItemSelection> showFindItemDialog(String inventoryTitle, JsonArray slotsArray, Window owner) {
        EnumSet<ItemCatalog.ItemType> allowedTypes = determineAllowedItemTypes(inventoryTitle, slotsArray);
        List<ItemCatalog.ItemEntry> entries = ItemCatalog.getItemsForTypes(allowedTypes);
        if (entries.isEmpty()) {
            statusLabel.setText("No catalog entries available for " + inventoryTitle);
            return Optional.empty();
        }

        Stage dialog = new Stage();
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle("Find Item – " + inventoryTitle);

        TextField searchField = new TextField();
        searchField.setPromptText("Search by name or ID…");
        searchField.getStyleClass().add("item-search-field");

        ObservableList<ItemCatalog.ItemEntry> master = FXCollections.observableArrayList(entries);
        FilteredList<ItemCatalog.ItemEntry> filtered = new FilteredList<>(master, item -> true);
        SortedList<ItemCatalog.ItemEntry> sorted = new SortedList<>(
                filtered,
                Comparator.comparing(ItemCatalog.ItemEntry::displayName, String.CASE_INSENSITIVE_ORDER)
        );

        ListView<ItemCatalog.ItemEntry> listView = new ListView<>(sorted);
        listView.setPrefHeight(360);
        listView.setCellFactory(lv -> new ListCell<>() {
            private final ImageView iconView = new ImageView();
            private final Label nameLabel = new Label();
            private final Label typeLabel = new Label();
            private final VBox textBox = new VBox(nameLabel, typeLabel);
            private final HBox content = new HBox(12, iconView, textBox);
            {
                iconView.setFitWidth(36);
                iconView.setFitHeight(36);
                iconView.setPreserveRatio(true);
                textBox.setSpacing(2);
                nameLabel.getStyleClass().add("inventory-slot-name");
                typeLabel.getStyleClass().add("inventory-slot-amount");
                content.setAlignment(Pos.CENTER_LEFT);
            }

            @Override
            protected void updateItem(ItemCatalog.ItemEntry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                Image icon = IconRegistry.getIcon(item.id());
                if (icon == null) {
                    icon = IconRegistry.getIcon("^" + item.id());
                }
                iconView.setImage(icon);
                nameLabel.setText(item.displayName());
                typeLabel.setText(item.type().label());
                setGraphic(content);
            }
        });

        searchField.textProperty().addListener((obs, oldText, newText) -> {
            String query = newText == null ? "" : newText.trim().toLowerCase(Locale.ROOT);
            filtered.setPredicate(entry -> {
                if (query.isEmpty()) {
                    return true;
                }
                return entry.displayName().toLowerCase(Locale.ROOT).contains(query)
                        || entry.id().toLowerCase(Locale.ROOT).contains(query);
            });
        });

        TextField quantityField = new TextField();
        quantityField.setPromptText("Amount");
        UnaryOperator<TextFormatter.Change> quantityFilter = change -> {
            String next = change.getControlNewText();
            return next.matches("\\d{0,5}") ? change : null;
        };
        quantityField.setTextFormatter(new TextFormatter<>(quantityFilter));

        listView.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> {
            if (newItem == null) {
                quantityField.setPromptText("Amount");
                return;
            }
            int suggested = Math.min(newItem.maxStack(), Math.max(1, newItem.type().defaultSuggestedAmount()));
            quantityField.setPromptText("Max " + newItem.maxStack());
            if (quantityField.getText().isBlank()) {
                quantityField.setText(String.valueOf(suggested > 0 ? suggested : 1));
            }
        });

        Button addButton = new Button("Add");
        Button cancelButton = new Button("Cancel");
        addButton.setDefaultButton(true);
        cancelButton.setCancelButton(true);
        addButton.getStyleClass().add("item-search-button");
        cancelButton.getStyleClass().add("item-search-button");

        addButton.disableProperty().bind(Bindings.createBooleanBinding(
                () -> listView.getSelectionModel().getSelectedItem() == null || !isPositiveInteger(quantityField.getText()),
                listView.getSelectionModel().selectedItemProperty(),
                quantityField.textProperty()
        ));

        HBox controls = new HBox(10, new Label("Amount:"), quantityField, addButton, cancelButton);
        controls.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(12, searchField, listView, controls);
        root.setPadding(new Insets(10));

        Scene scene = new Scene(root, 520, 520);
        applyApplicationStyles(scene);
        dialog.setScene(scene);

        final ItemSelection[] selection = new ItemSelection[1];
        Runnable commitSelection = () -> {
            ItemCatalog.ItemEntry chosen = listView.getSelectionModel().getSelectedItem();
            if (chosen == null) {
                return;
            }
            try {
                int requested = Integer.parseInt(quantityField.getText());
                if (requested <= 0) {
                    return;
                }
                if (chosen.maxStack() > 0) {
                    requested = Math.min(requested, chosen.maxStack());
                }
                selection[0] = new ItemSelection(chosen, requested);
                dialog.close();
            } catch (NumberFormatException ignored) {
            }
        };

        addButton.setOnAction(evt -> commitSelection.run());
        cancelButton.setOnAction(evt -> dialog.close());
        listView.setOnMouseClicked(evt -> {
            if (evt.getClickCount() == 2) {
                commitSelection.run();
            }
        });

        Platform.runLater(searchField::requestFocus);
        dialog.showAndWait();
        return Optional.ofNullable(selection[0]);
    }

    private EnumSet<ItemCatalog.ItemType> determineAllowedItemTypes(String inventoryTitle, JsonArray slotsArray) {
        EnumSet<ItemCatalog.ItemType> types = EnumSet.noneOf(ItemCatalog.ItemType.class);
        if (slotsArray != null) {
            for (JsonElement element : slotsArray) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject obj = element.getAsJsonObject();
                JsonObject typeObj = obj.has("Vn8") ? obj.getAsJsonObject("Vn8") : null;
                String value = (typeObj != null && typeObj.has("elv")) ? typeObj.get("elv").getAsString() : null;
                ItemCatalog.ItemType resolved = ItemCatalog.ItemType.fromInventoryValue(value);
                if (resolved != ItemCatalog.ItemType.UNKNOWN) {
                    types.add(resolved);
                }
            }
        }
        if (!types.isEmpty()) {
            return types;
        }
        String lower = inventoryTitle == null ? "" : inventoryTitle.toLowerCase(Locale.ROOT);
        if (lower.contains("tech") || lower.contains("multi")) {
            return EnumSet.of(ItemCatalog.ItemType.TECHNOLOGY);
        }
        return EnumSet.of(ItemCatalog.ItemType.SUBSTANCE, ItemCatalog.ItemType.PRODUCT);
    }

    private JsonObject findItemAt(JsonArray slots, int x, int y) {
        for (int i = 0; i < slots.size(); i++) {
            JsonObject obj = slots.get(i).getAsJsonObject();
            JsonObject idx = obj.has("3ZH") ? obj.getAsJsonObject("3ZH") : null;
            if (idx == null || !idx.has(">Qh") || !idx.has("XJ>")) {
                continue;
            }
            if (idx.get(">Qh").getAsInt() == x && idx.get("XJ>").getAsInt() == y) {
                return obj;
            }
        }
        return null;
    }

    private boolean isPositiveInteger(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            return Integer.parseInt(value) > 0;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private void markInventoryDirty() {
        inventoryModified = true;
        statusLabel.setText("Pending changes — remember to Save!");
    }

    private record ItemSelection(ItemCatalog.ItemEntry entry, int amount) {}

    /** Gives visual feedback when an item is dropped, then restores its style. */
    private void flashDropHighlight(Node cell) {
        if (!(cell instanceof StackPane sp)) return;
        if (!sp.getStyleClass().contains(INVENTORY_HIGHLIGHT_CLASS)) {
            sp.getStyleClass().add(INVENTORY_HIGHLIGHT_CLASS);
        }
        PauseTransition flash = new PauseTransition(Duration.seconds(0.2));
        flash.setOnFinished(e -> sp.getStyleClass().remove(INVENTORY_HIGHLIGHT_CLASS));
        flash.play();
    }

    private void applyApplicationStyles(Scene scene) {
        if (scene == null) {
            return;
        }
        if (!scene.getStylesheets().contains(APPLICATION_STYLESHEET)) {
            scene.getStylesheets().add(APPLICATION_STYLESHEET);
        }
    }
}
