package com.nmssaveexplorer;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Duration;

public class MainMenuController {

    @FXML
    private Label headingLabel;
    @FXML
    private ComboBox<SaveGameLocator.SaveFile> saveCombo;
    @FXML
    private Button openJsonButton;
    @FXML
    private Button openInventoryButton;
    @FXML
    private Button refreshButton;
    @FXML
    private Button browseButton;
    @FXML
    private Button saveChangesButton;
    @FXML
    private Label statusLabel;
    @FXML
    private StackPane loadingOverlay;
    @FXML
    private Label loadingMessageLabel;

    private final ObservableList<SaveGameLocator.SaveFile> saves = FXCollections.observableArrayList();
    private SaveExplorerController activeController;
    private SaveGameLocator.SaveFile activeSaveFile;

    @FXML
    private void initialize() {
        saveCombo.setItems(saves);
        saveCombo.setButtonCell(new SaveFileListCell());
        saveCombo.setCellFactory(listView -> new SaveFileListCell());
        refreshSaves();

        saveCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> updateSelectionState());
        updateSelectionState();
        hideLoadingOverlay();
    }

    @FXML
    private void onRefreshSaves() {
        refreshSaves();
    }

    @FXML
    private void onBrowseForSave() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select No Man's Sky Save (.hg)");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("No Man's Sky Save Files", "*.hg"));
        File chosen = chooser.showOpenDialog(null);
        if (chosen == null) {
            setStatus("No file selected.");
            return;
        }

        SaveGameLocator.SaveFile saveFile = new SaveGameLocator.SaveFile(chosen.toPath(), chosen.toPath().getParent());
        if (saves.stream().noneMatch(existing -> existing.path().equals(saveFile.path()))) {
            saves.add(0, saveFile);
        }
        saveCombo.getSelectionModel().select(saveFile);
        setStatus("Selected: " + saveFile.displayName());
    }

    @FXML
    private void onOpenJsonEditor() {
        SaveGameLocator.SaveFile saveFile = saveCombo.getSelectionModel().getSelectedItem();
        if (saveFile == null) {
            setStatus("Choose a save file first.");
            return;
        }
        runWithLoadingOverlay("Opening JSON explorer...", "Failed to open JSON explorer: ", () -> openJsonEditor(saveFile));
    }

    @FXML
    private void onOpenExosuitInventory() {
        SaveGameLocator.SaveFile saveFile = saveCombo.getSelectionModel().getSelectedItem();
        if (saveFile == null) {
            setStatus("Choose a save file first.");
            return;
        }
        runWithLoadingOverlay("Loading inventories...", "Failed to open Exosuit inventory: ", () -> openExosuitInventory(saveFile));
    }

    @FXML
    private void onSaveChanges() {
        if (activeController == null || !activeController.hasLoadedSave()) {
            setStatus("No active editor to save.");
            return;
        }

        try {
            boolean success = activeController.saveChanges();
            if (success) {
                String name = (activeSaveFile != null) ? activeSaveFile.displayName() : "current save";
                setStatus("Saved changes to " + name + ".");
            } else {
                setStatus("Save failed. Check editor status.");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            setStatus("Save failed: " + ex.getMessage());
        }
    }

    private void refreshSaves() {
        List<SaveGameLocator.SaveFile> discovered = SaveGameLocator.discoverSaves();
        saves.setAll(discovered);
        if (!saves.isEmpty()) {
            saveCombo.getSelectionModel().selectFirst();
            headingLabel.setText("Detected " + saves.size() + " save file(s).");
            setStatus("Found " + saves.size() + " save file(s).");
        } else {
            headingLabel.setText("No save files found automatically.");
            setStatus("No save files detected. Use Browse to select one manually.");
        }
        updateSelectionState();
    }

    private void updateSelectionState() {
        boolean hasSelection = saveCombo.getSelectionModel().getSelectedItem() != null;
        openJsonButton.setDisable(!hasSelection);
        openInventoryButton.setDisable(!hasSelection);
        boolean canSave = activeController != null && activeController.hasLoadedSave();
        saveChangesButton.setDisable(!canSave);
    }

    private void setStatus(String text) {
        statusLabel.setText(text);
    }

    private void openJsonEditor(SaveGameLocator.SaveFile saveFile) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/SaveExplorer.fxml"));
        Parent root = loader.load();
        SaveExplorerController controller = loader.getController();
        controller.loadSaveFile(saveFile.path().toFile());

        Stage stage = new Stage();
        Stage owner = getOwningStage();
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.setTitle("Save Explorer - " + saveFile.displayName());
        stage.setScene(new Scene(root, 1000, 700));
        registerActiveController(controller, saveFile, stage);
        stage.show();

        setStatus("Opened JSON explorer.");
    }

    private void openExosuitInventory(SaveGameLocator.SaveFile saveFile) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/SaveExplorer.fxml"));
        loader.load();
        SaveExplorerController controller = loader.getController();
        controller.loadSaveFile(saveFile.path().toFile());
        controller.showExosuitInventory(getOwningStage());
        Stage inventoryStage = controller.getLastInventoryStage();
        registerActiveController(controller, saveFile, inventoryStage);

        setStatus("Opened Exosuit inventory for " + saveFile.displayName());
    }

    private void runWithLoadingOverlay(String message, String failurePrefix, OverlayTask task) {
        showLoadingOverlay(message);
        PauseTransition delay = new PauseTransition(Duration.millis(75));
        delay.setOnFinished(event -> {
            try {
                task.run();
            } catch (Exception ex) {
                ex.printStackTrace();
                setStatus(failurePrefix + ex.getMessage());
            } finally {
                hideLoadingOverlay();
            }
        });
        delay.play();
    }

    private void showLoadingOverlay(String message) {
        if (loadingOverlay == null) {
            return;
        }
        if (loadingMessageLabel != null) {
            loadingMessageLabel.setText(message);
        }
        loadingOverlay.setManaged(true);
        loadingOverlay.setVisible(true);
        loadingOverlay.setMouseTransparent(false);
    }

    private void hideLoadingOverlay() {
        if (loadingOverlay == null) {
            return;
        }
        loadingOverlay.setVisible(false);
        loadingOverlay.setManaged(false);
        loadingOverlay.setMouseTransparent(true);
    }

    private void registerActiveController(SaveExplorerController controller,
                                          SaveGameLocator.SaveFile saveFile,
                                          Stage stage) {
        this.activeController = controller;
        this.activeSaveFile = saveFile;
        if (stage != null) {
            stage.addEventHandler(WindowEvent.WINDOW_HIDDEN, event -> clearActiveController(controller));
        }
        Stage inventoryStage = controller.getLastInventoryStage();
        if (inventoryStage != null && inventoryStage != stage) {
            inventoryStage.addEventHandler(WindowEvent.WINDOW_HIDDEN, event -> clearActiveController(controller));
        }
        updateSelectionState();
    }

    private void clearActiveController(SaveExplorerController controller) {
        if (this.activeController == controller) {
            this.activeController = null;
            this.activeSaveFile = null;
            updateSelectionState();
            setStatus("Editor closed.");
        }
    }

    private Stage getOwningStage() {
        if (statusLabel != null && statusLabel.getScene() != null) {
            if (statusLabel.getScene().getWindow() instanceof Stage stage) {
                return stage;
            }
        }
        return null;
    }

    private static final class SaveFileListCell extends ListCell<SaveGameLocator.SaveFile> {
        @Override
        protected void updateItem(SaveGameLocator.SaveFile item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
            } else {
                Path path = item.path();
                String text = item.displayName();
                if (item.root() != null) {
                    text += "  (" + item.rootDisplay() + ")";
                } else if (path != null) {
                    text += "  (" + path.toAbsolutePath() + ")";
                }
                setText(text);
            }
        }
    }

    @FunctionalInterface
    private interface OverlayTask {
        void run() throws Exception;
    }
}
