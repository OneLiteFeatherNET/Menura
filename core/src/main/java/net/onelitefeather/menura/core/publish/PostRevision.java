package net.onelitefeather.menura.core.publish;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A proposed optimisation of a post, with its rationale. It is stored as its own record and leaves the post unchanged
 * until a publisher accepts it; a rejection keeps the post unchanged as well.
 */
public record PostRevision(UUID id, UUID postId, UUID authorId, String rationale, Content content, Status status,
                           UUID decidedBy, Instant decidedAt, Instant createdAt) {

    public PostRevision {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(postId, "postId");
        Objects.requireNonNull(authorId, "authorId");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        if (rationale == null || rationale.isBlank()) {
            throw Post.invalid("A revision needs a rationale.");
        }
    }

    public enum Status {
        PENDING("pending"),
        ACCEPTED("accepted"),
        REJECTED("rejected");

        private final String value;

        Status(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }

        public static Optional<Status> parse(String name) {
            for (Status status : values()) {
                if (status.value.equals(name)) {
                    return Optional.of(status);
                }
            }
            return Optional.empty();
        }
    }

    public PostRevision decided(Status decision, UUID by, Instant at) {
        return new PostRevision(id, postId, authorId, rationale, content, decision, by, at, createdAt);
    }

    public boolean pending() {
        return status == Status.PENDING;
    }
}
