package net.onelitefeather.menura.core.channel;

import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelRepresentationTest {

    @Test
    void channelHasNoTokenComponent() {
        Arrays.stream(Channel.class.getRecordComponents()).map(RecordComponent::getName).forEach(name ->
                assertFalse(name.toLowerCase(Locale.ROOT).contains("token"),
                        "the channel representation must have no token field, but has " + name));
    }

    @Test
    void grantDoesNotRevealItsTokensInText() {
        ChannelGrant grant = new ChannelGrant(UUID.randomUUID(), "access-SECRET-value",
                "refresh-SECRET-value", null, Instant.EPOCH);

        assertFalse(grant.toString().contains("SECRET"), "a grant printed in a log line must not show a token");
        assertTrue(grant.toString().contains("redacted"), "the redaction is visible so a reader knows why");
    }
}
