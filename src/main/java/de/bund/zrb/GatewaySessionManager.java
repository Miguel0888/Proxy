package de.bund.zrb;

import java.io.IOException;

public class GatewaySessionManager {

    private volatile GatewaySession activeSession;

    /**
     * Set active gateway session.
     */
    public synchronized void setActiveSession(GatewaySession session) {
        this.activeSession = session;
    }

    /**
     * Clear active session if it matches given instance.
     */
    public synchronized void clearActiveSession(GatewaySession session) {
        if (this.activeSession == session) {
            this.activeSession = null;
        }
    }

    /**
     * Get current active session or null.
     */
    public GatewaySession getActiveSession() {
        return activeSession;
    }

    /**
     * Get active session or throw IOException if none is available.
     */
    public GatewaySession getActiveSessionOrThrow() throws IOException {
        GatewaySession session = activeSession;
        if (session == null || !session.isAlive()) {
            throw new IOException("No active gateway session available");
        }
        return session;
    }
}
