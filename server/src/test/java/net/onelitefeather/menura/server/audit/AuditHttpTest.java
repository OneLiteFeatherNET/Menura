package net.onelitefeather.menura.server.audit;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import net.onelitefeather.menura.server.security.HeaderCurrentPerson;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(rebuildContext = true)
class AuditHttpTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    InMemoryAuditRepository audit;

    @Test
    void writeEndpointRecordsOneEntryForTheSignedInPersonAndChannel() {
        UUID person = UUID.randomUUID();

        HttpResponse<Void> response = client.toBlocking().exchange(HttpRequest.POST("/test-audit/write", "")
                .header(HeaderCurrentPerson.PERSON_HEADER, person.toString())
                .header(HeaderCurrentPerson.CHANNEL_HEADER, "mcp")
                .header(RequestIds.HEADER, "req-http-1"));

        assertEquals(204, response.code());
        assertEquals(1, audit.entries().size(), "the write must produce exactly one audit entry");
        AuditEntry entry = audit.entries().get(0);
        assertEquals(person, entry.personId());
        assertEquals(Channel.MCP, entry.channel());
        assertEquals("req-http-1", entry.requestId());
    }

    @Test
    void anonymousWriteRecordsNothing() {
        HttpClientResponseException refused = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(HttpRequest.POST("/test-audit/write", "")));

        assertEquals(401, refused.getStatus().getCode(), "an anonymous write is refused before it runs");
        assertTrue(audit.entries().isEmpty(), "a refused write must leave no audit entry");
    }
}
