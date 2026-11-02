package net.onelitefeather.menura.server.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PkceTest {

    @Test
    void challengeMatchesTheS256ExampleOfRfc7636() {
        // RFC 7636, appendix B.
        String verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";

        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", Pkce.s256Challenge(verifier));
    }

    @Test
    void generatedVerifierUsesTheUnreservedCharactersWithinRfcLength() {
        String verifier = Pkce.newVerifier();

        assertTrue(verifier.length() >= 43 && verifier.length() <= 128,
                "RFC 7636 allows 43 to 128 characters, but was " + verifier.length());
        assertTrue(verifier.matches("[A-Za-z0-9._~-]+"), "verifier must use only unreserved characters");
    }
}
