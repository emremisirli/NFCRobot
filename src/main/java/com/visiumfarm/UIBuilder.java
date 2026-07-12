package com.visiumfarm;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;
import java.time.format.DateTimeFormatter;

public class UIBuilder {

    private SerialManager serialManager;
    private LogManager logManager;
    private CommandManager commandManager;

    private Button ileriButton;
    private Button solButton;
    private Button sagButton;
    private Button geriButton;
    private Button homeButton;

    private ToggleGroup stepGroup;

    private TextArea logArea;

    private Label statusLabel;
    private Label deviceLabel;
    private Circle connectionLed;

    private EspClientManager espClientManager;
    private TextField espIpField;
    private TextField espPortField;
    private Button espConnectButton;
    private Button espDisconnectButton;
    private Button ledOnButton;
    private Button ledOffButton;
    private Label espStatusLabel;
    private Label espLastCheckLabel;
    private Circle espConnectionLed;
    private java.time.LocalDateTime lastSuccessfulCheckTime = null;
    private Timeline lastCheckTimeline;

    // Manuel kontrol paneli bileşenleri (WiFi bağlantısına bağlı)
    private VBox manualControlPanelRef;
    private Label wifiWarningLabel;
    private GridPane dpadRef;
    private HBox radioRowRef;

    public BorderPane build() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");

        // Initialize Managers
        serialManager = new SerialManager();

        VBox mainContainer = new VBox(15);
        mainContainer.setPadding(new Insets(15));

        VBox manualControlPanel = createManualControlPanel();
        manualControlPanelRef = manualControlPanel;
        VBox espControlPanel = createEspControlPanel();

        manualControlPanel.setPrefWidth(520);
        espControlPanel.setPrefWidth(520);

        HBox.setHgrow(manualControlPanel, Priority.ALWAYS);
        HBox.setHgrow(espControlPanel, Priority.ALWAYS);

        HBox topRow = new HBox(15);
        topRow.getChildren().addAll(
                manualControlPanel,
                espControlPanel
        );

        mainContainer.getChildren().addAll(topRow, createLogPanel());

        // Header, Footer, and Center
        root.setTop(createHeaderPanel());
        root.setCenter(mainContainer);
        root.setBottom(createStatusBar());

        // Initialize logManager and commandManager once logArea is created
        logManager = new LogManager(logArea);
        commandManager = new CommandManager(serialManager, logManager);
        espClientManager = new EspClientManager(logManager);

