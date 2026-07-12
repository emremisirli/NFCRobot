package com.visiumfarm;

import com.fazecast.jSerialComm.SerialPort;

public class SerialManager {

    private SerialPort activePort;

    public SerialPort[] getAvailablePorts() {
        return SerialPort.getCommPorts();
    }

    public boolean connect(String portName, int baudRate) {
        activePort = SerialPort.getCommPort(portName);

        activePort.setBaudRate(baudRate);
        activePort.setNumDataBits(8);
        activePort.setNumStopBits(SerialPort.ONE_STOP_BIT);
        activePort.setParity(SerialPort.NO_PARITY);

        return activePort.openPort();
    }

    public void disconnect() {
        if (activePort != null && activePort.isOpen()) {
            activePort.closePort();
        }
    }

    public boolean isConnected() {
        return activePort != null && activePort.isOpen();
    }

    public boolean sendCommand(String command) {
        if (!isConnected()) {
            return false;
        }

        String fullCommand = command + "\n";
        byte[] data = fullCommand.getBytes();

        int bytesWritten = activePort.writeBytes(data, data.length);

        return bytesWritten > 0;
    }
    // comment line
}