package de.bund.zrb.common;

public interface ProxyView {
    // Status des Gateway-Clients aktualisieren (z.B. in Statusleiste anzeigen)
    void updateGatewayClientStatus(String text, boolean connected);

    // SERVER-spezifische Werte (lokaler Proxy)
    int getServerPort();
    String getServerGatewayPasskey();

    // CLIENT-spezifische Zielwerte (Remote-Gateway)
    String getClientTargetHost();
    int getClientTargetPort();
    String getClientGatewayPasskey();
}
