package net.onelitefeather.menura.server.prototype.analytics;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * One measurement of one post at one offset after it was published. {@code values} holds the metrics the platform
 * delivers at that point, keyed by catalogue id; an absent key is unavailable and is served as null, never as 0.
 */
public record Snapshot(String postId, String platform, Offset offset, Instant at, Map<String, Double> values) {

    /** The measurement offsets Menura takes after publishing, in the order of the contract. */
    public enum Offset {
        H1("+1h", Duration.ofHours(1)),
        H2("+2h", Duration.ofHours(2)),
        H4("+4h", Duration.ofHours(4)),
        H8("+8h", Duration.ofHours(8)),
        H24("+24h", Duration.ofHours(24)),
        H48("+48h", Duration.ofHours(48)),
        D7("+7d", Duration.ofDays(7));

        private final String label;
        private final Duration duration;

        Offset(String label, Duration duration) {
            this.label = label;
            this.duration = duration;
        }

        public String label() {
            return label;
        }

        public Duration duration() {
            return duration;
        }

        public static Optional<Offset> ofLabel(String label) {
            for (Offset offset : values()) {
                if (offset.label.equals(label)) {
                    return Optional.of(offset);
                }
            }
            return Optional.empty();
        }
    }

    public Optional<Double> value(String metric) {
        return Optional.ofNullable(values.get(metric));
    }
}
