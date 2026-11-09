package net.onelitefeather.menura.core.attribution;

import net.onelitefeather.menura.core.problem.ProblemException;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The installation's join domain and attribution window. The domain is one lower-case host name shared by all channels;
 * the window is how long before a join a post may still count for it (72 hours unless an admin sets another value).
 *
 * @param joinDomain the domain, or null when none is configured
 * @param updatedAt  when an admin last saved the settings, or null when they were never saved
 * @param updatedBy  the admin who saved them, or null
 */
public record JoinSettings(String joinDomain, int attributionWindowHours, Instant updatedAt, UUID updatedBy) {

    public static final int DEFAULT_WINDOW_HOURS = 72;
    public static final int MAX_WINDOW_HOURS = 720;

    private static final int MAX_DOMAIN = 253;
    private static final Pattern HOST_NAME = Pattern.compile(
            "[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)*");

    public JoinSettings {
        if (attributionWindowHours < 1 || attributionWindowHours > MAX_WINDOW_HOURS) {
            throw new IllegalArgumentException("the attribution window must be between 1 and 720 hours");
        }
    }

    /** The settings before any admin saved them: no domain and the default window. */
    public static JoinSettings unset() {
        return new JoinSettings(null, DEFAULT_WINDOW_HOURS, null, null);
    }

    /** The attribution window as a duration. */
    public Duration window() {
        return Duration.ofHours(attributionWindowHours);
    }

    /**
     * Checks a domain a caller sent. It must be a lower-case host name, with no scheme, port, path or upper-case letter.
     * A refusal is a 422 Problem Details error; the value is not repeated in it.
     */
    public static String checkedDomain(String domain) {
        if (domain == null || domain.length() > MAX_DOMAIN || !HOST_NAME.matcher(domain).matches()) {
            throw new ProblemException(422, "Invalid join domain",
                    "The join domain must be one lower-case host name, without scheme, port or path.");
        }
        return domain;
    }

    /** Checks a window a caller sent: a whole number of hours from 1 to 720. */
    public static int checkedWindowHours(int hours) {
        if (hours < 1 || hours > MAX_WINDOW_HOURS) {
            throw new ProblemException(422, "Invalid attribution window",
                    "The attribution window must be between 1 and 720 hours.");
        }
        return hours;
    }
}
