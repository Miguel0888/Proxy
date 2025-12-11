package de.bund.zrb;

class CaInstallResult {
    final boolean success;
    final String log;
    final int exitCode;

    CaInstallResult(boolean success, String log, int exitCode) {
        this.success = success;
        this.log = log;
        this.exitCode = exitCode;
    }
}

