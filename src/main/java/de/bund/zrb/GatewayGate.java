package de.bund.zrb;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Coordinate the "gateway first, clients later" behavior.
 *
 * In gateway-required mode:
 * - the first authenticated gateway connection opens the gate
 * - other connections can await the gate without blocking accept()
 * - when the gateway disconnects, the gate can be reset
 *
 * In direct mode the gate is open from the start.
 */
final class GatewayGate {

    private final boolean gatewayRequired;
    private final AtomicBoolean masterReserved = new AtomicBoolean(false);
    private final AtomicReference<CountDownLatch> readyLatchRef =
            new AtomicReference<CountDownLatch>(new CountDownLatch(1));

    GatewayGate(boolean gatewayRequired) {
        this.gatewayRequired = gatewayRequired;
        if (!gatewayRequired) {
            readyLatchRef.get().countDown();
        }
    }

    boolean isGatewayRequired() {
        return gatewayRequired;
    }

    boolean isGateOpen() {
        return readyLatchRef.get().getCount() == 0;
    }

    boolean tryReserveMaster() {
        return masterReserved.compareAndSet(false, true);
    }

    void releaseMasterReservation() {
        masterReserved.set(false);
    }

    void openGate() {
        readyLatchRef.get().countDown();
    }

    void resetGate() {
        if (!gatewayRequired) {
            return;
        }
        readyLatchRef.set(new CountDownLatch(1));
    }

    boolean awaitGateOpen(long timeoutMillis) throws InterruptedException {
        CountDownLatch latch = readyLatchRef.get();
        if (timeoutMillis <= 0) {
            latch.await();
            return true;
        }
        return latch.await(timeoutMillis, TimeUnit.MILLISECONDS);
    }
}
