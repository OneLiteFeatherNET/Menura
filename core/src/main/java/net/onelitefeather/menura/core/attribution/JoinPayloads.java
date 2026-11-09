package net.onelitefeather.menura.core.attribution;

import net.onelitefeather.menura.core.problem.ProblemException;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Reads the bodies the network proxy sends and refuses anything that is not exactly the fixed shape (change
 * add-join-attribution, principle 5). The check runs before anything is stored, and a refusal is a 422 Problem Details
 * error that names the field and never repeats its value, so no personal data reaches a log or an error response.
 *
 * <p>A join event has exactly the fields {@code eventId}, {@code firstJoin}, {@code joinedAt} and {@code pseudonym}. A
 * source answer has exactly {@code source}. Any other field is refused; a field whose name points at personal data (a
 * UUID, a name or an address) is refused with its own message. The values are shape-checked as well: the pseudonym must be
 * 64 lower-case hex digits, so a name or an address cannot pass as one.
 */
public final class JoinPayloads {

    static final Set<String> EVENT_FIELDS = Set.of("eventId", "firstJoin", "joinedAt", "pseudonym");
    static final Set<String> ANSWER_FIELDS = Set.of("source");

    /** Field names, compared without case and separators, that name personal data. */
    static final Set<String> PERSONAL_DATA_NAMES = Set.of("uuid", "playeruuid", "username", "playername", "name",
            "player", "ip", "ipaddress", "address", "clientaddress", "clientip", "remoteaddress", "remoteip",
            "playerip", "useruuid");

    private static final Pattern PSEUDONYM = Pattern.compile("[0-9a-f]{64}");

    private JoinPayloads() {
    }

    /** One join event as the proxy sent it, checked but not yet stored. */
    public record EventInput(UUID eventId, boolean firstJoin, Instant joinedAt, String pseudonym) {
    }

    /** Checks a join event body. */
    public static EventInput event(Map<String, Object> body) {
        requireObject(body);
        refuseUnknownFields(body, EVENT_FIELDS);
        UUID eventId = uuid(body, "eventId");
        boolean firstJoin = bool(body, "firstJoin");
        Instant joinedAt = instant(body, "joinedAt");
        String pseudonym = text(body, "pseudonym");
        if (!PSEUDONYM.matcher(pseudonym).matches()) {
            throw invalid("pseudonym");
        }
        return new EventInput(eventId, firstJoin, joinedAt, pseudonym);
    }

    /** Checks a source answer body and returns the choice it names. */
    public static StatedSource answer(Map<String, Object> body) {
        requireObject(body);
        refuseUnknownFields(body, ANSWER_FIELDS);
        String value = text(body, "source");
        return StatedSource.parse(value).orElseThrow(() -> new ProblemException(422, "Invalid source",
                "The source must be one of youtube, tiktok, instagram, twitch, friends or other."));
    }

    private static void requireObject(Map<String, Object> body) {
        if (body == null) {
            throw new ProblemException(422, "Invalid payload", "The body must be a JSON object.");
        }
    }

    private static void refuseUnknownFields(Map<String, Object> body, Set<String> allowed) {
        for (String field : body.keySet()) {
            if (allowed.contains(field)) {
                continue;
            }
            if (PERSONAL_DATA_NAMES.contains(field.replace("_", "").toLowerCase(Locale.ROOT))) {
                throw new ProblemException(422, "Personal data refused",
                        "The payload carries personal data in the field " + field + " and is refused. Nothing was stored.");
            }
            throw new ProblemException(422, "Unknown field", "The field " + field + " is not allowed.");
        }
    }

    private static String text(Map<String, Object> body, String field) {
        Object value = body.get(field);
        if (!(value instanceof String string) || string.isBlank()) {
            throw invalid(field);
        }
        return string;
    }

    private static UUID uuid(Map<String, Object> body, String field) {
        try {
            return UUID.fromString(text(body, field));
        } catch (IllegalArgumentException notAUuid) {
            throw invalid(field);
        }
    }

    private static Instant instant(Map<String, Object> body, String field) {
        try {
            return Instant.parse(text(body, field));
        } catch (DateTimeParseException notATime) {
            throw invalid(field);
        }
    }

    private static boolean bool(Map<String, Object> body, String field) {
        Object value = body.get(field);
        if (!(value instanceof Boolean flag)) {
            throw invalid(field);
        }
        return flag;
    }

    private static ProblemException invalid(String field) {
        return new ProblemException(422, "Invalid payload", "The field " + field + " is missing or not valid.");
    }
}
