package com.visiumfarm;

public class CommandManager {

    private final SerialManager serialManager;
    private final LogManager logManager;

    public CommandManager(SerialManager serialManager, LogManager logManager) {
        this.serialManager = serialManager;
        this.logManager = logManager;
    }

    public void sendMoveCommand(String direction, int step) {

        String command = direction + ":" + step;

        if (serialManager.sendCommand(command)) {
            logManager.logEkle("Komut gönderildi -> " + command);
        } else {
            logManager.logEkle("Komut gönderilemedi! Seri port bağlı değil.");
        }
    }

    public void sendHomeCommand() {

        if (serialManager.sendCommand("HOME")) {
            logManager.logEkle("HOME komutu gönderildi.");
        } else {
            logManager.logEkle("HOME komutu gönderilemedi.");
        }
    }
}