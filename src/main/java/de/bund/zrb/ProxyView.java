package de.bund.zrb;

public interface ProxyView {
    // Status des Gateway-Clients aktualisieren (z.B. in Statusleiste anzeigen)
    void updateGatewayClientStatus(String text, boolean connected);

    // Aktueller Ziel-Host aus der UI (Toolbar)
    String getClientTargetHost();

    // Aktueller Ziel-Port aus der UI (Toolbar)
    int getClientTargetPort();

    // Gateway-Passkey aus der UI (Toolbar)
    default String getGatewayPasskey() {
        return "passkey1234";
    }
}
