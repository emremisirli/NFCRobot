package com.visiumfarm;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.util.Duration;
import java.time.format.DateTimeFormatter;

public class UIBuilder {

    private LogManager logManager;
    private TextArea logArea;

    private Label statusLabel;
    private Label deviceLabel;
    private Circle connectionLed;

    private EspClientManager espClientManager;
    private TextField espIpField;
    private TextField espPortField;
    private Button espConnectButton;
    private Button espDisconnectButton;
    private Label espStatusLabel;
    private Label espLastCheckLabel;
    private Circle espConnectionLed;
    private java.time.LocalDateTime lastSuccessfulCheckTime = null;
    private Timeline lastCheckTimeline;

    // Servo Kontrol Bileşenleri
    private Button btnHome;
    private Button btnTarget;
    private Arc dialBackground;
    private Arc valueArc;
    private Line needle;
    private Circle centerPin;
    private Label angleValueLabel;
    private Label servoWarningLabel;
    private VBox servoControlPanelRef;

    // Test (N tekrar) bileşenleri
    private TextField nField;
    private Button btnStartTest;
    private Button btnStopTest;
    private Label testStatusLabel;

    private volatile boolean testRunning = false;
    private Thread testThread;

    // Servo pozisyonlari ve test zamanlamasi
    private static final double HOME_ANGLE = 90;
    private static final double TARGET_ANGLE = 180;
    private static final long TARGET_HOLD_MS = 3000; // hedefte bekleme suresi
    private static final long SETTLE_MS = 800;       // her hareket sonrasi oturma suresi

    public BorderPane build() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");

        VBox mainContainer = new VBox(15);
        mainContainer.setPadding(new Insets(15));

        VBox espControlPanel = createEspControlPanel();
        VBox servoControlPanel = createServoControlPanel();

        espControlPanel.setPrefWidth(520);
        servoControlPanel.setPrefWidth(520);

        HBox.setHgrow(espControlPanel, Priority.ALWAYS);
        HBox.setHgrow(servoControlPanel, Priority.ALWAYS);

        HBox topRow = new HBox(15);
        topRow.getChildren().addAll(
                espControlPanel,
                servoControlPanel
        );

        mainContainer.getChildren().addAll(topRow, createLogPanel());

        // Header, Footer, and Center
        root.setTop(createHeaderPanel());
        root.setCenter(mainContainer);
        root.setBottom(createStatusBar());

        // LogManager ve EspClientManager Başlatma
        logManager = new LogManager(logArea);
        espClientManager = new EspClientManager(logManager);

