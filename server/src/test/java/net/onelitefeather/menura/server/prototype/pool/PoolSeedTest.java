package net.onelitefeather.menura.server.prototype.pool;

import net.onelitefeather.menura.server.prototype.pool.Asset.Kind;
import net.onelitefeather.menura.server.prototype.pool.Asset.Stage;
import net.onelitefeather.menura.server.prototype.pool.Asset.Transcript;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The seed is pure data: every test reads the static seed and builds nothing else. */
class PoolSeedTest {

    private static final Instant NOW = Instant.parse("2026-11-08T12:00:00Z");
    private static final Pattern TAG = Pattern.compile("^[a-z0-9-]{2,30}$");
    private static final Set<String> SERIES_OF_RENDERED_CLIPS = Set.of("k8s-30s", "live-migration", "survival");

    private static List<Asset> assets() {
        return PoolSeed.assets();
    }

    private static Optional<Asset> byId(String id) {
        return assets().stream().filter(asset -> asset.id().equals(id)).findFirst();
    }

    private static List<Asset> stream() {
        return assets().stream().filter(asset -> asset.sourceAssetId() == null).toList();
    }

    private static long countStage(Stage stage) {
        return assets().stream().filter(asset -> asset.stage() == stage).count();
    }

    @Test
    void theSeedHoldsFourStreamRecordingsOfOneAndAHalfToThreeHours() {
        List<Asset> streams = stream();

        assertEquals(4, streams.size(), "four raw recordings");
        assertTrue(streams.stream().allMatch(asset -> asset.kind() == Kind.raw), "recordings are of kind raw");
        assertTrue(streams.stream().allMatch(asset -> asset.stage() == Stage.raw), "recordings are in stage raw");
        assertTrue(streams.stream().allMatch(asset -> asset.durationSeconds() >= 5_400
                && asset.durationSeconds() <= 10_800), "recordings last between 1.5 and 3 hours");
    }

    @Test
    void theSeedHasSixRenderedFiveCutAndThreeRawSnippetsOfTheDerivedAssets() {
        List<Asset> derived = assets().stream().filter(asset -> asset.sourceAssetId() != null).toList();

        assertEquals(14, derived.size(), "fourteen clips are cut from the recordings");
        assertEquals(6, derived.stream().filter(asset -> asset.stage() == Stage.rendered).count(), "rendered");
        assertEquals(5, derived.stream().filter(asset -> asset.stage() == Stage.cut).count(), "cut");
        assertEquals(3, derived.stream().filter(asset -> asset.stage() == Stage.raw).count(), "raw snippets");
        assertEquals(6, countStage(Stage.rendered), "rendered assets in total");
    }

    @Test
    void everyDerivedAssetPointsAtAnExistingRawRecording() {
        for (Asset derived : assets().stream().filter(asset -> asset.sourceAssetId() != null).toList()) {
            Optional<Asset> source = byId(derived.sourceAssetId());

            assertTrue(source.isPresent(), derived.id() + " must point at an existing asset");
            assertEquals(Kind.raw, source.get().kind(), derived.id() + " must point at raw material");
            assertTrue(derived.durationSeconds() <= source.get().durationSeconds(),
                    derived.id() + " must not be longer than its recording");
        }
    }

    @Test
    void everyRenderedClipHasAReadyOrPendingTranscript() {
        List<Asset> rendered = assets().stream().filter(asset -> asset.stage() == Stage.rendered).toList();

        assertTrue(rendered.stream().allMatch(asset -> asset.transcript() == Transcript.ready
                || asset.transcript() == Transcript.pending), "a rendered clip is never without a transcript");
    }

    @Test
    void transcriptStatesAreMostlyReadyWithThreePendingAndOneNone() {
        Map<Transcript, Long> counts = assets().stream()
                .collect(Collectors.groupingBy(Asset::transcript, Collectors.counting()));

        assertEquals(14L, counts.getOrDefault(Transcript.ready, 0L), "ready");
        assertEquals(3L, counts.getOrDefault(Transcript.pending, 0L), "pending");
        assertEquals(1L, counts.getOrDefault(Transcript.none, 0L), "none");
    }

