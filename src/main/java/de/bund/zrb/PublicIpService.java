package de.bund.zrb;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;

class PublicIpService {

    String resolvePublicIp() {
        BufferedReader reader = null;
        try {
            URL url = new URL("https://checkip.amazonaws.com/");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            connection.setRequestMethod("GET");
            int code = connection.getResponseCode();
            if (code == 200) {
                reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
                String line = reader.readLine();
                if (line != null) {
                    return line.trim();
                }
            }
        } catch (IOException e) {
            // Ignore and fall back
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                    // Ignore
                }
            }
        }

        try {
            InetAddress local = InetAddress.getLocalHost();
            return local.getHostAddress();
        } catch (IOException e) {
            // Ignore
        }

        return "unknown";
    }
}

