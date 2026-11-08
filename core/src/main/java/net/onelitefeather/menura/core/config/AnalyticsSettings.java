package net.onelitefeather.menura.core.config;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;

/**
 * The analytics settings from the environment (change add-content-analytics). The time zone decides the publish hour and
 * weekday of the equal-age comparison and the weeks of the series comparison; it defaults to Europe/Berlin.
 *
 * @param timeZone the zone of the team; an unknown zone name stops the start with an IllegalArgumentException
 */
public record AnalyticsSettings(ZoneId timeZone) {

    public static final String TIME_ZONE_VARIABLE = "MENURA_ANALYTICS_TIME_ZONE";
    public static final ZoneId DEFAULT_TIME_ZONE = ZoneId.of("Europe/Berlin");

    public AnalyticsSettings {
        Objects.requireNonNull(timeZone, "timeZone");
    }

    /** The settings of the given environment; a missing or blank zone is the default. */
    public static AnalyticsSettings from(Map<String, String> environment) {
        String value = environment.get(TIME_ZONE_VARIABLE);
        if (value == null || value.isBlank()) {
            return new AnalyticsSettings(DEFAULT_TIME_ZONE);
        }
        try {
            return new AnalyticsSettings(ZoneId.of(value.trim()));
        } catch (DateTimeException unknown) {
            throw new IllegalArgumentException(TIME_ZONE_VARIABLE + " is not a known time zone", unknown);
        }
    }
}
