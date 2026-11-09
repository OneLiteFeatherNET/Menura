package net.onelitefeather.menura.server.attribution;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.read.ListAppender;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import net.onelitefeather.menura.core.attribution.support.InMemoryJoinStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A refused join payload leaves no trace of its values in the logs: not in a record of the attribution code, not in a
 * stack trace, and not in the framework's own request or error logging. The root logger is captured at TRACE, so records of
 * every level are seen. Each test starts from an empty store and an empty capture, and the root level is restored after it.
 */
@MicronautTest(rebuildContext = true)
class JoinLogRedactionHttpTest {

    private static final String PROXY_CREDENTIAL = "test-join-proxy-credential";
    private static final String PROXY = "Bearer " + PROXY_CREDENTIAL;
    private static final String PSEUDONYM = "e".repeat(64);
    private static final String PLAYER_UUID = "069a79f4-44e9-4726-a5be-fca90e38aaf5";
    private static final String PLAYER_NAME = "PlayerZelda42";
    private static final String CLIENT_ADDRESS = "198.51.100.23";
    private static final String FREE_TEXT = "my cousin in the Discord told me";
    private static final Logger ROOT = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    /** The test's own HTTP client logs the bodies it sends; those are not server logs, so this logger stays at INFO. */
    private static final Logger TEST_CLIENT = (Logger) LoggerFactory.getLogger("io.micronaut.http.client");

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    InMemoryJoinStore store;

    @Inject
    Clock clock;

    private ListAppender<ILoggingEvent> logs;
    private Level previousLevel;
    private Level previousClientLevel;

    @BeforeEach
    void captureRecordsOfEveryLevel() {
        store.clear();
        previousLevel = ROOT.getLevel();
        previousClientLevel = TEST_CLIENT.getLevel();
        TEST_CLIENT.setLevel(Level.INFO);
        ROOT.setLevel(Level.TRACE);
        logs = new ListAppender<>();
        // Server threads may still log while a test reads the records, so the list must be safe to iterate concurrently.
        logs.list = new CopyOnWriteArrayList<>();
        logs.start();
        ROOT.addAppender(logs);
    }

    @AfterEach
    void stopCapture() {
        ROOT.detachAppender(logs);
        logs.stop();
        ROOT.setLevel(previousLevel);
        TEST_CLIENT.setLevel(previousClientLevel);
    }

    @Test
    void theCaptureSeesARecordLoggedAtTraceLevel() {
        LoggerFactory.getLogger(JoinLogRedactionHttpTest.class).trace("capture probe");

        assertTrue(renderedLogs().contains("capture probe"), "the appender sees trace records of the root logger");
    }

    @Test
    void aRefusedPlayerUuidAppearsInNoLogRecord() {
        Reply refused = send(proxy(HttpRequest.POST("/join-events", eventWith("\"uuid\":\"" + PLAYER_UUID + "\""))));

        assertEquals(422, refused.status(), "a payload with a player UUID is refused");
        assertNoLogRecordContains(PLAYER_UUID);
    }

    @Test
    void aRefusedPlayerNameAppearsInNoLogRecord() {
        Reply refused = send(proxy(HttpRequest.POST("/join-events", eventWith("\"username\":\"" + PLAYER_NAME + "\""))));

        assertEquals(422, refused.status(), "a payload with a player name is refused");
        assertNoLogRecordContains(PLAYER_NAME);
    }

    @Test
    void aRefusedClientAddressAppearsInNoLogRecord() {
        Reply refused = send(proxy(HttpRequest.POST("/join-events",
                eventWith("\"client_address\":\"" + CLIENT_ADDRESS + "\""))));

        assertEquals(422, refused.status(), "a payload with an IP address is refused");
        assertNoLogRecordContains(CLIENT_ADDRESS);
    }

