package net.onelitefeather.menura.core.attribution;

import net.onelitefeather.menura.core.problem.ProblemException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The payload checks at the boundary: the fixed shape of a join event and a source answer, and the refusal of personal
 * data. A refused value never appears in the refusal.
 */
class JoinPayloadsTest {

    private static final String EVENT_ID = "3f1c2b7e-8d4a-4c6f-9e21-5b7a0d4c8e10";
    private static final String JOINED_AT = "2026-11-08T11:30:00Z";
    private static final String PSEUDONYM = "c".repeat(64);

    @Test
    void aJoinEventWithTheFixedFieldsIsAccepted() {
        JoinPayloads.EventInput input = JoinPayloads.event(JoinFixtureBodies.event());

        assertEquals(JoinFixtureBodies.UUID_OF_EVENT, input.eventId(), "the event id is read as a UUID");
        assertTrue(input.firstJoin(), "the first-join flag is read");
        assertEquals(PSEUDONYM, input.pseudonym(), "the pseudonym is kept as it was sent");
    }

    @Test
    void aPayloadWithAPlayerUuidIsRefusedAndTheUuidIsNotRepeated() {
        Map<String, Object> body = JoinFixtureBodies.event();
        body.put("uuid", "069a79f4-44e9-4726-a5be-fca90e38aaf5");

        ProblemException refused = assertThrows(ProblemException.class, () -> JoinPayloads.event(body));

        assertEquals(422, refused.status(), "personal data is refused as unprocessable");
        assertTrue(refused.detail().contains("uuid"), "the refusal names the field");
        assertFalse(refused.toString().contains("069a79f4"), "the refused value is not repeated");
    }

    @Test
    void aPayloadWithAPlayerNameIsRefused() {
        Map<String, Object> body = JoinFixtureBodies.event();
        body.put("username", "Steve_Example");

        ProblemException refused = assertThrows(ProblemException.class, () -> JoinPayloads.event(body));

        assertEquals("Personal data refused", refused.title(), "a player name is refused as personal data");
        assertFalse(refused.detail().contains("Steve_Example"), "the name is not repeated");
    }

    @Test
    void aPayloadWithAnAddressFieldIsRefusedUnderAnyCasing() {
        Map<String, Object> body = JoinFixtureBodies.event();
        body.put("client_address", "203.0.113.7");

        ProblemException refused = assertThrows(ProblemException.class, () -> JoinPayloads.event(body));

        assertEquals("Personal data refused", refused.title(), "an address field is refused");
        assertFalse(refused.detail().contains("203.0.113.7"), "the address is not repeated");
    }

    @Test
    void anIpAddressUnderAnInnocentNameIsRefusedByItsShape() {
        Map<String, Object> body = JoinFixtureBodies.event();
        body.put("pseudonym", "203.0.113.7");

        ProblemException refused = assertThrows(ProblemException.class, () -> JoinPayloads.event(body));

        assertEquals(422, refused.status(), "a value that is not a keyed hash is refused");
        assertFalse(refused.detail().contains("203.0.113.7"), "the value is not repeated");
    }

    @Test
    void anyOtherFieldIsRefused() {
        Map<String, Object> body = JoinFixtureBodies.event();
        body.put("server", "lobby-1");

        ProblemException refused = assertThrows(ProblemException.class, () -> JoinPayloads.event(body));

        assertEquals("Unknown field", refused.title(), "a field outside the fixed shape is refused");
    }

    @Test
    void aMissingOrMistypedFieldIsRefused() {
        Map<String, Object> body = JoinFixtureBodies.event();
        body.put("firstJoin", "yes");

        assertEquals(422, assertThrows(ProblemException.class, () -> JoinPayloads.event(body)).status(),
                "the first-join flag must be a boolean");
        assertEquals(422, assertThrows(ProblemException.class, () -> JoinPayloads.event(null)).status(),
                "a body that is not an object is refused");
    }

    @Test
    void aSourceAnswerNamingAFixedChoiceIsAccepted() {
        assertEquals(StatedSource.TWITCH, JoinPayloads.answer(Map.of("source", "twitch")),
                "a fixed choice is read as its source");
    }

    @Test
    void aSourceOutsideTheChoicesIsRefusedWith422() {
        ProblemException refused = assertThrows(ProblemException.class,
                () -> JoinPayloads.answer(Map.of("source", "reddit")));

        assertEquals(422, refused.status(), "a value outside the choices is refused");
    }

    @Test
    void freeTextInAnAnswerIsRefusedAndTheTextIsNotRepeated() {
        Map<String, Object> body = new HashMap<>();
        body.put("source", "friends");
        body.put("text", "a friend told me");

        ProblemException refused = assertThrows(ProblemException.class, () -> JoinPayloads.answer(body));

        assertEquals(422, refused.status(), "free text is refused");
        assertFalse(refused.toString().contains("a friend told me"), "the text is not repeated");
    }

    /** Bodies as the proxy sends them. */
    private static final class JoinFixtureBodies {

        static final java.util.UUID UUID_OF_EVENT = java.util.UUID.fromString(EVENT_ID);

        static Map<String, Object> event() {
            Map<String, Object> body = new HashMap<>();
            body.put("eventId", EVENT_ID);
            body.put("firstJoin", true);
            body.put("joinedAt", JOINED_AT);
            body.put("pseudonym", PSEUDONYM);
            return body;
        }
    }
}
