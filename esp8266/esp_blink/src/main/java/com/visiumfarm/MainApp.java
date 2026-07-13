package com.visiumfarm;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) {
        UIBuilder uiBuilder = new UIBuilder();

        Scene scene = new Scene(uiBuilder.build(), 1100, 780);

        java.net.URL cssUrl = getClass().getResource("/style.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        } else {
            System.err.println("Warning: style.css stylesheet not found in classpath resources.");
        }

        stage.setTitle("VISIUM FARM - ROBOTİK NFC TEST KONTROL PANELİ");
        stage.setScene(scene);
        stage.setMinWidth(1120);
        stage.setMinHeight(820);
        stage.setOnCloseRequest(event -> uiBuilder.cleanup());
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}