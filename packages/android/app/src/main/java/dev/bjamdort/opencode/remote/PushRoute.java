package dev.bjamdort.opencode.remote;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** The same URL-safe UTF-8 server key used by the web app's sessionHref. */
final class PushRoute {
    static String href(String server, String session) {
        if (server == null || session == null || !session.matches("ses_[A-Za-z0-9_-]+")) return null;
        try {
            URI uri = new URI(server);
            if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null
                    || uri.getPort() == 0 || uri.getPort() > 65535) return null;
            String key = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(server.replaceAll("/+$", "").getBytes(StandardCharsets.UTF_8));
            return BundledSite.HOME.resolve("server/" + key + "/session/" + session).toString();
        } catch (URISyntaxException invalid) {
            return null;
        }
    }
}
