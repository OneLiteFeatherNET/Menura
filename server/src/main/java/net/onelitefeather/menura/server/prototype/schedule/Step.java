package net.onelitefeather.menura.server.prototype.schedule;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/** One step of a publication: upload, schedule, metadata, publish and metrics, in this order. */
@Serdeable
public record Step(StepName name, StepState state, Instant at, String detail) {

    public enum StepName { upload, schedule, metadata, publish, metrics }

    public enum StepState { done, running, waiting, failed }

    /** All five steps waiting, in the order of the contract. */
    public static List<Step> allWaiting() {
        return Arrays.stream(StepName.values())
                .map(name -> new Step(name, StepState.waiting, null, null))
                .toList();
    }
}
