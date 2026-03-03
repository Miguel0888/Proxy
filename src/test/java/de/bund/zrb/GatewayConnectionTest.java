package de.bund.zrb;

import de.bund.zrb.server.LocalProxyServer;
import de.bund.zrb.server.gateway.GatewaySessionManager;
import org.junit.jupiter.api.*;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for Gateway Client-Server connection.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GatewayConnectionTest {

    private static final int TEST_PORT = 18888;
    private static final String TEST_PASSKEY = "testpasskey123";

    private LocalProxyServer server;

    @BeforeEach
    void setUp() throws Exception {
        // Server mit Gateway-Mode starten
        GatewaySessionManager gsm = new GatewaySessionManager();
        GatewayGate gate = new GatewayGate(true); // Gateway required

        server = new LocalProxyServer(
                TEST_PORT,
                null, // kein MITM
                new DirectConnectionProvider(10000, 30000),
                gsm,
                TEST_PASSKEY,
                null, // keine View
                gate
        );
        server.start();

        // Kurz warten bis Server bereit ist
        Thread.sleep(500);
        
        System.out.println("=== Test Server started on port " + TEST_PORT + " ===");
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
            System.out.println("=== Test Server stopped ===");
        }
    }

    @Test
    @Order(1)
    @DisplayName("Test: Server accepts TCP connection")
    void testTcpConnection() throws Exception {
        System.out.println("\n--- Test: TCP Connection ---");
        
        try (Socket socket = new Socket("127.0.0.1", TEST_PORT)) {
            assertTrue(socket.isConnected(), "Socket should be connected");
            System.out.println("TCP connection successful!");
        }
    }

    @Test
    @Order(2)
    @DisplayName("Test: Server responds to HELLO with correct passkey")
    void testHelloWithCorrectPasskey() throws Exception {
        System.out.println("\n--- Test: HELLO with correct passkey ---");
        
        try (Socket socket = new Socket("127.0.0.1", TEST_PORT)) {
            socket.setSoTimeout(10000);
            
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), "UTF-8"));
            PrintWriter writer = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

            // HELLO senden
            String hello = "HELLO " + TEST_PASSKEY;
            System.out.println("Sending: " + hello);
            writer.println(hello);

            // Antwort lesen
            System.out.println("Waiting for response...");
            String response = reader.readLine();
            System.out.println("Response: '" + response + "'");

            assertNotNull(response, "Server should respond");
            assertEquals("OK", response.trim(), "Server should respond with OK");
        }
    }

    @Test
    @Order(3)
    @DisplayName("Test: Server rejects HELLO with wrong passkey")
    void testHelloWithWrongPasskey() throws Exception {
        System.out.println("\n--- Test: HELLO with wrong passkey ---");
        
        try (Socket socket = new Socket("127.0.0.1", TEST_PORT)) {
            socket.setSoTimeout(10000);
            
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), "UTF-8"));
            PrintWriter writer = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

            // HELLO mit falschem Passkey senden
            String hello = "HELLO wrongpasskey";
            System.out.println("Sending: " + hello);
            writer.println(hello);

            // Antwort lesen
            System.out.println("Waiting for response...");
            String response = reader.readLine();
            System.out.println("Response: '" + response + "'");

            assertNotNull(response, "Server should respond");
            assertEquals("DENIED", response.trim(), "Server should respond with DENIED");
        }
    }

    @Test
    @Order(4)
    @DisplayName("Test: Server returns BUSY when second client connects")
    void testSecondClientGetsBusy() throws Exception {
        System.out.println("\n--- Test: Second client gets BUSY ---");
        
        // Erster Client verbindet sich
        Socket client1 = new Socket("127.0.0.1", TEST_PORT);
        client1.setSoTimeout(10000);
        
        BufferedReader reader1 = new BufferedReader(
                new InputStreamReader(client1.getInputStream(), "UTF-8"));
        PrintWriter writer1 = new PrintWriter(
                new OutputStreamWriter(client1.getOutputStream(), "UTF-8"), true);

        writer1.println("HELLO " + TEST_PASSKEY);
        String response1 = reader1.readLine();
        System.out.println("Client 1 response: '" + response1 + "'");
        assertEquals("OK", response1.trim(), "First client should get OK");

        // Kurz warten
        Thread.sleep(200);

        // Zweiter Client versucht zu verbinden
        try (Socket client2 = new Socket("127.0.0.1", TEST_PORT)) {
            client2.setSoTimeout(10000);
            
            BufferedReader reader2 = new BufferedReader(
                    new InputStreamReader(client2.getInputStream(), "UTF-8"));
            PrintWriter writer2 = new PrintWriter(
                    new OutputStreamWriter(client2.getOutputStream(), "UTF-8"), true);

            writer2.println("HELLO " + TEST_PASSKEY);
            String response2 = reader2.readLine();
            System.out.println("Client 2 response: '" + response2 + "'");
            assertEquals("BUSY", response2.trim(), "Second client should get BUSY");
        }

        client1.close();
    }

    @Test
    @Order(5)
    @DisplayName("Test: GatewayClient connects successfully")
    void testGatewayClientConnection() throws Exception {
        System.out.println("\n--- Test: GatewayClient connection ---");
        
        CountDownLatch connectedLatch = new CountDownLatch(1);
        AtomicReference<String> statusRef = new AtomicReference<>("");
        AtomicReference<Boolean> successRef = new AtomicReference<>(false);

        // Einfache ProxyView-Implementierung für Test
        de.bund.zrb.common.ProxyView testView = new de.bund.zrb.common.ProxyView() {
            @Override
            public int getServerPort() { return TEST_PORT; }
            
            @Override
            public String getServerGatewayPasskey() { return TEST_PASSKEY; }
            
            @Override
            public String getClientTargetHost() { return "127.0.0.1"; }
            
            @Override
            public int getClientTargetPort() { return TEST_PORT; }
            
            @Override
            public String getClientGatewayPasskey() { return TEST_PASSKEY; }
            
            @Override
            public void updateGatewayClientStatus(String status, boolean connected) {
                System.out.println("GatewayClient status: " + status + " (connected=" + connected + ")");
                statusRef.set(status);
                if (connected) {
                    successRef.set(true);
                    connectedLatch.countDown();
                }
            }
        };

        // GatewayClient in separatem Thread starten
        Thread clientThread = new Thread(() -> {
            try {
                de.bund.zrb.client.GatewayClient client = new de.bund.zrb.client.GatewayClient(
                        "127.0.0.1",
                        TEST_PORT,
                        "test-client",
                        null, // kein TrafficListener
                        testView,
                        new DirectSocketDialer(10000, 30000)
                );
                client.run();
            } catch (IOException e) {
                System.err.println("GatewayClient error: " + e.getMessage());
                e.printStackTrace();
            }
        }, "test-gateway-client");
        clientThread.setDaemon(true);
        clientThread.start();

        // Warten auf Verbindung
        boolean connected = connectedLatch.await(10, TimeUnit.SECONDS);
        
        System.out.println("Final status: " + statusRef.get());
        System.out.println("Connected: " + successRef.get());
        
        assertTrue(connected, "GatewayClient should connect within 10 seconds");
        assertTrue(successRef.get(), "GatewayClient should report successful connection");
        assertTrue(statusRef.get().contains("connected"), "Status should indicate connected");
    }

    @Test
    @Order(6)
    @DisplayName("Test: Raw socket HELLO/OK exchange")
    void testRawSocketExchange() throws Exception {
        System.out.println("\n--- Test: Raw socket exchange (debugging) ---");
        
        Socket socket = new Socket();
        socket.connect(new java.net.InetSocketAddress("127.0.0.1", TEST_PORT), 5000);
        socket.setSoTimeout(10000);
        
        System.out.println("Connected to server");
        
        OutputStream out = socket.getOutputStream();
        InputStream in = socket.getInputStream();
        
        // HELLO senden (genau wie GatewayClient)
        String hello = "HELLO " + TEST_PASSKEY + "\r\n";
        System.out.println("Sending bytes: " + bytesToHex(hello.getBytes("UTF-8")));
        out.write(hello.getBytes("UTF-8"));
        out.flush();
        System.out.println("Sent!");
        
        // Antwort lesen
        System.out.println("Reading response...");
        StringBuilder response = new StringBuilder();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') break;
            if (b != '\r') response.append((char) b);
        }
        
        System.out.println("Response: '" + response + "'");
        System.out.println("Response bytes: " + bytesToHex(response.toString().getBytes("UTF-8")));
        
        socket.close();
        
        assertEquals("OK", response.toString().trim(), "Should receive OK");
    }
    
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }

    @Test
    @Order(7)
    @DisplayName("Test: Server WITHOUT Gateway mode ignores HELLO (simulates user error)")
    void testServerWithoutGatewayModeIgnoresHello() throws Exception {
        System.out.println("\n--- Test: Server WITHOUT Gateway mode ---");
        
        // Server stoppen
        if (server != null) {
            server.stop();
        }
        
        // Server OHNE Gateway-Mode starten (gsm = null)
        GatewayGate gate = new GatewayGate(false); // Gateway NOT required

        server = new LocalProxyServer(
                TEST_PORT,
                null, // kein MITM
                new DirectConnectionProvider(10000, 30000),
                null, // KEIN GatewaySessionManager!
                TEST_PASSKEY,
                null, // keine View
                gate
        );
        server.start();
        Thread.sleep(500);
        
        System.out.println("Server started WITHOUT Gateway mode (gsm=null)");
        
        try (Socket socket = new Socket("127.0.0.1", TEST_PORT)) {
            socket.setSoTimeout(5000); // 5 Sekunden Timeout
            
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), "UTF-8"));
            PrintWriter writer = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

            // HELLO senden
            System.out.println("Sending: HELLO " + TEST_PASSKEY);
            writer.println("HELLO " + TEST_PASSKEY);

            // Antwort lesen - sollte ERROR kommen da Gateway-Mode nicht aktiviert
            System.out.println("Waiting for response...");
            try {
                String response = reader.readLine();
                System.out.println("Response: '" + response + "'");
                
                assertNotNull(response, "Server should respond with ERROR");
                assertTrue(response.startsWith("ERROR"), 
                        "Server should respond with ERROR when Gateway mode is disabled, got: " + response);
                System.out.println("SUCCESS: Server correctly rejected HELLO with ERROR message");
            } catch (java.net.SocketTimeoutException e) {
                fail("Server should respond with ERROR, not timeout");
            }
        }
    }
}

