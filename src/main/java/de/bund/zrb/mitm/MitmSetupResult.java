package de.bund.zrb.mitm;

import java.io.File;

public class MitmSetupResult {

    public final boolean success;
    public final String log;
    public final File keystoreFile;

    public MitmSetupResult(boolean success, String log, File keystoreFile) {
        this.success = success;
        this.log = log;
        this.keystoreFile = keystoreFile;
    }
}
