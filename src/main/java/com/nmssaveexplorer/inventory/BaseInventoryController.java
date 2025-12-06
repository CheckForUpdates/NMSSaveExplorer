package com.nmssaveexplorer.inventory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.nmssaveexplorer.registry.IconRegistry;
import com.nmssaveexplorer.registry.ItemDefinitionRegistry;
import javafx.application.Platform;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;

public abstract class BaseInventoryController {

    protected static final int CELL_SIZE = 100;
    protected static final int ICON_SIZE = 72;
    protected static final int GRID_WIDTH = 10;
    private static final String GRID_CLASS = "inventory-grid";
    private static final String PLACEHOLDER_CLASS = "inventory-slot-placeholder";
    private static final String PANE_CLASS = "inventory-slot-pane";
    private static final String NAME_CLASS = "inventory-slot-name";
    private static final String AMOUNT_CLASS = "inventory-slot-amount";

    private static final Set<String> LOGGED_MISSING_ICONS = ConcurrentHashMap.newKeySet();

    protected abstract String getInventoryName();
    protected abstract JsonArray getSlotsArray(JsonObject root);
    protected abstract JsonArray getValidSlotsArray(JsonObject root);

    public final String inventoryName() {
        return getInventoryName();
    }

    public final JsonArray resolveSlots(JsonObject root) {
        JsonArray slots = getSlotsArray(root);
        return slots != null ? slots : new JsonArray();
    }

    public final JsonArray resolveValidSlots(JsonObject root) {
        JsonArray valid = getValidSlotsArray(root);
        return valid != null ? valid : new JsonArray();
    }

    public GridPane buildInventoryGrid(JsonObject root) {
        JsonArray slotsArray = getSlotsArray(root);
        JsonArray validSlotIndices = getValidSlotsArray(root);
        GridPane grid = new GridPane();
        grid.setPadding(new Insets(10));
        grid.setHgap(1);
        grid.setVgap(1);
        grid.setAlignment(Pos.TOP_LEFT);
        grid.getStyleClass().add(GRID_CLASS);

        int maxY = 0;
        for (JsonElement e : validSlotIndices) {
            if (e.isJsonObject()) {
                int y = e.getAsJsonObject().has("XJ>") ? e.getAsJsonObject().get("XJ>").getAsInt() : 0;
                maxY = Math.max(maxY, y);
            }
        }
        for (JsonElement e : slotsArray) {
            if (e.isJsonObject()) {
                JsonObject idx = e.getAsJsonObject().getAsJsonObject("3ZH");
                if (idx != null && idx.has("XJ>"))
                    maxY = Math.max(maxY, idx.get("XJ>").getAsInt());
            }
        }

        grid.getColumnConstraints().clear();
        grid.getRowConstraints().clear();
        for (int i = 0; i < GRID_WIDTH; i++) {
            ColumnConstraints cc = new ColumnConstraints(CELL_SIZE, CELL_SIZE, CELL_SIZE);
            cc.setHalignment(HPos.CENTER);
            grid.getColumnConstraints().add(cc);
        }
        for (int i = 0; i <= maxY; i++) {
            RowConstraints rc = new RowConstraints(CELL_SIZE, CELL_SIZE, CELL_SIZE);
            rc.setValignment(VPos.CENTER);
            grid.getRowConstraints().add(rc);
        }

        for (int y = 0; y <= maxY; y++) {
            for (int x = 0; x < GRID_WIDTH; x++) {
                StackPane cell = new StackPane();
                cell.setPrefSize(CELL_SIZE, CELL_SIZE);
                cell.getStyleClass().add(PLACEHOLDER_CLASS);
                grid.add(cell, x, y);
            }
        }

        for (int i = 0; i < slotsArray.size(); i++) {
            JsonObject item = slotsArray.get(i).getAsJsonObject();
            JsonObject idx = item.has("3ZH") ? item.getAsJsonObject("3ZH") : null;
            int x = idx != null && idx.has(">Qh") ? idx.get(">Qh").getAsInt() : i % GRID_WIDTH;
            int y = idx != null && idx.has("XJ>") ? idx.get("XJ>").getAsInt() : i / GRID_WIDTH;

            BorderPane pane = new BorderPane();
            pane.setPrefSize(CELL_SIZE, CELL_SIZE);
            pane.getStyleClass().add(PANE_CLASS);

            String id = item.has("b2n") ? item.get("b2n").getAsString() : "Unknown";
            int amount = item.has("1o9") ? item.get("1o9").getAsInt() : 1;
            int max = item.has("F9q") ? item.get("F9q").getAsInt() : 0;

            String displayName = ItemDefinitionRegistry.getDisplayName(id);
            if (displayName == null || displayName.isBlank()) {
                displayName = id;
            }
            Text nameText = new Text(displayName);
            nameText.setTextAlignment(TextAlignment.CENTER);
            nameText.getStyleClass().add(NAME_CLASS);
            nameText.setVisible(false);

            Text amountText = new Text(amount + (max > 0 ? "/" + max : ""));
            amountText.getStyleClass().add(AMOUNT_CLASS);

            VBox box = new VBox();
            box.setAlignment(Pos.CENTER);
            box.setSpacing(4);

            Image icon = IconRegistry.getIcon(id);
            if (icon == null && id.startsWith("^") && id.length() > 1) {
                icon = IconRegistry.getIcon(id.substring(1));
            }
            if (icon == null) {
                if (LOGGED_MISSING_ICONS.add(id)) {
                    System.err.println("[InventoryIcon] Missing icon for " + id);
                }
            }
            if (icon != null) {
                if (LOGGED_MISSING_ICONS.add(id + "#logged")) {
                    System.out.println("[InventoryIcon] Loaded icon for " + id + " size=" + icon.getWidth() + "x" + icon.getHeight());
                }
                ImageView imageView = new ImageView(icon);
                imageView.setFitWidth(ICON_SIZE);
                imageView.setFitHeight(ICON_SIZE);
                imageView.setPreserveRatio(true);
                box.getChildren().add(imageView);
            }
            box.getChildren().add(amountText);

            StackPane nameLayer = new StackPane(nameText);
            nameLayer.setMouseTransparent(true);
            nameLayer.setPickOnBounds(false);
            nameLayer.setVisible(false);
            StackPane.setAlignment(nameText, Pos.TOP_CENTER);
            StackPane.setAlignment(nameLayer, Pos.TOP_CENTER);

            StackPane layeredContent = new StackPane(box, nameLayer);
            pane.setCenter(layeredContent);

            pane.hoverProperty().addListener((obs, wasHovering, isHovering) -> {
                nameLayer.setVisible(isHovering);
                nameText.setVisible(isHovering);
            });

            final int startX = x;
            final int startY = y;
            pane.setOnDragDetected(e -> {
                var db = pane.startDragAndDrop(TransferMode.MOVE);
                var cc = new javafx.scene.input.ClipboardContent();
                cc.putString(startX + "," + startY);
                db.setContent(cc);
                e.consume();
            });
            pane.setOnDragDone(e -> {});

            Node existing = getNodeFromGrid(grid, x, y);
            if (existing instanceof StackPane sp)
                sp.getChildren().setAll(pane);
        }

        for (Node n : grid.getChildren()) {
            n.setOnDragOver(evt -> {
                if (evt.getGestureSource() != n && evt.getDragboard().hasString())
                    evt.acceptTransferModes(TransferMode.MOVE);
                evt.consume();
            });
            n.setOnDragDropped(evt -> {
                var db = evt.getDragboard();
                if (!db.hasString()) return;
                String[] p = db.getString().split(",");
                int sx = Integer.parseInt(p[0]);
                int sy = Integer.parseInt(p[1]);
                Integer dx = GridPane.getColumnIndex(n);
                Integer dy = GridPane.getRowIndex(n);
                if (dx == null || dy == null) return;
                moveOrSwap(slotsArray, sx, sy, dx, dy);
                refreshGrid(grid, root);
                evt.setDropCompleted(true);
            });
        }

        return grid;
    }