        // Son kontrol zamanını güncelleyen timeline
        lastCheckTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            refreshLastCheckLabel();
        }));
        lastCheckTimeline.setCycleCount(Timeline.INDEFINITE);
        lastCheckTimeline.play();

        // Olay bağlamaları
        bindEspEvents();
        updateEspControlStates(false, false, "Bağlantı Yok");

        // Sahne yüklendiğinde Klavye Kısayollarını tanımla
        root.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.setOnKeyPressed(event -> {
                    switch (event.getCode()) {
                        case DIGIT1:
                        case NUMPAD1:
                            if (btnHome != null && !btnHome.isDisable()) {
                                btnHome.fire();
                            }
                            event.consume();
                            break;
                        case DIGIT2:
                        case NUMPAD2:
                            if (btnTarget != null && !btnTarget.isDisable()) {
                                btnTarget.fire();
                            }
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

    private VBox createEspControlPanel() {
        VBox frame = createCard();

        Label title = createTitle("ESP8266 BAĞLANTI AYARLARI");

        // IP ve Port Giriş Alanları
        espIpField = new TextField("192.168.2.100");
        espIpField.setPromptText("IP Adresi");
        HBox.setHgrow(espIpField, Priority.ALWAYS);
        espIpField.setMaxWidth(Double.MAX_VALUE);

        espPortField = new TextField("5000");
        espPortField.setPromptText("Port");
        espPortField.setPrefWidth(80);

        HBox ipPortRow = new HBox(10, espIpField, espPortField);
        ipPortRow.setAlignment(Pos.CENTER_LEFT);

        // Bağlantı Butonları
        espConnectButton = createButton("WIFI BAĞLAN", Theme.BLUE);
        espDisconnectButton = createButton("BAĞLANTIYI KES", Theme.RED);
        HBox.setHgrow(espConnectButton, Priority.ALWAYS);
        HBox.setHgrow(espDisconnectButton, Priority.ALWAYS);
        espConnectButton.setMaxWidth(Double.MAX_VALUE);
        espDisconnectButton.setMaxWidth(Double.MAX_VALUE);

        HBox connectionButtonRow = new HBox(10, espConnectButton, espDisconnectButton);
        connectionButtonRow.setAlignment(Pos.CENTER);

        // Bağlantı Durumu Göstergesi
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
                statusRow
        );

        return frame;
    }

    private VBox createServoControlPanel() {
        VBox frame = createCard();
        servoControlPanelRef = frame;

        Label title = createTitle("SERVO MOTOR KONTROLÜ");

        // WiFi bağlantısı uyarı etiketi
        servoWarningLabel = new Label("⚠ WiFi bağlantısı gerekli");
        servoWarningLabel.setStyle("-fx-text-fill: #f59e0b; -fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 4 10 4 10; -fx-background-color: rgba(245,158,11,0.1); -fx-background-radius: 6; -fx-border-color: #f59e0b; -fx-border-radius: 6; -fx-border-width: 1;");
        servoWarningLabel.setAlignment(Pos.CENTER);
        servoWarningLabel.setMaxWidth(Double.MAX_VALUE);

        // Grafiksel Açı Kadranı
        Pane gaugePane = new Pane();
        gaugePane.setPrefSize(150, 95);
        gaugePane.setMaxSize(150, 95);

        dialBackground = new Arc(75, 80, 65, 65, 180, -180);
        dialBackground.getStyleClass().add("gauge-background");

        valueArc = new Arc(75, 80, 65, 65, 180, 0);
        valueArc.getStyleClass().add("gauge-value-arc");

        needle = new Line(75, 80, 25, 80);
        needle.getStyleClass().add("gauge-needle");

        centerPin = new Circle(75, 80, 8);
        centerPin.getStyleClass().add("gauge-center-pin");

        Label label0 = new Label("0°");
        label0.getStyleClass().add("gauge-tick-label");
        label0.setLayoutX(3);
        label0.setLayoutY(75);

        Label label90 = new Label("90°");
        label90.getStyleClass().add("gauge-tick-label");
        label90.setLayoutX(65);
        label90.setLayoutY(3);

        Label label180 = new Label("180°");
        label180.getStyleClass().add("gauge-tick-label");
        label180.setLayoutX(128);
        label180.setLayoutY(75);

        gaugePane.getChildren().addAll(dialBackground, valueArc, needle, centerPin, label0, label90, label180);

        angleValueLabel = new Label("90°");
        angleValueLabel.setStyle("-fx-text-fill: #38bdf8; -fx-font-size: 18px; -fx-font-weight: bold;");

        VBox gaugeBox = new VBox(5, gaugePane, angleValueLabel);
        gaugeBox.setAlignment(Pos.CENTER);

        // Pozisyon Butonları (yalnizca HOME ve TARGET)
        btnHome = createButton("HOME (90°)", Theme.DARK_BUTTON);
        btnTarget = createButton("TARGET (180°)", Theme.DARK_BUTTON);

        btnHome.setPrefWidth(190);
        btnTarget.setPrefWidth(190);

        HBox servoButtonsRow = new HBox(10, btnHome, btnTarget);
        servoButtonsRow.setAlignment(Pos.CENTER);

        // Test (N tekrar) satiri
        nField = new TextField("5");
        nField.setPromptText("N");
        nField.setPrefWidth(70);

        btnStartTest = createButton("TESTİ BAŞLAT", Theme.BLUE);
        btnStopTest = createButton("DURDUR", Theme.RED);
        btnStartTest.setPrefWidth(150);
        btnStopTest.setPrefWidth(110);

        HBox testRow = new HBox(10, nField, btnStartTest, btnStopTest);
        testRow.setAlignment(Pos.CENTER);

        testStatusLabel = new Label("");
        testStatusLabel.setStyle("-fx-text-fill: #38bdf8; -fx-font-size: 13px; -fx-font-weight: bold;");

        frame.getChildren().addAll(
                title,
                servoWarningLabel,
                gaugeBox,
                createSmallLabel("Pozisyon Seçimi"),
                servoButtonsRow,
                createSmallLabel("Otomatik Test (HOME → TARGET → 3sn bekle → HOME, N kez)"),
                testRow,
                testStatusLabel
        );

        // Varsayılan gösterge açısını ayarla
        updateNeedle(HOME_ANGLE);

        return frame;
    }

    private void updateNeedle(double angle) {
        double rad = Math.toRadians(angle);
        double r = 50; // İbre yarıçapı
        double centerX = 75;
        double centerY = 80;
        double endX = centerX - r * Math.cos(rad);
        double endY = centerY - r * Math.sin(rad);
        needle.setEndX(endX);
        needle.setEndY(endY);
        valueArc.setLength(-angle);
        angleValueLabel.setText(String.format("%.0f°", angle));
    }

    private void updateServoButtonActiveState(double angle) {
        // Tüm butonları varsayılan koyu temaya sıfırla
        btnHome.getStyleClass().removeAll("btn-blue-active", "btn-dark");
        btnTarget.getStyleClass().removeAll("btn-blue-active", "btn-dark");

        btnHome.getStyleClass().add("btn-dark");
        btnTarget.getStyleClass().add("btn-dark");

        // Seçilen butona aktif sınıfını uygula
        if (angle == HOME_ANGLE) {
            btnHome.getStyleClass().remove("btn-dark");
            btnHome.getStyleClass().add("btn-blue-active");
        } else if (angle == TARGET_ANGLE) {
            btnTarget.getStyleClass().remove("btn-dark");
            btnTarget.getStyleClass().add("btn-blue-active");
        }
    }

    private void resetServoButtons() {
        btnHome.getStyleClass().removeAll("btn-blue-active", "btn-dark");
        btnTarget.getStyleClass().removeAll("btn-blue-active", "btn-dark");

        btnHome.getStyleClass().add("btn-dark");
        btnTarget.getStyleClass().add("btn-dark");
    }

    private void setServoButtonsDisable(boolean disable) {
        btnHome.setDisable(disable);
        btnTarget.setDisable(disable);
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
                    logManager.logEkle("ESP8266 Yanıtı: " + response);
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

        btnHome.setOnAction(e -> sendManualMove("HOME", HOME_ANGLE));
        btnTarget.setOnAction(e -> sendManualMove("TARGET", TARGET_ANGLE));
        btnStartTest.setOnAction(e -> startTest());
        btnStopTest.setOnAction(e -> stopTest());
    }

    private void sendManualMove(String command, double angle) {
        setServoButtonsDisable(true);
        espClientManager.sendCommand(command, new EspClientManager.ConnectionCallback() {
            @Override
            public void onConnectionStateChanged(boolean connected, String statusMessage) {
                updateEspControlStates(connected, false, statusMessage);
            }

            @Override
            public void onResponseReceived(String response) {
                setServoButtonsDisable(false);
                if (response.startsWith("OK")) {
                    updateNeedle(angle);
                    updateServoButtonActiveState(angle);
                    updateLastCheckTime();
                } else {
                    logManager.logEkle("Servo Motor hareket hatası: " + response);
                }
            }
        });
    }

    private void startTest() {
        if (testRunning) {
            return;
        }
        if (!espClientManager.isConnected()) {
            logManager.logEkle("Hata: Test başlatılamadı! ESP8266 bağlantısı yok.");
            return;
        }

        final int n;
        try {
            n = Integer.parseInt(nField.getText().trim());
        } catch (NumberFormatException ex) {
            logManager.logEkle("Hata: Geçersiz tekrar sayısı (N). Bir tam sayı girin.");
            return;
        }
        if (n < 1) {
            logManager.logEkle("Hata: Tekrar sayısı (N) en az 1 olmalıdır.");
            return;
        }

        testRunning = true;
        setTestUiRunning(true);
        logManager.logEkle("Otomatik test başladı. Tekrar sayısı N=" + n);

        testThread = new Thread(() -> {
            try {
                // Teste HOME pozisyonundan başla
                testMove("HOME", HOME_ANGLE);
                if (!sleepTest(SETTLE_MS)) return;

                for (int i = 1; i <= n && testRunning; i++) {
                    final int cycle = i;
                    Platform.runLater(() -> testStatusLabel.setText("Test döngüsü: " + cycle + " / " + n));

                    testMove("TARGET", TARGET_ANGLE);
                    if (!sleepTest(TARGET_HOLD_MS)) return;   // hedefte 3 sn bekle

                    testMove("HOME", HOME_ANGLE);
                    if (!sleepTest(SETTLE_MS)) return;

                    logManager.logEkle("Test döngüsü tamamlandı: " + cycle + " / " + n);
                }

                if (testRunning) {
                    logManager.logEkle("Otomatik test tamamlandı (" + n + " döngü).");
                }
            } catch (java.io.IOException ex) {
                logManager.logEkle("Test hatası: " + ex.getMessage());
            } finally {
                testRunning = false;
                Platform.runLater(() -> setTestUiRunning(false));
            }
        }, "Servo-Test-Thread");
        testThread.setDaemon(true);
        testThread.start();
    }

    private void stopTest() {
        if (testRunning) {
            testRunning = false;
            logManager.logEkle("Otomatik test kullanıcı tarafından durduruldu.");
        }
    }

    // Test thread'inden cagrilir: komut gonderir ve gostergeyi gunceller.
    private void testMove(String command, double angle) throws java.io.IOException {
        String response = espClientManager.sendCommandBlocking(command);
        if (!response.startsWith("OK")) {
            throw new java.io.IOException("Beklenmeyen yanıt: " + response);
        }
        Platform.runLater(() -> {
            updateNeedle(angle);
            updateServoButtonActiveState(angle);
            updateLastCheckTime();
        });
    }

    // testRunning false olursa erken cikar; durdurulabilir bekleme. false donerse test iptal.
    private boolean sleepTest(long ms) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            if (!testRunning) {
                return false;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException ex) {
                return false;
            }
        }
        return testRunning;
    }

    private void setTestUiRunning(boolean running) {
        boolean connected = espClientManager != null && espClientManager.isConnected();
        btnHome.setDisable(running || !connected);
        btnTarget.setDisable(running || !connected);
        nField.setDisable(running || !connected);
        btnStartTest.setDisable(running || !connected);
        btnStopTest.setDisable(!running);
        if (!running) {
            testStatusLabel.setText("");
        }
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

        updateServoControlStates(connected);

        espStatusLabel.setText("Durum: " + statusText);
        if (statusLabel != null) {
            statusLabel.setText("Durum: " + statusText);
        }
        if (!connected) {
            refreshLastCheckLabel();
            resetServoButtons();
        }

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
        } else {
            espConnectionLed.setFill(Color.web("#ef4444"));
            espConnectionLed.getStyleClass().remove("led-online");
            espConnectionLed.getStyleClass().add("led-offline");

            if (connectionLed != null) {
                connectionLed.setFill(Color.web("#ef4444"));
                connectionLed.getStyleClass().remove("led-online");
                connectionLed.getStyleClass().add("led-offline");
            }
        }
    }

    private void updateServoControlStates(boolean wifiConnected) {
        boolean disabled = !wifiConnected || testRunning;

        btnHome.setDisable(disabled);
        btnTarget.setDisable(disabled);
        if (nField != null) nField.setDisable(disabled);
        if (btnStartTest != null) btnStartTest.setDisable(disabled);
        if (btnStopTest != null) btnStopTest.setDisable(!testRunning);

        if (servoWarningLabel != null) {
            servoWarningLabel.setVisible(!wifiConnected);
            servoWarningLabel.setManaged(!wifiConnected);
        }

        double opacity = disabled ? 0.35 : 1.0;
        if (dialBackground != null) dialBackground.setOpacity(opacity);
        if (valueArc != null) valueArc.setOpacity(opacity);
        if (needle != null) needle.setOpacity(opacity);
        if (centerPin != null) centerPin.setOpacity(opacity);
        if (angleValueLabel != null) angleValueLabel.setOpacity(opacity);
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

    public void cleanup() {
        testRunning = false;
        if (espClientManager != null) {
            espClientManager.disconnect();
        }
        if (lastCheckTimeline != null) {
            lastCheckTimeline.stop();
        }
    }
}