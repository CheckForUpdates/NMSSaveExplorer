package com.nmssaveexplorer;

import com.nmssaveexplorer.registry.IconRegistry;
import com.nmssaveexplorer.registry.ItemDefinitionRegistry;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.Callback;

import java.util.Comparator;
import java.util.Map;

public class MaterialLookupDialog extends Dialog<Void> {

    private final TextField searchField;
    private final ListView<Map.Entry<String, ItemDefinitionRegistry.ItemDefinition>> listView;
    private final ObservableList<Map.Entry<String, ItemDefinitionRegistry.ItemDefinition>> masterData;

    public MaterialLookupDialog(Window owner) {
        initOwner(owner);
        setTitle("Material Lookup");
        setHeaderText("Search for materials and components.");
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        setResizable(true);

        searchField = new TextField();
        searchField.setPromptText("Search by name...");
        
        listView = new ListView<>();
        masterData = FXCollections.observableArrayList(ItemDefinitionRegistry.getAllDefinitions().entrySet());
        
        // Sort alphabetically by name
        masterData.sort(Comparator.comparing(e -> {
            String n = e.getValue().name();
            return n != null ? n : "";
        }, String::compareToIgnoreCase));

        FilteredList<Map.Entry<String, ItemDefinitionRegistry.ItemDefinition>> filteredList = 
                new FilteredList<>(masterData, p -> true);
        listView.setItems(filteredList);

        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            String filter = newVal == null ? "" : newVal.toLowerCase();
            filteredList.setPredicate(entry -> {
                if (filter.isEmpty()) return true;
                String name = entry.getValue().name();
                return name != null && name.toLowerCase().contains(filter);
            });
        });

        listView.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(Map.Entry<String, ItemDefinitionRegistry.ItemDefinition> item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getValue().name());
                }
            }
        });

        listView.setOnMouseClicked(event -> {
            var selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                showDetail(selected.getKey(), selected.getValue());
            }
        });

        VBox content = new VBox(10, searchField, listView);
        content.setPadding(new Insets(10));
        VBox.setVgrow(listView, Priority.ALWAYS);
        content.setPrefSize(400, 500);

        getDialogPane().setContent(content);
        
        // Request focus on search field
        setOnShown(e -> Platform.runLater(searchField::requestFocus));
    }

    private void showDetail(String itemId, ItemDefinitionRegistry.ItemDefinition def) {
        Image icon = IconRegistry.getIcon(itemId);
        String name = def.name();
        
        // Use a placeholder if icon is null? 
        // Existing IcoRegistry logs errors if missing.
        // We will pass whatever we have.
        
        ItemDetailPopup.show(getOwner(), icon, name);
    }
}
