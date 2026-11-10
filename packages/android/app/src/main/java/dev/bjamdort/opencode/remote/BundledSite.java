package dev.bjamdort.opencode.remote;

import java.net.URI;
import java.net.URISyntaxException;

/** Maps only the embedded site's requests to APK assets, with an SPA navigation fallback. */
final class BundledSite {
    static final URI HOME = URI.create("http://localhost:49373/");

    static URI asset(String request, boolean navigation) {
        try {
            URI uri = new URI(request);
            if (!HOME.getScheme().equals(uri.getScheme()) || !HOME.getAuthority().equals(uri.getAuthority())) return null;
            return navigation ? HOME.resolve("index.html") : uri;
        } catch (URISyntaxException invalid) {
            return null;
        }
    }
}