    @Test
    void onlyReadyTranscriptsHaveSegments() {
        for (Asset asset : assets()) {
            List<TranscriptSegment> segments = PoolSeed.transcripts().getOrDefault(asset.id(), List.of());

            if (asset.transcript() == Transcript.ready) {
                assertTrue(segments.size() >= 6 && segments.size() <= 12,
                        asset.id() + " has six to twelve segments");
            } else {
                assertTrue(segments.isEmpty(), asset.id() + " has no segments while its transcript is "
                        + asset.transcript());
            }
        }
    }

    @Test
    void segmentsAreOrderedInsideTheAssetAndHaveText() {
        for (Map.Entry<String, List<TranscriptSegment>> entry : PoolSeed.transcripts().entrySet()) {
            Asset asset = byId(entry.getKey()).orElseThrow();
            double previousEnd = 0;

            for (TranscriptSegment segment : entry.getValue()) {
                assertTrue(segment.startSeconds() >= previousEnd, asset.id() + " segments do not overlap");
                assertTrue(segment.endSeconds() > segment.startSeconds(), asset.id() + " segment ends after it starts");
                assertTrue(segment.endSeconds() <= asset.durationSeconds(), asset.id() + " segment lies in the asset");
                assertFalse(segment.text().isBlank(), asset.id() + " segment has text");
                previousEnd = segment.endSeconds();
            }
        }
    }

    @Test
    void ownersAreTheFourDemoPersonsAndEachOfThemOwnsSomething() {
        Set<String> owners = assets().stream().map(Asset::ownerId).collect(Collectors.toSet());

        assertEquals(Set.of("p-a", "p-b", "p-c", "p-d"), owners);
    }

    @Test
    void theDemoPersonsAreNamedAsDemoPersons() {
        List<Person> people = PoolSeed.people();

        assertEquals(List.of("p-a", "p-b", "p-c", "p-d"), people.stream().map(Person::id).toList());
        assertEquals(List.of("Demo-Person A", "Demo-Person B", "Demo-Person C", "Demo-Person D"),
                people.stream().map(Person::name).toList());
    }

    @Test
    void everyTagIsATechTopicThatMatchesTheTagRule() {
        for (Asset asset : assets()) {
            for (String tag : asset.tags()) {
                assertTrue(TAG.matcher(tag).matches(), asset.id() + " has the valid tag " + tag);
            }
        }
    }

    @Test
    void renderedClipsBelongToTheSeriesOfTheDemoSeedOrToNoSeries() {
        for (Asset rendered : assets().stream().filter(asset -> asset.stage() == Stage.rendered).toList()) {
            if (rendered.seriesId() != null) {
                assertTrue(SERIES_OF_RENDERED_CLIPS.contains(rendered.seriesId()),
                        rendered.id() + " belongs to a series of the demo seed");
            }
        }
        assertTrue(assets().stream().anyMatch(asset -> asset.seriesId() != null
                        && SERIES_OF_RENDERED_CLIPS.contains(asset.seriesId()) && asset.stage() == Stage.rendered),
                "several rendered clips belong to a series");
    }

    @Test
    void everyAssetIsCreatedNoLaterThanTheSeedNow() {
        assertTrue(assets().stream().allMatch(asset -> !asset.createdAt().isAfter(NOW)));
    }

    @Test
    void assetIdsAreUnique() {
        Set<String> ids = assets().stream().map(Asset::id).collect(Collectors.toSet());

        assertEquals(assets().size(), ids.size());
    }

    @Test
    void theSeedIsDeterministic() {
        assertEquals(PoolSeed.assets(), PoolSeed.assets(), "the same assets on every call");
        assertEquals(PoolSeed.transcripts(), PoolSeed.transcripts(), "the same transcripts on every call");
    }
}
