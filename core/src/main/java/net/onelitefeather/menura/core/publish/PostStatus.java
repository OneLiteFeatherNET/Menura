package net.onelitefeather.menura.core.publish;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The statuses of a post and the transitions the server allows between them (change add-content-distribution). The
 * table lives here, not in the interface, so every client is held to it: a skip such as draft to published is refused.
 */
public enum PostStatus {
    DRAFT("draft"),
    IN_REVIEW("in_review"),
    APPROVED("approved"),
    SCHEDULED("scheduled"),
    PUBLISHED("published"),
    FAILED("failed");

    private final String value;

    PostStatus(String value) {
        this.value = value;
    }

    /** The lower case name of the status as the API and the database write it. */
    public String value() {
        return value;
    }

    /** Parses the wire name, case-insensitively. An unknown name is empty. */
    public static Optional<PostStatus> parse(String name) {
        if (name == null) {
            return Optional.empty();
        }
        for (PostStatus status : values()) {
            if (status.value.equals(name.trim().toLowerCase(Locale.ROOT))) {
                return Optional.of(status);
            }
        }
        return Optional.empty();
    }

    /** The statuses a post may move to from this one. Published has none: a published post is only read. */
    public Set<PostStatus> next() {
        return switch (this) {
            case DRAFT -> EnumSet.of(IN_REVIEW);
            case IN_REVIEW -> EnumSet.of(APPROVED, DRAFT);
            case APPROVED -> EnumSet.of(SCHEDULED, IN_REVIEW);
            case SCHEDULED -> EnumSet.of(PUBLISHED, FAILED, IN_REVIEW);
            case FAILED -> EnumSet.of(SCHEDULED, IN_REVIEW);
            case PUBLISHED -> EnumSet.noneOf(PostStatus.class);
        };
    }

    public boolean canMoveTo(PostStatus target) {
        return next().contains(target);
    }

    /** Statuses in which the content of a post may still be edited by an editor. */
    public boolean editable() {
        return this == DRAFT || this == IN_REVIEW;
    }

    /** Statuses in which a revision may be proposed. */
    public boolean revisable() {
        return this == DRAFT || this == IN_REVIEW || this == APPROVED || this == SCHEDULED;
    }
}
