package net.onelitefeather.menura.core.publish;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.ZoneId;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PublishSettingsTest {

    @Test
    void theDefaultsApplyWhenNothingIsSet() {
        PublishSettings settings = PublishSettings.from(Map.of());

        assertEquals(3, settings.retry().maxAttempts(), "three attempts by default");
        assertEquals(Duration.ofMinutes(5), settings.retry().baseDelay(), "five minutes before the first retry");
        assertEquals(Duration.ofHours(24), settings.handoverLead(), "a natively scheduled post is handed over a day ahead");
        assertEquals(ZoneId.of("Europe/Berlin"), settings.zone(), "the installation time zone is Europe/Berlin");
    }

    @Test
    void theEnvironmentOverridesTheDefaults() {
        PublishSettings settings = PublishSettings.from(Map.of(PublishSettings.MAX_ATTEMPTS, "5",
                PublishSettings.RETRY_DELAY, "PT1M", PublishSettings.TIME_ZONE, "UTC"));

        assertEquals(5, settings.retry().maxAttempts(), "the attempt limit is read");
        assertEquals(Duration.ofMinutes(1), settings.retry().baseDelay(), "the retry delay is read");
        assertEquals(ZoneId.of("UTC"), settings.zone(), "the time zone is read");
    }

    @Test
    void aBatchOfZeroIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> PublishSettings.from(Map.of(PublishSettings.BATCH_SIZE, "0")), "a batch must claim at least one post");
    }
}
