package dev.bjamdort.opencode.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ServerAddressTest {
    @Test
    public void acceptsTailscaleAddressesAndPairingLinks() {
        String[] addresses = {
            "http://100.101.102.103:49374/",
            "https://laptop.example.ts.net",
            "http://desktop:49374/auth/connect/one-time-code",
            "http://[fd7a:115c:a1e0::1]:49374/"
        };
        for (String address : addresses) {
            assertEquals(address, ServerAddress.parse(" " + address + " ").toString());
        }
    }

    @Test
    public void rejectsLocalSchemesCredentialsAndMalformedAddresses() {
        String[] addresses = {
            "javascript:alert(1)", "file:///etc/passwd", "intent://laptop",
            "data:text/html,test", "laptop:49374", "http:///missing-host",
            "https://user:secret@laptop", "http://laptop:0", "http://laptop:65536",
            "http://a host/", ""
        };
        for (String address : addresses) {
            assertThrows(address, IllegalArgumentException.class, () -> ServerAddress.parse(address));
        }
    }

    @Test
    public void savedHomeDoesNotContainPairingSecretsOrSessionPaths() {
        assertEquals("http://laptop:49374/", ServerAddress.home(
                ServerAddress.parse("http://laptop:49374/auth/connect/secret?token=secret#secret")));
        assertEquals("https://desktop/", ServerAddress.home(ServerAddress.parse("https://desktop/project/session")));
    }

    @Test
    public void navigationStaysWithinTheSelectedOrigin() {
        assertTrue(ServerAddress.sameOrigin(ServerAddress.parse("https://LAPTOP/"), ServerAddress.parse("https://laptop:443/session")));
        String[] external = {"http://laptop/", "https://laptop:8443/", "https://desktop/", "https://laptop.attacker.example/"};
        for (String address : external) {
            assertFalse(address, ServerAddress.sameOrigin(ServerAddress.parse("https://laptop/"), ServerAddress.parse(address)));
        }
    }
}
