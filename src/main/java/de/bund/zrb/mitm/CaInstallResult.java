package de.bund.zrb.mitm;

public class CaInstallResult {
    public final boolean success;
    public final String log;
    public final int exitCode;

    public CaInstallResult(boolean success, String log, int exitCode) {
        this.success = success;
        this.log = log;
        this.exitCode = exitCode;
    }
}

