package de.bund.zrb;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class SocketPipeTask implements Runnable {

    private final Socket source;
    private final Socket target;

    public SocketPipeTask(Socket source, Socket target) {
        this.source = source;
        this.target = target;
    }

    @Override
    public void run() {
        byte[] buffer = new byte[8192];
        try {
            InputStream in = source.getInputStream();
            OutputStream out = target.getOutputStream();
            int read;
            while (!source.isClosed()
                    && !target.isClosed()
                    && (read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                out.flush();
            }
        } catch (IOException ignored) {
            // Ignore, connection closed
        }
    }
}
