package com.visiumfarm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;

public class EspClientManager {

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private final LogManager logManager;
    private final Object socketLock = new Object();

    private volatile boolean isConnecting = false;
    private volatile boolean isConnected = false;

    private ScheduledExecutorService heartbeatScheduler;
    private ConnectionCallback connectionCallback;

    public EspClientManager(LogManager logManager) {
        this.logManager = logManager;
    }

    public interface ConnectionCallback {
        void onConnectionStateChanged(boolean connected, String statusMessage);
        void onResponseReceived(String response);
        default void onHeartbeatSuccess() {}
    }

    public void connect(String ip, int port, ConnectionCallback callback) {
        if (isConnected() || isConnecting) {
            return;
        }

        isConnecting = true;
        this.connectionCallback = callback;
        callback.onConnectionStateChanged(false, "Bağlanıyor...");
        logManager.logEkle("ESP8266 WiFi sunucusuna bağlanılıyor: " + ip + ":" + port);

        // Run connection in a background thread to prevent UI freezing
        new Thread(() -> {
            try {
                Socket tempSocket = new Socket();
                synchronized (socketLock) {
                    if (!isConnecting) {
                        tempSocket.close();
                        return;
                    }
                    socket = tempSocket;
                }

                // Set connect timeout to 3 seconds
                socket.connect(new InetSocketAddress(ip, port), 3000);
                // Set read timeout to 3 seconds
                socket.setSoTimeout(3000);

                synchronized (socketLock) {
                    if (!isConnecting) {
                        disconnect();
                        return;
                    }
                    out = new PrintWriter(socket.getOutputStream(), true); // autoFlush = true
                    in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    isConnected = true;
                }

                logManager.logEkle("ESP8266 WiFi sunucusuna başarıyla bağlanıldı (" + ip + ":" + port + ")");
                startHeartbeat();
                Platform.runLater(() -> {
                    isConnecting = false;
                    callback.onConnectionStateChanged(true, "Bağlı (" + ip + ")");
                });
            } catch (Exception e) {
                logManager.logEkle("Hata: ESP8266 bağlantısı başarısız. " + e.getMessage());
                disconnect();
                Platform.runLater(() -> {
                    isConnecting = false;
                    callback.onConnectionStateChanged(false, "Bağlantı Hatası");
                });
            }
        }).start();
    }

    public void disconnect() {
        isConnecting = false;
        stopHeartbeat();
        isConnected = false;

        // Close the socket first to interrupt any blocked read/write
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (Exception e) {
            // Ignore
        }

        synchronized (socketLock) {
            try {
                if (out != null) out.close();
                if (in != null) in.close();
            } catch (Exception e) {
                logManager.logEkle("Hata: ESP8266 bağlantısı kapatılırken sorun oluştu: " + e.getMessage());
            } finally {
                socket = null;
                out = null;
                in = null;
            }
        }
    }

    public boolean isConnected() {
        return isConnected;
    }

    private String sendAndReceive(String command) throws IOException {
        synchronized (socketLock) {
            if (socket == null || socket.isClosed() || !isConnected) {
                throw new IOException("Bağlantı yok.");
            }
            out.println(command);
            String response = in.readLine();
            if (response == null) {
                throw new IOException("Sunucu bağlantıyı sonlandırdı.");
            }
            return response;
        }
    }

    private void startHeartbeat() {
        stopHeartbeat();
        heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ESP8266-Heartbeat-Thread");
            thread.setDaemon(true);
            return thread;
        });

        heartbeatScheduler.scheduleAtFixedRate(() -> {
            if (!isConnected) {
                return;
            }
            try {
                String response = sendAndReceive("PING");
                if (!"PONG".equals(response)) {
                    throw new IOException("Beklenmeyen yanıt: " + response);
                }
                if (connectionCallback != null) {
                    Platform.runLater(connectionCallback::onHeartbeatSuccess);
                }
            } catch (Exception e) {
                logManager.logEkle("Heartbeat hatası: " + e.getMessage());
                handleDisconnection("Bağlantı Koptu");
            }
        }, 3, 3, TimeUnit.SECONDS);
    }

    private void stopHeartbeat() {
        if (heartbeatScheduler != null) {
            heartbeatScheduler.shutdownNow();
            heartbeatScheduler = null;
        }
    }

    private void handleDisconnection(String message) {
        if (!isConnected) return;
        isConnected = false;
        disconnect();
        if (connectionCallback != null) {
            Platform.runLater(() -> connectionCallback.onConnectionStateChanged(false, message));
        }
    }

    public void sendCommand(String command, ConnectionCallback callback) {
        if (!isConnected()) {
            logManager.logEkle("Hata: Komut gönderilemedi! ESP8266 bağlantısı yok.");
            return;
        }

        // Run socket communication in a background thread to prevent UI freezing
        new Thread(() -> {
            try {
                logManager.logEkle("ESP8266'ya komut gönderiliyor -> " + command);
                String response = sendAndReceive(command);
                logManager.logEkle("ESP8266'dan yanıt geldi -> " + response);
                Platform.runLater(() -> callback.onResponseReceived(response));
            } catch (Exception e) {
                logManager.logEkle("Hata: ESP8266 komut gönderme hatası. " + e.getMessage());
                handleDisconnection("Bağlantı Koptu");
                Platform.runLater(() -> {
                    callback.onResponseReceived("HATA: " + e.getMessage());
                    callback.onConnectionStateChanged(false, "Bağlantı Koptu");
                });
            }
        }).start();
    }

    /**
     * Sends a command and blocks the CALLING thread until the response arrives.
     * Intended for use from a dedicated background thread (e.g. the servo test
     * loop), never from the JavaFX Application thread. Thread-safe with the
     * heartbeat because sendAndReceive serialises on socketLock.
     */
    public String sendCommandBlocking(String command) throws IOException {
        logManager.logEkle("ESP8266'ya komut gönderiliyor -> " + command);
        String response = sendAndReceive(command);
        logManager.logEkle("ESP8266'dan yanıt geldi -> " + response);
        return response;
    }
}
