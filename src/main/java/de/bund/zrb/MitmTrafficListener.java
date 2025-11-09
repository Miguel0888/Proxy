package de.bund.zrb;

public interface MitmTrafficListener {

    // Log MITM traffic. direction e.g. "client->server" or "server->client".
    // If isJson is true, UI may pretty-print using JSON parser.
    void onTraffic(String direction, String text, boolean isJson);
}