    @Test
    void aMalformedPayloadWithAPlayerNameAppearsInNoLogRecord() {
        String malformed = "{\"eventId\":\"" + UUID.randomUUID() + "\",\"firstJoin\":true,\"username\":" + PLAYER_NAME + "}";

        Reply refused = send(proxy(HttpRequest.POST("/join-events", malformed)));

        assertTrue(refused.status() >= 400 && refused.status() < 500, "a body that is not JSON is refused: " + refused.status());
        assertNoLogRecordContains(PLAYER_NAME);
    }

    @Test
    void freeTextInAnAnswerAppearsInNoLogRecord() {
        UUID join = UUID.randomUUID();
        Reply created = send(proxy(HttpRequest.POST("/join-events", eventWithId(join))));
        assertEquals(201, created.status(), "the join the answer refers to is stored first");

        Reply refused = send(proxy(HttpRequest.POST("/join-events/" + join + "/source-answer",
                "{\"source\":\"youtube\",\"text\":\"" + FREE_TEXT + "\"}")));

        assertEquals(422, refused.status(), "an answer with free text is refused");
        assertNoLogRecordContains(FREE_TEXT);
    }

    @Test
    void freeTextAsTheSourceValueAppearsInNoLogRecord() {
        UUID join = UUID.randomUUID();
        Reply created = send(proxy(HttpRequest.POST("/join-events", eventWithId(join))));
        assertEquals(201, created.status(), "the join the answer refers to is stored first");

        Reply refused = send(proxy(HttpRequest.POST("/join-events/" + join + "/source-answer",
                "{\"source\":\"" + FREE_TEXT + "\"}")));

        assertEquals(422, refused.status(), "a source that is not one of the choices is refused");
        assertNoLogRecordContains(FREE_TEXT);
    }

    @Test
    void theProxyCredentialAppearsInNoLogRecord() {
        send(proxy(HttpRequest.POST("/join-events", eventWith("\"uuid\":\"" + PLAYER_UUID + "\""))));

        assertNoLogRecordContains(PROXY_CREDENTIAL);
    }

    private String eventWith(String extraField) {
        return "{" + eventFields(UUID.randomUUID()) + "," + extraField + "}";
    }

    private String eventWithId(UUID id) {
        return "{" + eventFields(id) + "}";
    }

    private String eventFields(UUID id) {
        return "\"eventId\":\"" + id + "\",\"firstJoin\":true,\"joinedAt\":\"" + clock.instant() + "\",\"pseudonym\":\""
                + PSEUDONYM + "\"";
    }

    private void assertNoLogRecordContains(String value) {
        assertFalse(renderedLogs().contains(value), "a refused value reached a log record");
    }

    /** Every captured record as text: logger, formatted message, MDC values, and each throwable of its cause chain. */
    private String renderedLogs() {
        StringBuilder text = new StringBuilder();
        for (ILoggingEvent event : logs.list) {
            text.append(event.getLoggerName()).append(' ').append(event.getFormattedMessage()).append('\n');
            event.getMDCPropertyMap().values().forEach(value -> text.append(value).append('\n'));
            for (IThrowableProxy proxy = event.getThrowableProxy(); proxy != null; proxy = proxy.getCause()) {
                text.append(proxy.getClassName()).append(": ").append(proxy.getMessage()).append('\n');
                for (StackTraceElementProxy frame : proxy.getStackTraceElementProxyArray()) {
                    text.append(frame.getSTEAsString()).append('\n');
                }
            }
        }
        return text.toString();
    }

    private static MutableHttpRequest<?> proxy(MutableHttpRequest<?> request) {
        return request.header("Authorization", PROXY).contentType(MediaType.APPLICATION_JSON_TYPE);
    }

    private Reply send(MutableHttpRequest<?> request) {
        request.contentType(MediaType.APPLICATION_JSON_TYPE);
        try {
            HttpResponse<String> response = client.toBlocking().exchange(request, String.class);
            return new Reply(response.code(), response.body());
        } catch (HttpClientResponseException refused) {
            return new Reply(refused.getStatus().getCode(), refused.getResponse().getBody(String.class).orElse(""));
        }
    }

    private record Reply(int status, String body) {
    }
}
