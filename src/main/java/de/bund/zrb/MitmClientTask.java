package de.bund.zrb;

import java.io.IOException;
import java.net.Socket;

public class MitmClientTask implements Runnable {

    private final Socket clientSocket;
    private final MitmConnectionHandler handler;

    public MitmClientTask(Socket clientSocket, MitmConnectionHandler handler) {
        this.clientSocket = clientSocket;
        this.handler = handler;
    }

    @Override
    public void run() {
        try {
            handler.handle(clientSocket);
        } catch (IOException e) {
            System.err.println("[MITM] Connection error: " + e.getMessage());
        } finally {
            closeQuietly(clientSocket);
        }
    }

    private void closeQuietly(Socket socket) {
        if (socket == null) {
            return;
        }
        try {
            socket.close();
        } catch (IOException ignored) {
            // Ignore
        }
    }
}
