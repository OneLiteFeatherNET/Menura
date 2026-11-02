package net.onelitefeather.menura.server.audit;

import io.micronaut.http.HttpRequest;
import net.onelitefeather.menura.core.id.IdentifierGenerator;
import net.onelitefeather.menura.server.support.DeterministicRandom;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestIdsTest {

    private final IdentifierGenerator identifiers = new IdentifierGenerator(new DeterministicRandom());

    @Test
    void wellFormedRequestIdHeaderIsKept() {
        HttpRequest<?> request = HttpRequest.GET("/x").header(RequestIds.HEADER, "req-1_A.2");

        assertEquals("req-1_A.2", RequestIds.from(request, identifiers));
    }

    @Test
    void missingRequestIdGetsAFreshIdentifier() {
        String id = RequestIds.from(HttpRequest.GET("/x"), identifiers);

        assertTrue(id.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"),
                "a generated request id is a version 4 UUID, but was " + id);
    }

    @Test
    void overlongOrOddRequestIdIsReplacedNotStored() {
        String odd = "x".repeat(65);
        HttpRequest<?> request = HttpRequest.GET("/x").header(RequestIds.HEADER, odd);

        assertNotEquals(odd, RequestIds.from(request, identifiers), "a request id that is too long must not be stored");
    }

    @Test
    void requestIdWithSpacesOrPunctuationIsReplaced() {
        HttpRequest<?> request = HttpRequest.GET("/x").header(RequestIds.HEADER, "a b;c");

        assertNotEquals("a b;c", RequestIds.from(request, identifiers),
                "a request id with spaces or punctuation must not reach the audit log");
    }
}
