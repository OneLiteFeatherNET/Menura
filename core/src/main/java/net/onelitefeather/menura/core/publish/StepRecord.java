package net.onelitefeather.menura.core.publish;

import java.time.Instant;
import java.util.UUID;

/** The state of one publication step of a post, with the time of its last change and, when failed, its reason. */
public record StepRecord(UUID postId, PostStep step, StepState state, Instant changedAt, String reason) {
}
