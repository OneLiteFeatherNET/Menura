package net.onelitefeather.menura.core.config;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AnalyticsSettingsTest {

    @Test
    void theTimeZoneDefaultsToEuropeBerlin() {
        assertEquals(ZoneId.of("Europe/Berlin"), AnalyticsSettings.from(Map.of()).timeZone(),
                "without MENURA_ANALYTICS_TIME_ZONE the team's zone is used");
    }

    @Test
    void aBlankTimeZoneFallsBackToTheDefault() {
        assertEquals(ZoneId.of("Europe/Berlin"),
                AnalyticsSettings.from(Map.of("MENURA_ANALYTICS_TIME_ZONE", "  ")).timeZone(),
                "a blank value is the same as no value");
    }

    @Test
    void theTimeZoneIsReadFromTheEnvironment() {
        assertEquals(ZoneId.of("UTC"), AnalyticsSettings.from(Map.of("MENURA_ANALYTICS_TIME_ZONE", "UTC")).timeZone(),
                "a configured zone replaces the default");
    }

    @Test
    void anUnknownTimeZoneIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> AnalyticsSettings.from(Map.of("MENURA_ANALYTICS_TIME_ZONE", "Mars/Base")),
                "an unknown zone stops the start rather than silently using another one");
    }
}
