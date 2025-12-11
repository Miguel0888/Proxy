package de.bund.zrb;

import java.io.*;

class MitmSetupService {

    private final ProxyConfigService configService = new ProxyConfigService();

    private static final String RESOURCE_CREATE_CA = "/ps/create-ca.ps1";
    private static final String RESOURCE_OPENAI_CERT = "/ps/create-openai-cert.ps1";

    MitmSetupResult runMitmSetup() throws IOException {
        File configDir = configService.getConfigDir();
        if (!configDir.exists() && !configDir.mkdirs()) {
            throw new IOException("Could not create config directory: " + configDir.getAbsolutePath());
        }

        StringBuilder log = new StringBuilder();

        File createCaPs1 = extractResourceIfMissing(
                RESOURCE_CREATE_CA,
                new File(configDir, "create-ca.ps1"),
                log
        );
        File openAiPs1 = extractResourceIfMissing(
                RESOURCE_OPENAI_CERT,
                new File(configDir, "create-openai-cert.ps1"),
                log
        );

        int r1 = runPowerShellScript(configDir, createCaPs1, log);
        log.append("create-ca.ps1 finished with exit code ").append(r1).append('\n');
        if (r1 != 0) {
            return new MitmSetupResult(false, log.toString(), null);
        }

        int r2 = runPowerShellScript(configDir, openAiPs1, log);
        log.append("create-openai-cert.ps1 finished with exit code ").append(r2).append('\n');
        if (r2 != 0) {
            return new MitmSetupResult(false, log.toString(), null);
        }

        File ks = new File(configDir, "myproxy.jks");
        if (!ks.exists()) {
            log.append("myproxy.jks not found after scripts.\n");
            return new MitmSetupResult(false, log.toString(), null);
        }

        log.append("myproxy.jks detected.\n");
        return new MitmSetupResult(true, log.toString(), ks);
    }

    private File extractResourceIfMissing(String resourcePath, File target, StringBuilder log) throws IOException {
        if (target.exists()) {
            return target;
        }

        InputStream in = ProxyControlFrame.class.getResourceAsStream(resourcePath);
        if (in == null) {
            throw new IOException("Resource not found: " + resourcePath);
        }

        log.append("Extract ").append(resourcePath).append(" -> ")
                .append(target.getAbsolutePath()).append('\n');

        FileOutputStream out = null;
        try {
            out = new FileOutputStream(target);
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } finally {
            closeQuietly(in);
            closeQuietly(out);
        }

        return target;
    }

    private int runPowerShellScript(File workingDir, File scriptFile, StringBuilder log) throws IOException {
        if (!scriptFile.exists()) {
            throw new IOException("Script not found: " + scriptFile.getAbsolutePath());
        }

        ProcessBuilder pb = new ProcessBuilder(
                "powershell.exe",
                "-ExecutionPolicy", "Bypass",
                "-File", scriptFile.getAbsolutePath()
        );
        pb.directory(workingDir);
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
            throw new IOException("Script execution interrupted: " + scriptFile.getName(), e);
        }

        return exitCode;
    }

    private void closeQuietly(Closeable c) {
        if (c == null) {
            return;
        }
        try {
            c.close();
        } catch (IOException ignored) {
            // Ignore
        }
    }
}

