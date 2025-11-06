package com.nmssaveexplorer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.nmssaveexplorer.inventory.BaseInventoryController;
import com.nmssaveexplorer.inventory.ExosuitInventoryController;
import com.nmssaveexplorer.inventory.ExosuitTechController;
import com.nmssaveexplorer.inventory.MultitoolInventoryController;
import com.nmssaveexplorer.inventory.ShipInventoryController;
import com.nmssaveexplorer.registry.IconRegistry;
import com.nmssaveexplorer.registry.ItemNameRegistry;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
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
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
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
import javafx.stage.FileChooser;
import javafx.stage.Stage;
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
    private Stage lastInventoryStage;
    private static final Set<String> loggedMissingIcons = new HashSet<>();

    @FXML
    private void initialize() {
        setupTree();
        setupEditor();
        statusLabel.setText("Status: Ready.");
    }

    /**
     * ====================== INITIAL SETUP ======================
     **/

    private void setupTree() {
        TreeItem<String> root = new TreeItem<>("root");
        root.getChildren().add(new TreeItem<>("Open a save file to begin."));
        root.setExpanded(true);
        jsonTree.setRoot(root);

        jsonTree.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> {
            // 🔹 When switching nodes, try to commit the old edit first
            if (oldItem != null && currentEditedNode == oldItem) {
                String editedText = codeArea.getText().trim();
                if (!editedText.isEmpty()) {
                    try {
                        JsonElement newFragment = JsonParser.parseString(editedText);

                        // Compare with current JSON value; skip if identical
                        JsonElement currentValue = nodeToElementMap.get(oldItem);
                        String newJson = new Gson().toJson(newFragment);
                        String currentJson = (currentValue != null) ? new Gson().toJson(currentValue) : "";

                        if (!newJson.equals(currentJson)) {
                            updateJsonNode(oldItem, newFragment);
                            System.out.println("[AutoCommit] Updated node before switching: " + oldItem.getValue());
                        } else {
                            System.out.println("[AutoCommit] No change detected for: " + oldItem.getValue());
                        }
                    } catch (JsonSyntaxException ex) {
                        statusLabel.setText("❌ Invalid JSON, not applied: " + ex.getMessage());
                    }

                }
            }

            if (newItem != null) {
                showJsonForItem(newItem);
                currentEditedNode = newItem;
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
                    } else {
                        setText(item);
                        TreeItem<String> treeItem = getTreeItem();
                        if (modifiedNodes.contains(treeItem)) {
                            setStyle("-fx-font-weight: bold;");
                        } else {
                            setStyle("");
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
                getClass().getResource("/editor.css").toExternalForm()
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
        populateJsonTree(rootJsonElement, file.getName());
        modifiedNodes.clear();
        showTemporaryStatus("Loaded: " + file.getName());
    }

    public boolean hasLoadedSave() {
        return rootJsonElement != null && currentSaveFile != null;
    }

    private void ensureMappingLoaded() {
        if (mappingLoaded) {
            return;
        }

        try {
            var res = getClass().getResource("/mapping.json");
            if (res != null) {
                File mappingFile = new File(res.toURI());
                JsonMapper.loadMapping(mappingFile);
                mappingLoaded = JsonMapper.isLoaded();
                reverseMapping.clear();
                for (Map.Entry<String, String> entry : JsonMapper.getMapping().entrySet()) {
                    reverseMapping.put(entry.getValue(), entry.getKey());
                }
                System.out.println("[Mapping] Loaded " + reverseMapping.size() + " reverse entries.");
            } else {
                System.err.println("[Controller] mapping.json not found in resources.");
            }
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

    /**
     * ====================== JSON MAPPING HELPERS ======================
     **/

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

    private JsonObject remapJson(JsonObject readable, Map<String, String> reverse) {
        JsonObject out = new JsonObject();
        for (Map.Entry<String, JsonElement> e : readable.entrySet()) {
            String readableKey = e.getKey();
            String shortKey = reverse.getOrDefault(readableKey, readableKey);
            JsonElement val = e.getValue();

            // Keep unmapped keys and nested data intact
            if (val.isJsonObject()) {
                out.add(shortKey, remapJson(val.getAsJsonObject(), reverse));
            } else if (val.isJsonArray()) {
                JsonArray arr = new JsonArray();
                for (JsonElement x : val.getAsJsonArray()) {
                    if (x.isJsonObject()) {
                        arr.add(remapJson(x.getAsJsonObject(), reverse));
                    } else {
                        arr.add(x.deepCopy());
                    }
                }
                out.add(shortKey, arr);
            } else {
                out.add(shortKey, val.deepCopy());
            }
        }
        return out;
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
            statusLabel.setText("✅ Inventory changes saved.");
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

    /**
     * ====================== MENU ACTIONS ======================
     **/

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

            Map<String, InventoryPayload> inventories = new LinkedHashMap<>();

            List<BaseInventoryController> controllers = Arrays.asList(
                    new ExosuitInventoryController(),
                    new ExosuitTechController(),
                    new ShipInventoryController(),
                    new MultitoolInventoryController()
            );

            for (BaseInventoryController controller : controllers) {
                JsonArray slots = controller.resolveSlots(root);
                JsonArray valid = controller.resolveValidSlots(root);
                inventories.put(controller.inventoryName(), new InventoryPayload(slots, valid));
            }

            // Freighter inventory (no dedicated controller yet)
            JsonArray freighterSlots = null;
            JsonArray freighterValid = null;
            if (root.has("vLc")) {
                JsonObject base = root.getAsJsonObject("vLc");
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
            invStage.setTitle("Inventory Editor");
            TabPane tabs = new TabPane();

            inventories.forEach((name, payload) -> {
                JsonArray slots = payload.slots();
                JsonArray valid = payload.validSlots();

                ScrollPane scroll = new ScrollPane();
                scroll.setFitToWidth(true);
                scroll.setStyle("-fx-background: #1e1e1e;");

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

            if (tabs.getTabs().isEmpty()) {
                statusLabel.setText("Inventories not available for display.");
                return;
            }

            Button saveButton = new Button("Save Changes");
            Label infoLabel = new Label("Ready.");
            saveButton.setOnAction(ev -> {
                boolean success = saveChanges();
                if (success) {
                    infoLabel.setText("✅ Saved " + (currentSaveFile != null ? currentSaveFile.getName() : "changes") + ".");
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
            invStage.setScene(scene);
            invStage.show();
            lastInventoryStage = invStage;
            invStage.setOnHidden(e -> lastInventoryStage = null);

            statusLabel.setText("Inventory editor opened (" + tabs.getTabs().size() + " categories)");
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
        stage.setTitle(name + " Inventory");

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: #1e1e1e;");
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
        stage.setScene(scene);
        stage.show();
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
        grid.setStyle("-fx-background-color:#111;");

        final int CELL_SIZE = 100;
        final int GRID_WIDTH = 10;

        // ---------------------------------------------------------
        // 1️⃣ Determine grid height
        // ---------------------------------------------------------
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

        // ---------------------------------------------------------
        // 2️⃣ Lock row/column constraints
        // ---------------------------------------------------------
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

        // ---------------------------------------------------------
        // 3️⃣ Draw base grid
        // ---------------------------------------------------------
        for (int y = 0; y <= maxY; y++) {
            for (int x = 0; x < GRID_WIDTH; x++) {
                StackPane slot = new StackPane();
                slot.setPrefSize(CELL_SIZE, CELL_SIZE);
                slot.setMinSize(CELL_SIZE, CELL_SIZE);
                slot.setMaxSize(CELL_SIZE, CELL_SIZE);
                slot.setStyle("-fx-border-color:#444; -fx-border-width:1; -fx-background-color:#1b1b1b;");
                grid.add(slot, x, y);
            }
        }

        // ---------------------------------------------------------
        // 4️⃣ Add items
        // ---------------------------------------------------------
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
            cell.setStyle("-fx-background-color:#2e2e2e; -fx-border-color:#666; -fx-border-width:1;");

            //JsonObject typeObj = item.has("Vn8") ? item.getAsJsonObject("Vn8") : null;
            //String type = (typeObj != null && typeObj.has("elv")) ? typeObj.get("elv").getAsString() : "Unknown";
            String id = item.has("b2n") ? item.get("b2n").getAsString() : "Unknown";
            int amount = item.has("1o9") ? item.get("1o9").getAsInt() : 1;
            int max = item.has("F9q") ? item.get("F9q").getAsInt() : 0;

            String displayName = ItemNameRegistry.getDisplayName(id);
            Text nameText = new Text(displayName != null ? displayName : id);
            nameText.setStyle("-fx-fill:#fff; -fx-font-weight:bold; -fx-font-size:11;");
            Text idText = null;
            // if (displayName != null && !displayName.equals(id)) {
            //     idText = new Text(id);
            //     idText.setStyle("-fx-fill:#888; -fx-font-size:9;");
            // }
            Text amountText = new Text(amount + (max > 0 ? "/" + max : ""));
            amountText.setStyle("-fx-fill:#ccc; -fx-font-size:10;");
            //Text typeText = new Text(type);
            //typeText.setStyle("-fx-fill:#aaa; -fx-font-size:9;");

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
                imageView.setFitWidth(48);
                imageView.setFitHeight(48);
                imageView.setPreserveRatio(true);
                content.getChildren().add(imageView);
            } else if (loggedMissingIcons.add(id)) {
                System.err.println("[InventoryIcon] Missing icon for " + id);
            }
            content.getChildren().add(nameText);
            if (idText != null) {
                content.getChildren().add(idText);
            }
            //content.getChildren().addAll(amountText, typeText);
            content.getChildren().addAll(amountText);
            cell.setCenter(content);

            Tooltip tooltip = new Tooltip(item.toString());
            tooltip.setStyle("-fx-font-size:11; -fx-background-color:#333; -fx-text-fill:white;");
            Tooltip.install(cell, tooltip);

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
            } else {
                StackPane sp = new StackPane(cell);
                sp.setPrefSize(CELL_SIZE, CELL_SIZE);
                sp.setStyle("-fx-border-color:#444; -fx-border-width:1;");
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

    /** Refresh grid visuals in place (no rebuild, no resizing bugs). */
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
                sp.setStyle("-fx-border-color:#444; -fx-border-width:1; -fx-background-color:#1b1b1b;");
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
            cell.setStyle("-fx-background-color:#2e2e2e; -fx-border-color:#666; -fx-border-width:1;");

            //JsonObject typeObj = item.has("Vn8") ? item.getAsJsonObject("Vn8") : null;
            //String type = (typeObj != null && typeObj.has("elv")) ? typeObj.get("elv").getAsString() : "Unknown";
            String id = item.has("b2n") ? item.get("b2n").getAsString() : "Unknown";
            int amount = item.has("1o9") ? item.get("1o9").getAsInt() : 1;
            int max = item.has("F9q") ? item.get("F9q").getAsInt() : 0;

            String displayName = ItemNameRegistry.getDisplayName(id);
            Text nameText = new Text(displayName != null ? displayName : id);
            nameText.setStyle("-fx-fill:#fff; -fx-font-weight:bold; -fx-font-size:11;");
            Text idText = null;
            // if (displayName != null && !displayName.equals(id)) {
            //     idText = new Text(id);
            //     idText.setStyle("-fx-fill:#888; -fx-font-size:9;");
            // }
            Text amountText = new Text(amount + (max > 0 ? "/" + max : ""));
            amountText.setStyle("-fx-fill:#ccc; -fx-font-size:10;");
            //Text typeText = new Text(type);
            //typeText.setStyle("-fx-fill:#aaa; -fx-font-size:9;");

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
                imageView.setFitWidth(48);
                imageView.setFitHeight(48);
                imageView.setPreserveRatio(true);
                box.getChildren().add(imageView);
            } else if (loggedMissingIcons.add(id)) {
                System.err.println("[InventoryIcon] Missing icon for " + id);
            }
            box.getChildren().add(nameText);
            if (idText != null) {
                box.getChildren().add(idText);
            }
            //box.getChildren().addAll(amountText, typeText);
            box.getChildren().addAll(amountText);
            cell.setCenter(box);

            Tooltip.install(cell, new Tooltip(item.toString()));

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

    /** Gives visual feedback when an item is dropped, then restores its style. */
    private void flashDropHighlight(Node cell) {
        if (!(cell instanceof StackPane sp)) return;
        sp.setStyle("-fx-border-color:yellow; -fx-border-width:2; -fx-background-color:#2e2e2e;");
        PauseTransition flash = new PauseTransition(Duration.seconds(0.2));
        flash.setOnFinished(e -> {
            sp.setStyle("-fx-border-color:#444; -fx-border-width:1; -fx-background-color:#1b1b1b;");
        });
        flash.play();
    }
}
