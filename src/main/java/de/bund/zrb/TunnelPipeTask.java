package de.bund.zrb;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class TunnelPipeTask implements Runnable {

    private final InputStream in;
    private final OutputStream out;

    public TunnelPipeTask(InputStream in, OutputStream out) {
        this.in = in;
        this.out = out;
    }

    @Override
    public void run() {
        byte[] buffer = new byte[8192];
        int read;
        try {
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                out.flush();
            }
        } catch (IOException ignored) {
            // Connection closed, stop piping
        }
    }
}
