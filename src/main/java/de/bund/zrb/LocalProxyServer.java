package de.bund.zrb;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class LocalProxyServer {

    private final int listenPort;
    private final ProxyConnectionHandler connectionHandler;

    public LocalProxyServer(int listenPort) {
        this.listenPort = listenPort;
//        this.connectionHandler = new ProxyConnectionHandler(null); // MITM aus
        MitmHandler mitm = new OpenAiMitmHandler(
                "api.openai.com",
                keystorePathFromUi,
                "changeit" // oder aus Config
        );
        ProxyConnectionHandler connectionHandler = new ProxyConnectionHandler(mitm); // MITM an
    }

    public void start() throws IOException {
        ServerSocket serverSocket = new ServerSocket(listenPort);
        System.out.println("[Proxy] Listening on port " + listenPort);

        while (true) {
            Socket clientSocket = serverSocket.accept();
            System.out.println("[Proxy] Incoming connection from " + clientSocket.getRemoteSocketAddress());
            handleClientAsync(clientSocket);
        }
    }

    private void handleClientAsync(Socket clientSocket) {
        Thread clientThread = new Thread(new ClientConnectionTask(clientSocket, connectionHandler));
        clientThread.setDaemon(true);
        clientThread.start();
    }

    public static void main(String[] args) throws IOException {
        int port = 8888; // keep in sync with IntelliJ/GitHub Copilot settings
        new LocalProxyServer(port).start();
    }
}
