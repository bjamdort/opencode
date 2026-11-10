package dev.bjamdort.opencode.remote;

import java.net.URI;
import java.net.URISyntaxException;

/** Validates user input before it reaches WebView or persistent storage. */
final class ServerAddress {
    static URI parse(String input) {
        try {
            URI uri = new URI(input.trim());
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getPort() == 0 || uri.getPort() > 65535) {
                throw new IllegalArgumentException("Invalid server address");
            }
            return uri;
        } catch (URISyntaxException error) {
            throw new IllegalArgumentException("Invalid server address", error);
        }
    }

    // Pairing codes and session paths are used once, never saved in preferences.
    static String home(URI uri) {
        return uri.resolve("/").toString();
    }

    static boolean sameOrigin(URI server, URI target) {
        return server.getScheme().equalsIgnoreCase(target.getScheme())
                && server.getHost().equalsIgnoreCase(target.getHost())
                && port(server) == port(target);
    }

    private static int port(URI uri) {
        if (uri.getPort() != -1) return uri.getPort();
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }
}
