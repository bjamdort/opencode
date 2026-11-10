package dev.bjamdort.opencode.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class PushRouteTest {
    @Test
    public void matchesTheWebAppsUrlSafeUnpaddedServerKey() {
        assertEquals("http://localhost:49373/server/aHR0cHM6Ly9uYXMuZXhhbXBsZS5jb20/session/ses_abc123",
                PushRoute.href("https://nas.example.com/", "ses_abc123"));
    }

    @Test
    public void rejectsNonHttpServersCredentialsAndInvalidSessionPaths() {
        for (String server : new String[]{null, "file:///private", "javascript:alert(1)", "https://user:secret@nas/", "https://nas/?secret=1", "https://nas/#secret", "http:///missing", "http://nas:0", "http://nas:65536"}) {
            assertNull(PushRoute.href(server, "ses_valid"));
        }
        for (String session : new String[]{null, "", "other_id", "ses_a/b", "ses_a?x=1", "ses_../settings"}) {
            assertNull(PushRoute.href("https://nas/", session));
        }
    }
}
