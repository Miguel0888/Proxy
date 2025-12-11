package de.bund.zrb;

public interface ProxyView {
    // Status des Gateway-Clients aktualisieren (z.B. in Statusleiste anzeigen)
    void updateGatewayClientStatus(String text, boolean connected);
}
