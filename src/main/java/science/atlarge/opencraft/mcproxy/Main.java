package science.atlarge.opencraft.mcproxy;

import java.util.Map;

public class Main {
    public static void main(String[] args) {
        Map<String, String> envVars = System.getenv();
        String targetAddress = envVars.getOrDefault("TARGET_ADDRESS", "127.0.0.1");
        int targetPort = Integer.parseInt(envVars.getOrDefault("TARGET_PORT", "25565"));
        int proxyPort = Integer.parseInt(envVars.getOrDefault("PROXY_PORT", "25566"));

        ProxyServer proxy = new ProxyServer(targetAddress, targetPort, proxyPort);
        proxy.start();
    }
}
