package net.onelitefeather.menura.core.publish;

import java.util.Optional;

/** The steps a publication of a post goes through, in this order. Each step has its own row with its state and time. */
public enum PostStep {
    UPLOAD("upload"),
    SCHEDULE("schedule"),
    METADATA("metadata"),
    PUBLISH("publish");

    private final String value;

    PostStep(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static Optional<PostStep> parse(String name) {
        for (PostStep step : values()) {
            if (step.value.equals(name)) {
                return Optional.of(step);
            }
        }
        return Optional.empty();
    }
}
