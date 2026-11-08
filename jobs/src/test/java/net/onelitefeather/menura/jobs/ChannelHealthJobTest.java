package net.onelitefeather.menura.jobs;

import net.onelitefeather.menura.core.config.ChannelSettings;
import net.onelitefeather.menura.core.config.MenuraSettings;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelHealthJobTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-02T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void jobIsNamedForTheCronJobThatRunsIt() {
        ChannelHealthJob job = new ChannelHealthJob(MenuraSettings.from(Map.of()), ChannelSettings.from(Map.of()), CLOCK);

        assertEquals("channel-health", job.name());
    }

    @Test
    void runWithoutTheTokenKeyFailsBeforeAnyDatabaseIsContacted() {
        ChannelHealthJob job = new ChannelHealthJob(MenuraSettings.from(Map.of()), ChannelSettings.from(Map.of()), CLOCK);

        IllegalStateException failure = assertThrows(IllegalStateException.class, job::execute);

        assertTrue(failure.getMessage().contains("MENURA_TOKEN_ENCRYPTION_KEY"),
                "the run names the variable the operator must set");
    }

    @Test
    void refreshMarginDefaultsToTenMinutesAndTheRedirectToTheLocalCallback() {
        ChannelSettings settings = ChannelSettings.from(Map.of());

        assertEquals(Duration.ofMinutes(10), settings.refreshMargin());
        assertEquals("http://localhost:8080/channel-connections/callback", settings.youtubeRedirectUri());
    }

    @Test
    void settingsTextNeverShowsTheKeysOrTheClientSecret() {
        ChannelSettings settings = ChannelSettings.from(Map.of(
                "MENURA_TOKEN_ENCRYPTION_KEY", "KEY-VALUE-SHOULD-NOT-PRINT",
                "MENURA_YOUTUBE_CLIENT_SECRET", "SECRET-VALUE-SHOULD-NOT-PRINT"));

        assertFalse(settings.toString().contains("KEY-VALUE"), "the token key stays out of the text form");
        assertFalse(settings.toString().contains("SECRET-VALUE"), "the client secret stays out of the text form");
    }
}
