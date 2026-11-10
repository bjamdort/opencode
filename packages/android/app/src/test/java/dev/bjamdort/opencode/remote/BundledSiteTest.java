package dev.bjamdort.opencode.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class BundledSiteTest {
    @Test
    public void localNavigationsAlwaysLoadTheBundledAppEntry() {
        String[] paths = {"", "index.html", "settings/appearance", "server/project/session", "?theme=dark"};
        for (String path : paths) {
            assertEquals(BundledSite.HOME.resolve("index.html"), BundledSite.asset(BundledSite.HOME + path, true));
        }
    }

    @Test
    public void subresourcesKeepTheirAssetPathInsteadOfReceivingHtml() {
        String[] paths = {"_assets/index.js", "_assets/worker.js", "_assets/font.woff2", "_assets/engine.wasm", "missing.js"};
        for (String path : paths) {
            assertEquals(BundledSite.HOME.resolve(path), BundledSite.asset(BundledSite.HOME + path, false));
        }
    }

    @Test
    public void remoteApisAndOtherOriginsNeverReceiveBundledAssets() {
        String[] addresses = {
            "http://100.101.102.103:49374/api/info", "https://laptop.example.ts.net/api/info",
            "http://localhost:49374/api/info", "https://localhost:49373/",
            "http://localhost:49373.attacker.example/", "file:///index.html", "javascript:alert(1)", "not a URL"
        };
        for (String address : addresses) {
            assertNull(address, BundledSite.asset(address, true));
            assertNull(address, BundledSite.asset(address, false));
        }
    }
}
