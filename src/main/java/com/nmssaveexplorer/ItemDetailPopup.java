package com.nmssaveexplorer;

import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

public class ItemDetailPopup extends Stage {

    private static final int WIDTH = 150;
    private static final int HEIGHT = 175;
    private static final int IMAGE_SIZE = 150;
    private static final int NAME_HEIGHT = 25;
    private static final double FONT_SIZE = 11.0;

    public ItemDetailPopup(Window owner, Image image, String name) {
        initOwner(owner);
        initStyle(StageStyle.UTILITY);
        initModality(Modality.NONE);
        setTitle("Item Detail");
        setResizable(false);

        ImageView imageView = new ImageView(image);
        imageView.setFitWidth(IMAGE_SIZE);
        imageView.setFitHeight(IMAGE_SIZE);
        imageView.setPreserveRatio(true);

        Label nameLabel = new Label(name);
        nameLabel.setPrefWidth(WIDTH);
        nameLabel.setMinHeight(NAME_HEIGHT);
        nameLabel.setAlignment(Pos.CENTER);
        nameLabel.setStyle("-fx-font-size: " + FONT_SIZE + "px;");
        nameLabel.setWrapText(true);

        VBox root = new VBox(imageView, nameLabel);
        root.setAlignment(Pos.TOP_CENTER);
        root.setPrefSize(WIDTH, HEIGHT);
        // Ensure the scene matches the requested size constraints
        root.setMaxSize(WIDTH, HEIGHT);
        root.setMinSize(WIDTH, HEIGHT);

        Scene scene = new Scene(root, WIDTH, HEIGHT);
        setScene(scene);

        // Close on click
        scene.setOnMouseClicked(e -> close());
        // Close on lost focus
        focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                close();
            }
        });

        // Center on owner
        if (owner != null) {
            setOnShown(e -> {
                double centerX = owner.getX() + owner.getWidth() / 2;
                double centerY = owner.getY() + owner.getHeight() / 2;
                setX(centerX - WIDTH / 2.0);
                setY(centerY - HEIGHT / 2.0);
            });
        }
    }

    public static void show(Window owner, Image image, String name) {
        new ItemDetailPopup(owner, image, name).show();
    }
}
