package de.bund.zrb;

import java.io.IOException;
import java.net.Socket;

public class ClientConnectionTask implements Runnable {

    private final Socket clientSocket;
    private final ProxyConnectionHandler connectionHandler;

    public ClientConnectionTask(Socket clientSocket, ProxyConnectionHandler connectionHandler) {
        this.clientSocket = clientSocket;
        this.connectionHandler = connectionHandler;
    }

    @Override
    public void run() {
        try {
            connectionHandler.handle(clientSocket);
        } catch (IOException e) {
            System.err.println("[Proxy] Connection error: " + e.getMessage());
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
