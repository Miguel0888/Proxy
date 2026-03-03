package de.bund.zrb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a proxy decision for a target URL/host.
 * Can contain multiple proxy candidates in fallback order.
 */
public final class ProxyInfo {

    private final List<ProxyCandidate> candidates;

    private ProxyInfo(List<ProxyCandidate> candidates) {
        this.candidates = Collections.unmodifiableList(new ArrayList<ProxyCandidate>(candidates));
    }

    public static ProxyInfo direct() {
        List<ProxyCandidate> list = new ArrayList<ProxyCandidate>();
        list.add(ProxyCandidate.direct());
        return new ProxyInfo(list);
    }

    public static ProxyInfo fromCandidates(List<ProxyCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return direct();
        }
        return new ProxyInfo(candidates);
    }

    public List<ProxyCandidate> getCandidates() {
        return candidates;
    }

    public boolean isDirect() {
        return candidates.size() == 1 && candidates.get(0).isDirect();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ProxyInfo[");
        for (int i = 0; i < candidates.size(); i++) {
            if (i > 0) {
                sb.append("; ");
            }
            sb.append(candidates.get(i));
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Represents a single proxy candidate (DIRECT, PROXY host:port, or SOCKS host:port).
     */
    public static final class ProxyCandidate {
        private final ProxyType type;
        private final String host;
        private final int port;

        private ProxyCandidate(ProxyType type, String host, int port) {
            this.type = type;
            this.host = host;
            this.port = port;
        }

        public static ProxyCandidate direct() {
            return new ProxyCandidate(ProxyType.DIRECT, null, 0);
        }

        public static ProxyCandidate httpProxy(String host, int port) {
            return new ProxyCandidate(ProxyType.HTTP, host, port);
        }

        public static ProxyCandidate socks(String host, int port) {
            return new ProxyCandidate(ProxyType.SOCKS, host, port);
        }

        public ProxyType getType() {
            return type;
        }

        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }

        public boolean isDirect() {
            return type == ProxyType.DIRECT;
        }

        @Override
        public String toString() {
            if (type == ProxyType.DIRECT) {
                return "DIRECT";
            }
            return type + " " + host + ":" + port;
        }
    }

    public enum ProxyType {
        DIRECT,
        HTTP,
        SOCKS
    }
}

