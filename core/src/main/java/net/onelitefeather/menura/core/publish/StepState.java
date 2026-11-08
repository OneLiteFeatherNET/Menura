package net.onelitefeather.menura.core.publish;

import java.util.Optional;

/** The state of one publication step. */
public enum StepState {
    PENDING("pending"),
    RUNNING("running"),
    DONE("done"),
    FAILED("failed");

    private final String value;

    StepState(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static Optional<StepState> parse(String name) {
        for (StepState state : values()) {
            if (state.value.equals(name)) {
                return Optional.of(state);
            }
        }
        return Optional.empty();
    }
}