    protected void moveOrSwap(JsonArray slots, int sx, int sy, int dx, int dy) {
        JsonObject src = null, dst = null;
        int si = -1, di = -1;
        for (int i = 0; i < slots.size(); i++) {
            JsonObject o = slots.get(i).getAsJsonObject();
            if (!o.has("3ZH")) continue;
            JsonObject idx = o.getAsJsonObject("3ZH");
            int x = idx.get(">Qh").getAsInt();
            int y = idx.get("XJ>").getAsInt();
            if (x == sx && y == sy) { src = o; si = i; }
            if (x == dx && y == dy) { dst = o; di = i; }
        }
        if (src == null) return;
        if (dst != null) {
            src.getAsJsonObject("3ZH").addProperty(">Qh", dx);
            src.getAsJsonObject("3ZH").addProperty("XJ>", dy);
            dst.getAsJsonObject("3ZH").addProperty(">Qh", sx);
            dst.getAsJsonObject("3ZH").addProperty("XJ>", sy);
            slots.set(si, dst);
            slots.set(di, src);
        } else {
            src.getAsJsonObject("3ZH").addProperty(">Qh", dx);
            src.getAsJsonObject("3ZH").addProperty("XJ>", dy);
        }
    }

    protected void refreshGrid(GridPane grid, JsonObject root) {
        Platform.runLater(() -> {
            grid.getChildren().clear();
            grid.getChildren().addAll(buildInventoryGrid(root).getChildren());
            grid.requestLayout();
        });
    }

    protected Node getNodeFromGrid(GridPane grid, int col, int row) {
        for (Node node : grid.getChildren()) {
            Integer c = GridPane.getColumnIndex(node);
            Integer r = GridPane.getRowIndex(node);
            if (c != null && r != null && c == col && r == row)
                return node;
        }
        return null;
    }

}
