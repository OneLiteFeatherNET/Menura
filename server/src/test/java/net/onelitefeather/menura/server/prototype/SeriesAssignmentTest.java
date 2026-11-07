package net.onelitefeather.menura.server.prototype;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SeriesAssignmentTest {

    private final List<Model.Series> series = DemoSeed.series();

    @Test
    void aPostWithExactlyOneSeriesHashtagIsAssignedToThatSeries() {
        assertEquals(Optional.of("k8s-30s"),
                Assignment.automaticSeries(List.of("#k8sin30", "#kubernetes"), series));
    }

    @Test
    void aPostWithHashtagsOfTwoSeriesIsLeftUnassigned() {
        assertEquals(Optional.empty(),
                Assignment.automaticSeries(List.of("#k8sin30", "#livemigration"), series));
    }

    @Test
    void aPostWithoutAnySeriesHashtagIsLeftUnassigned() {
        assertEquals(Optional.empty(), Assignment.automaticSeries(List.of("#kubernetes"), series));
    }

    @Test
    void theHashtagMatchIgnoresCase() {
        assertEquals(Optional.of("k8s-30s"), Assignment.automaticSeries(List.of("#K8SIN30"), series));
    }
}
