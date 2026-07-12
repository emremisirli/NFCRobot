package com.visiumfarm;

import javafx.scene.control.TextArea;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class LogManager {

    private final TextArea logArea;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    public LogManager(TextArea logArea) {
        this.logArea = logArea;
    }

    public void logEkle(String mesaj) {
        String time = LocalTime.now().format(formatter);
        logArea.appendText("> [" + time + "] " + mesaj + "\n");
    }

    public void temizle() {
        logArea.clear();
    }
}