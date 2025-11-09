package de.bund.zrb;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class MitmProxyServer {

    private final int listenPort;
    private final MitmConnectionHandler connectionHandler;

    public MitmProxyServer(int listenPort, String keyStorePath, String keyStorePassword, String mitmHostName) {
        this.listenPort = listenPort;
        this.connectionHandler = new MitmConnectionHandler(keyStorePath, keyStorePassword, mitmHostName);
    }

    public void start() throws IOException {
        ServerSocket serverSocket = new ServerSocket(listenPort);
        System.out.println("[MITM] Listening on port " + listenPort);

        while (true) {
            Socket clientSocket = serverSocket.accept();
            System.out.println("[MITM] Incoming connection from " + clientSocket.getRemoteSocketAddress());
            handleClientAsync(clientSocket);
        }
    }

    private void handleClientAsync(Socket clientSocket) {
        Thread t = new Thread(new MitmClientTask(clientSocket, connectionHandler));
        t.setDaemon(true);
        t.start();
    }

    public static void main(String[] args) throws IOException {
        int port = 8888; // use this in IntelliJ as HTTP proxy
        String keyStorePath = "myproxy.jks"; // same folder, or absolute path
        String keyStorePassword = "changeit";
        String mitmHostName = "api.openai.com";

        new MitmProxyServer(port, keyStorePath, keyStorePassword, mitmHostName).start();
    }
}