        // Initialize timeline for updating last check elapsed time
        lastCheckTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            refreshLastCheckLabel();
        }));
        lastCheckTimeline.setCycleCount(Timeline.INDEFINITE);
        lastCheckTimeline.play();

        // Bind events
        bindEvents();
        bindEspEvents();
        updateEspControlStates(false, false, "Bağlantı Yok");
        updateManualControlStates(false);

        // Setup Keyboard Shortcuts when scene is loaded
        root.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.setOnKeyPressed(event -> {
                    switch (event.getCode()) {
                        case UP:
                            ileriButton.fire();
                            event.consume();
                            break;
                        case DOWN:
                            geriButton.fire();
                            event.consume();
                            break;
                        case LEFT:
                            solButton.fire();
                            event.consume();
                            break;
                        case RIGHT:
                            sagButton.fire();
                            event.consume();
                            break;
                        case H:
                            homeButton.fire();
                            event.consume();
                            break;
                        default:
                            break;
                    }
                });
            }
        });

        return root;
    }

    private HBox createHeaderPanel() {
        HBox header = new HBox(15);
        header.setPadding(new Insets(15, 20, 15, 20));
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-background-color: #020617; -fx-border-color: transparent transparent #1e293b transparent; -fx-border-width: 1px;");

        Label titleLabel = new Label("VISIUM FARM");
        titleLabel.setStyle("-fx-text-fill: #0284c7; -fx-font-size: 22px; -fx-font-weight: bold;");

        Label subTitleLabel = new Label("•  Robotik NFC Test Kontrol Paneli");
        subTitleLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 14px; -fx-font-weight: 500;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        connectionLed = new Circle(7);
        connectionLed.setFill(Color.web("#ef4444"));
        connectionLed.getStyleClass().add("led-offline");

        Label ledLabel = new Label("Sistem Bağlantısı");
        ledLabel.setStyle("-fx-text-fill: #cbd5e1; -fx-font-size: 13px;");

        HBox ledBox = new HBox(8, connectionLed, ledLabel);
        ledBox.setAlignment(Pos.CENTER);
        ledBox.setPadding(new Insets(4, 10, 4, 10));
        ledBox.setStyle("-fx-background-color: #1e293b; -fx-background-radius: 20px; -fx-border-color: #334155; -fx-border-radius: 20px;");

        header.getChildren().addAll(titleLabel, subTitleLabel, spacer, ledBox);
        return header;
    }

    private VBox createManualControlPanel() {
        VBox frame = createCard();
        frame.setPrefWidth(520);

        Label title = createTitle("MANUEL MOTOR KONTROLÜ (DPAD)");

        // WiFi bağlantısı uyarı etiketi
        wifiWarningLabel = new Label("⚠ WiFi bağlantısı gerekli");
        wifiWarningLabel.setStyle("-fx-text-fill: #f59e0b; -fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 4 10 4 10; -fx-background-color: rgba(245,158,11,0.1); -fx-background-radius: 6; -fx-border-color: #f59e0b; -fx-border-radius: 6; -fx-border-width: 1;");
        wifiWarningLabel.setAlignment(Pos.CENTER);
        wifiWarningLabel.setMaxWidth(Double.MAX_VALUE);

        GridPane dpad = new GridPane();
        dpad.setHgap(10);
        dpad.setVgap(10);
        dpad.setAlignment(Pos.CENTER);
        dpadRef = dpad;

        ileriButton = createDpadButton("▲ İLERİ", Theme.DARK_BUTTON);
        solButton = createDpadButton("◀ SOL", Theme.DARK_BUTTON);
        sagButton = createDpadButton("SAĞ ▶", Theme.DARK_BUTTON);
        geriButton = createDpadButton("▼ GERİ", Theme.DARK_BUTTON);
        homeButton = createDpadButton("⌂ HOME", Theme.ORANGE);

        dpad.add(ileriButton, 1, 0);
        dpad.add(solButton, 0, 1);
        dpad.add(homeButton, 1, 1);
        dpad.add(sagButton, 2, 1);
        dpad.add(geriButton, 1, 2);

        Label stepLabel = createSmallLabel("Adım Hassasiyeti");

        HBox radioRow = new HBox(15);
        radioRow.setAlignment(Pos.CENTER);
        radioRowRef = radioRow;

        stepGroup = new ToggleGroup();

        String[] steps = {"1mm", "5mm", "10mm", "50mm"};

        for (String step : steps) {
            RadioButton rb = new RadioButton(step);
            rb.setToggleGroup(stepGroup);
            rb.getStyleClass().add("radio-button");
            if (step.equals("5mm")) rb.setSelected(true);
            radioRow.getChildren().add(rb);
        }

        frame.getChildren().addAll(title, wifiWarningLabel, dpad, stepLabel, radioRow);

        return frame;
    }



    private VBox createLogPanel() {
        VBox frame = createCard();

        Label title = createTitle("SİSTEM LOGLARI / KONSOL");

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(180);

        Button clearLogButton = createButton("Temizle", Theme.DARK_BUTTON);
        clearLogButton.setPrefWidth(100);
        clearLogButton.setPrefHeight(30);

        Button saveLogButton = createButton("Logları Kaydet", Theme.DARK_BUTTON);
        saveLogButton.setPrefWidth(120);
        saveLogButton.setPrefHeight(30);

        HBox logActionRow = new HBox(10, clearLogButton, saveLogButton);
        logActionRow.setAlignment(Pos.CENTER_RIGHT);

        clearLogButton.setOnAction(e -> logManager.temizle());
        saveLogButton.setOnAction(e -> {
            try {
                java.io.File logFile = new java.io.File("robotik_nfc_log.txt");
                java.nio.file.Files.writeString(logFile.toPath(), logArea.getText());
                logManager.logEkle("Loglar başarıyla kaydedildi: " + logFile.getAbsolutePath());
            } catch (Exception ex) {
                logManager.logEkle("Hata: Loglar kaydedilemedi: " + ex.getMessage());
            }
        });

        frame.getChildren().addAll(title, logArea, logActionRow);

        return frame;
    }

    private HBox createStatusBar() {
        HBox statusBar = new HBox();
        statusBar.setPadding(new Insets(8, 15, 8, 15));
        statusBar.getStyleClass().add("status-bar");
        statusBar.setAlignment(Pos.CENTER);

        statusLabel = new Label("Durum: Bağlantı Yok");
        statusLabel.setTextFill(Color.web("#cbd5e1"));
        statusLabel.setStyle("-fx-font-size: 13px;");

        deviceLabel = new Label("Cihaz: Robotik NFC Panel");
        deviceLabel.setTextFill(Color.web("#cbd5e1"));
        deviceLabel.setStyle("-fx-font-size: 13px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        statusBar.getChildren().addAll(statusLabel, spacer, deviceLabel);

        return statusBar;
    }

    private void bindEvents() {
        // DPAD click events
        ileriButton.setOnAction(e -> commandManager.sendMoveCommand("ILERI", getSelectedStepSize()));
        geriButton.setOnAction(e -> commandManager.sendMoveCommand("GERI", getSelectedStepSize()));
        solButton.setOnAction(e -> commandManager.sendMoveCommand("SOL", getSelectedStepSize()));
        sagButton.setOnAction(e -> commandManager.sendMoveCommand("SAG", getSelectedStepSize()));
        homeButton.setOnAction(e -> commandManager.sendHomeCommand());
    }

    private int getSelectedStepSize() {
        if (stepGroup.getSelectedToggle() != null) {
            RadioButton rb = (RadioButton) stepGroup.getSelectedToggle();
            try {
                return Integer.parseInt(rb.getText().replace("mm", ""));
            } catch (NumberFormatException e) {
                return 5;
            }
        }
        return 5;
    }


    private VBox createCard() {
        VBox frame = new VBox(15);
        frame.setPadding(new Insets(20));
        frame.setAlignment(Pos.CENTER);
        frame.getStyleClass().add("card");
        return frame;
    }

    private Label createTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("title");
        return label;
    }

    private Label createSmallLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("label-small");
        return label;
    }

    private Button createButton(String text, String colorClass) {
        Button button = new Button(text);
        button.getStyleClass().addAll("button", colorClass);
        button.setPrefWidth(170);
        button.setPrefHeight(42);
        return button;
    }

    private Button createDpadButton(String text, String colorClass) {
        Button button = new Button(text);
        button.getStyleClass().addAll("button", colorClass);
        button.setPrefWidth(90);
        button.setPrefHeight(40);
        return button;
    }

    private VBox createEspControlPanel() {
        VBox frame = createCard();

        Label title = createTitle("ESP8266 WIFI LED KONTROLÜ");

        // IP and Port inputs
        espIpField = new TextField("192.168.2.100");
        espIpField.setPromptText("IP Adresi");
        HBox.setHgrow(espIpField, Priority.ALWAYS);
        espIpField.setMaxWidth(Double.MAX_VALUE);

        espPortField = new TextField("5000");
        espPortField.setPromptText("Port");
        espPortField.setPrefWidth(80);

        HBox ipPortRow = new HBox(10, espIpField, espPortField);
        ipPortRow.setAlignment(Pos.CENTER_LEFT);

        // Connection Buttons
        espConnectButton = createButton("WIFI BAĞLAN", Theme.BLUE);
        espDisconnectButton = createButton("BAĞLANTIYI KES", Theme.RED);
        HBox.setHgrow(espConnectButton, Priority.ALWAYS);
        HBox.setHgrow(espDisconnectButton, Priority.ALWAYS);
        espConnectButton.setMaxWidth(Double.MAX_VALUE);
        espDisconnectButton.setMaxWidth(Double.MAX_VALUE);

        HBox connectionButtonRow = new HBox(10, espConnectButton, espDisconnectButton);
        connectionButtonRow.setAlignment(Pos.CENTER);

        // LED Action Buttons
        ledOnButton = createButton("LED YAK (ON)", Theme.GREEN);
        ledOffButton = createButton("LED SÖNDÜR (OFF)", Theme.ORANGE);
        HBox.setHgrow(ledOnButton, Priority.ALWAYS);
        HBox.setHgrow(ledOffButton, Priority.ALWAYS);
        ledOnButton.setMaxWidth(Double.MAX_VALUE);
        ledOffButton.setMaxWidth(Double.MAX_VALUE);

        HBox ledButtonRow = new HBox(10, ledOnButton, ledOffButton);
        ledButtonRow.setAlignment(Pos.CENTER);

        // Connection Indicator
        espConnectionLed = new Circle(7);
        espConnectionLed.setFill(Color.web("#ef4444"));
        espConnectionLed.getStyleClass().add("led-offline");

        espStatusLabel = new Label("Durum: Bağlantı Yok");
        espStatusLabel.setStyle("-fx-text-fill: #cbd5e1; -fx-font-size: 13px;");

        espLastCheckLabel = new Label("");
        espLastCheckLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px;");

        Region statusSpacer = new Region();
        HBox.setHgrow(statusSpacer, Priority.ALWAYS);

        HBox statusRow = new HBox(8, espConnectionLed, espStatusLabel, statusSpacer, espLastCheckLabel);
        statusRow.setAlignment(Pos.CENTER_LEFT);

        frame.getChildren().addAll(
                title,
                createSmallLabel("ESP8266 IP Adresi ve Port"),
                ipPortRow,
                connectionButtonRow,
                createSmallLabel("LED Kontrol Komutları"),
                ledButtonRow,
                statusRow
        );

        return frame;
    }

    private void bindEspEvents() {
        espConnectButton.setOnAction(e -> {
            String ip = espIpField.getText().trim();
            String portStr = espPortField.getText().trim();
            if (ip.isEmpty() || portStr.isEmpty()) {
                logManager.logEkle("Hata: IP adresi veya Port boş olamaz.");
                return;
            }
            int port;
            try {
                port = Integer.parseInt(portStr);
            } catch (NumberFormatException ex) {
                logManager.logEkle("Hata: Geçersiz port numarası.");
                return;
            }

            updateEspControlStates(false, true, "Bağlanıyor...");
            espClientManager.connect(ip, port, new EspClientManager.ConnectionCallback() {
                @Override
                public void onConnectionStateChanged(boolean connected, String statusMessage) {
                    updateEspControlStates(connected, false, statusMessage);
                    if (connected) {
                        updateLastCheckTime();
                    }
                }

                @Override
                public void onResponseReceived(String response) {
                    if (response.contains("OK LED ON")) {
                        logManager.logEkle("ESP8266: LED başarıyla yakıldı.");
                        setLedOnActive(true);
                        updateLastCheckTime();
                    } else if (response.contains("OK LED OFF")) {
                        logManager.logEkle("ESP8266: LED başarıyla söndürüldü.");
                        setLedOnActive(false);
                        updateLastCheckTime();
                    } else {
                        logManager.logEkle("ESP8266 Yanıtı: " + response);
                        resetLedButtons();
                    }
                }

                @Override
                public void onHeartbeatSuccess() {
                    updateLastCheckLabel();
                }
            });
        });

        espDisconnectButton.setOnAction(e -> {
            espClientManager.disconnect();
            updateEspControlStates(false, false, "Bağlantı Kesildi");
            logManager.logEkle("ESP8266 WiFi bağlantısı kesildi.");
        });

        ledOnButton.setOnAction(e -> {
            espClientManager.sendLedCommand("1", new EspClientManager.ConnectionCallback() {
                @Override
                public void onConnectionStateChanged(boolean connected, String statusMessage) {
                    updateEspControlStates(connected, false, statusMessage);
                }

                @Override
                public void onResponseReceived(String response) {
                    if (response.contains("OK LED ON")) {
                        logManager.logEkle("LED Başarıyla Yakıldı.");
                        setLedOnActive(true);
                        updateLastCheckTime();
                    } else {
                        logManager.logEkle("LED Yakma Başarısız: " + response);
                        resetLedButtons();
                    }
                }
            });
        });

        ledOffButton.setOnAction(e -> {
            espClientManager.sendLedCommand("0", new EspClientManager.ConnectionCallback() {
                @Override
                public void onConnectionStateChanged(boolean connected, String statusMessage) {
                    updateEspControlStates(connected, false, statusMessage);
                }

                @Override
                public void onResponseReceived(String response) {
                    if (response.contains("OK LED OFF")) {
                        logManager.logEkle("LED Başarıyla Söndürüldü.");
                        setLedOnActive(false);
                        updateLastCheckTime();
                    } else {
                        logManager.logEkle("LED Söndürme Başarısız: " + response);
                        resetLedButtons();
                    }
                }
            });
        });
    }

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private void updateLastCheckTime() {
        lastSuccessfulCheckTime = java.time.LocalDateTime.now();
        refreshLastCheckLabel();
    }

    private void refreshLastCheckLabel() {
        Platform.runLater(() -> {
            if (lastSuccessfulCheckTime == null) {
                espLastCheckLabel.setText("Son kontrol: -");
                return;
            }
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            if (now.isBefore(lastSuccessfulCheckTime)) {
                espLastCheckLabel.setText("Son kontrol: " + lastSuccessfulCheckTime.format(TIME_FORMATTER) + " (0 sn önce)");
                return;
            }

            java.time.LocalDateTime temp = lastSuccessfulCheckTime;

            long months = java.time.temporal.ChronoUnit.MONTHS.between(temp, now);
            temp = temp.plusMonths(months);

            long days = java.time.temporal.ChronoUnit.DAYS.between(temp, now);
            temp = temp.plusDays(days);

            long hours = java.time.temporal.ChronoUnit.HOURS.between(temp, now);
            temp = temp.plusHours(hours);

            long minutes = java.time.temporal.ChronoUnit.MINUTES.between(temp, now);
            temp = temp.plusMinutes(minutes);

            long seconds = java.time.temporal.ChronoUnit.SECONDS.between(temp, now);

            StringBuilder sb = new StringBuilder();
            if (months > 0) sb.append(months).append(" ay ");
            if (days > 0) sb.append(days).append(" gün ");
            if (hours > 0) sb.append(hours).append(" sa ");
            if (minutes > 0) sb.append(minutes).append(" dk ");
            if (seconds > 0 || sb.length() == 0) sb.append(seconds).append(" sn ");

            espLastCheckLabel.setText("Son kontrol: " + lastSuccessfulCheckTime.format(TIME_FORMATTER) + " (" + sb.toString().trim() + " önce)");
        });
    }

    private void updateLastCheckLabel() {
        updateLastCheckTime();
    }

    private void updateEspControlStates(boolean connected, boolean connecting, String statusText) {
        espIpField.setDisable(connected || connecting);
        espPortField.setDisable(connected || connecting);
        espConnectButton.setDisable(connected || connecting);
        espDisconnectButton.setDisable(!connected && !connecting);

        ledOnButton.setDisable(!connected);
        ledOffButton.setDisable(!connected);

        espStatusLabel.setText("Durum: " + statusText);
        if (statusLabel != null) {
            statusLabel.setText("Durum: " + statusText);
        }
        if (!connected) {
            refreshLastCheckLabel();
        }

        // Manuel kontrol panelini WiFi durumuna göre güncelle
        updateManualControlStates(connected);

        if (connected) {
            espConnectionLed.setFill(Color.web("#10b981"));
            espConnectionLed.getStyleClass().remove("led-offline");
            espConnectionLed.getStyleClass().add("led-online");

            if (connectionLed != null) {
                connectionLed.setFill(Color.web("#10b981"));
                connectionLed.getStyleClass().remove("led-offline");
                connectionLed.getStyleClass().add("led-online");
            }
        } else if (connecting) {
            espConnectionLed.setFill(Color.web("#f59e0b"));
            espConnectionLed.getStyleClass().remove("led-online");
            espConnectionLed.getStyleClass().remove("led-offline");

            if (connectionLed != null) {
                connectionLed.setFill(Color.web("#f59e0b"));
                connectionLed.getStyleClass().remove("led-online");
                connectionLed.getStyleClass().remove("led-offline");
            }
            resetLedButtons();
        } else {
            espConnectionLed.setFill(Color.web("#ef4444"));
            espConnectionLed.getStyleClass().remove("led-online");
            espConnectionLed.getStyleClass().add("led-offline");

            if (connectionLed != null) {
                connectionLed.setFill(Color.web("#ef4444"));
                connectionLed.getStyleClass().remove("led-online");
                connectionLed.getStyleClass().add("led-offline");
            }
            resetLedButtons();
        }
    }

    /**
     * Manuel kontrol panelini WiFi bağlantı durumuna göre etkinleştirir/devre dışı bırakır.
     * WiFi bağlı değilse: butonlar soluk ve tıklanamaz, uyarı etiketi görünür.
     * WiFi bağlıysa: butonlar aktif, uyarı etiketi gizli.
     */
    private void updateManualControlStates(boolean wifiConnected) {
        boolean disabled = !wifiConnected;

        // DPAD butonlarını devre dışı bırak/etkinleştir
        ileriButton.setDisable(disabled);
        geriButton.setDisable(disabled);
        solButton.setDisable(disabled);
        sagButton.setDisable(disabled);
        homeButton.setDisable(disabled);

        // Radio butonlarını devre dışı bırak/etkinleştir
        if (radioRowRef != null) {
            for (javafx.scene.Node node : radioRowRef.getChildren()) {
                node.setDisable(disabled);
            }
        }

        // Uyarı etiketini göster/gizle
        if (wifiWarningLabel != null) {
            wifiWarningLabel.setVisible(disabled);
            wifiWarningLabel.setManaged(disabled);
        }

        // Panel opacity'sini ayarla (disabled görünümü için ek ipucu)
        if (dpadRef != null) {
            dpadRef.setOpacity(disabled ? 0.45 : 1.0);
        }
    }

    private void setLedOnActive(boolean active) {
        if (active) {
            ledOnButton.getStyleClass().removeAll("btn-green", "btn-green-active");
            ledOnButton.getStyleClass().add("btn-green-active");

            ledOffButton.getStyleClass().removeAll("btn-orange", "btn-orange-active");
            ledOffButton.getStyleClass().add("btn-orange");
        } else {
            ledOnButton.getStyleClass().removeAll("btn-green", "btn-green-active");
            ledOnButton.getStyleClass().add("btn-green");

            ledOffButton.getStyleClass().removeAll("btn-orange", "btn-orange-active");
            ledOffButton.getStyleClass().add("btn-orange-active");
        }
    }

    private void resetLedButtons() {
        ledOnButton.getStyleClass().removeAll("btn-green", "btn-green-active");
        ledOnButton.getStyleClass().add("btn-green");

        ledOffButton.getStyleClass().removeAll("btn-orange", "btn-orange-active");
        ledOffButton.getStyleClass().add("btn-orange");
    }

    public void cleanup() {
        if (serialManager != null) {
            serialManager.disconnect();
        }
        if (espClientManager != null) {
            espClientManager.disconnect();
        }
        if (lastCheckTimeline != null) {
            lastCheckTimeline.stop();
        }
    }
}