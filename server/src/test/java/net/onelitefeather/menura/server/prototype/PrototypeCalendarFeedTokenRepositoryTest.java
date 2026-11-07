package net.onelitefeather.menura.server.prototype;

import net.onelitefeather.menura.server.secret.FeedToken;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Each test builds its own repository, so the order of the tests does not matter. The connection is not used. */
class PrototypeCalendarFeedTokenRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");

    private static UUID id(int number) {
        return UUID.nameUUIDFromBytes(("token-" + number).getBytes());
    }

    private static FeedToken token(int number) {
        byte[] hash = ByteBuffer.allocate(Integer.BYTES).putInt(number).array();
        return new FeedToken(id(number), UUID.nameUUIDFromBytes("person".getBytes()), hash, NOW, null);
    }

    @Test
    void theStoredTokensAreCappedAtOneThousandAndTheOldestIsEvicted() {
        PrototypeCalendarFeedTokenRepository repository = new PrototypeCalendarFeedTokenRepository();
        int newest = PrototypeCalendarFeedTokenRepository.MAX_TOKENS + 1;
        for (int number = 1; number <= newest; number++) {
            repository.insert(null, token(number));
        }

        assertEquals(Optional.empty(), repository.findActive(null, id(1)), "the oldest token was evicted");
        assertTrue(repository.findActive(null, id(2)).isPresent(), "the second token is still stored");
        assertTrue(repository.findActive(null, id(newest)).isPresent(), "the newest token is stored");
    }

    @Test
    void theCapIsOneThousandTokens() {
        assertEquals(1_000, PrototypeCalendarFeedTokenRepository.MAX_TOKENS);
    }
}
