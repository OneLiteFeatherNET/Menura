package net.onelitefeather.menura.server.prototype.pool;

import net.onelitefeather.menura.server.prototype.pool.Asset.Kind;
import net.onelitefeather.menura.server.prototype.pool.Asset.Stage;
import net.onelitefeather.menura.server.prototype.pool.Asset.Transcript;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Each test builds its own repository from the seed, so no test sees another test's saves. */
class AssetRepositoryTest {

    private static Asset newAsset(String id) {
        return new Asset(id, Kind.clip, "Neuer Clip " + id, "p-a", List.of("p-a"), List.of("ebpf"), 30,
                Instant.parse("2026-11-08T11:00:00Z"), "a-100", null, Stage.cut, Transcript.pending, 22_200_000L);
    }

    @Test
    void theRepositoryStartsWithTheSeededAssets() {
        AssetRepository repository = new AssetRepository();

        assertEquals(PoolSeed.assets().size(), repository.all().size());
        assertTrue(repository.find("a-104").isPresent(), "a seeded rendered clip is found by its id");
    }

    @Test
    void findReturnsEmptyForAnUnknownId() {
        AssetRepository repository = new AssetRepository();

        assertTrue(repository.find("a-999").isEmpty());
    }

    @Test
    void saveAddsANewAssetThatCanBeFoundAgain() {
        AssetRepository repository = new AssetRepository();
        int before = repository.all().size();

        repository.save(newAsset("a-900"));

        assertEquals(before + 1, repository.all().size(), "one more asset");
        assertEquals("Neuer Clip a-900", repository.find("a-900").orElseThrow().title());
    }

    @Test
    void saveReplacesTheAssetWithTheSameId() {
        AssetRepository repository = new AssetRepository();
        int before = repository.all().size();
        Asset seeded = repository.find("a-104").orElseThrow();

        repository.save(new Asset(seeded.id(), seeded.kind(), "Umbenannt", seeded.ownerId(), seeded.people(),
                seeded.tags(), seeded.durationSeconds(), seeded.createdAt(), seeded.sourceAssetId(),
                seeded.seriesId(), Stage.rendered, seeded.transcript(), seeded.sizeBytes()));

        assertEquals(before, repository.all().size(), "no asset was added");
        assertEquals("Umbenannt", repository.find("a-104").orElseThrow().title());
    }

    @Test
    void saveFailsForANewAssetBeyondTheCapacityOfFiveHundred() {
        AssetRepository repository = new AssetRepository();
        for (int number = repository.all().size(); number < AssetRepository.CAPACITY; number++) {
            repository.save(newAsset("a-" + (1_000 + number)));
        }
        assertEquals(AssetRepository.CAPACITY, repository.all().size(), "the repository is full");

        assertThrows(AssetCapacityExceededException.class, () -> repository.save(newAsset("a-overflow")));
        assertTrue(repository.find("a-overflow").isEmpty(), "the rejected asset was not stored");
    }

    @Test
    void replacingAnAssetIsStillAllowedAtTheCapacity() {
        AssetRepository repository = new AssetRepository();
        for (int number = repository.all().size(); number < AssetRepository.CAPACITY; number++) {
            repository.save(newAsset("a-" + (1_000 + number)));
        }

        Asset replaced = repository.save(newAsset("a-104"));

        assertEquals("Neuer Clip a-104", replaced.title());
        assertEquals(AssetRepository.CAPACITY, repository.all().size());
    }

    @Test
    void transcriptOfAReadyAssetHasItsSegments() {
        AssetRepository repository = new AssetRepository();

        List<TranscriptSegment> segments = repository.transcript("a-104");

        assertTrue(segments.size() >= 6 && segments.size() <= 12, "six to twelve segments");
    }

    @Test
    void transcriptOfAPendingAssetIsEmpty() {
        AssetRepository repository = new AssetRepository();

        assertTrue(repository.transcript("a-107").isEmpty(), "pending transcripts have no segments yet");
    }

    @Test
    void transcriptOfAnUnknownAssetIsEmpty() {
        AssetRepository repository = new AssetRepository();

        assertTrue(repository.transcript("a-999").isEmpty());
    }

    @Test
    void peopleAreTheFourDemoPersons() {
        AssetRepository repository = new AssetRepository();

        assertEquals(4, repository.people().size());
        assertEquals("Demo-Person C", repository.person("p-c").orElseThrow().name());
    }

    @Test
    void personIsEmptyForAnUnknownId() {
        AssetRepository repository = new AssetRepository();

        assertFalse(repository.person("p-z").isPresent());
    }

    @Test
    void allReturnsASnapshotThatCannotChangeTheStore() {
        AssetRepository repository = new AssetRepository();

        assertThrows(UnsupportedOperationException.class, () -> repository.all().clear());
        assertFalse(repository.all().isEmpty(), "the store kept its assets");
    }
}
