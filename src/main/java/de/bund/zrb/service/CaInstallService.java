package de.bund.zrb.service;

import de.bund.zrb.mitm.CaInstallResult;

import java.io.*;

public class CaInstallService {

    private final ProxyConfigService configService = new ProxyConfigService();

    private static final String CA_CERT_FILE_NAME = "myproxy-ca.crt";

    public File getCaFile() {
        return new File(configService.getConfigDir(), CA_CERT_FILE_NAME);
    }

    public CaInstallResult installCa(File caFile) throws IOException {
        StringBuilder log = new StringBuilder();

        ProcessBuilder pb = new ProcessBuilder(
                "certutil",
                "-addstore",
                "-f",
                "Root",
                caFile.getAbsolutePath()
        );
        pb.redirectErrorStream(true);

        Process process = pb.start();

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), "UTF-8")
        );

        String line;
        while ((line = reader.readLine()) != null) {
            log.append(line).append("\n");
        }

        int exitCode;
        try {
            exitCode = process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("certutil interrupted", e);
        }

        boolean success = exitCode == 0;
        return new CaInstallResult(success, log.toString(), exitCode);
    }
}

