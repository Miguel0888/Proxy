package de.bund.zrb;

import java.io.File;

class MitmSetupResult {

    final boolean success;
    final String log;
    final File keystoreFile;

    MitmSetupResult(boolean success, String log, File keystoreFile) {
        this.success = success;
        this.log = log;
        this.keystoreFile = keystoreFile;
    }
}
