package net.onelitefeather.menura.core.library.retention;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetentionRunTest {

    private static final Instant NOW = Instant.parse("2026-11-08T10:00:00Z");
    private static final Duration MAX_AGE = Duration.ofDays(30);
    private static final UUID OLD = UUID.fromString("a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d");
    private static final UUID RECENT = UUID.fromString("b2c3d4e5-f6a7-4b8c-9d0e-1f2a3b4c5d6e");

    /** Models the catalog rules: only available raw items older than the cutoff are selected; rows are never removed. */
    static final class InMemoryCatalog implements RawMediaCatalog {

        private final Map<UUID, Entry> rows = new java.util.LinkedHashMap<>();

        record Entry(UUID id, String storageKey, Instant createdAt, String state) {
        }

        void add(UUID id, String key, Instant createdAt) {
            rows.put(id, new Entry(id, key, createdAt, "available"));
        }

        @Override
        public List<RawMediaRef> olderThan(Instant cutoff, int limit) {
            List<RawMediaRef> found = new ArrayList<>();
            for (Entry entry : rows.values()) {
                if ("available".equals(entry.state()) && entry.createdAt().isBefore(cutoff) && found.size() < limit) {
                    found.add(new RawMediaRef(entry.id(), entry.storageKey()));
                }
            }
            return found;
        }

        @Override
        public void setMediaState(UUID rawItem, String state) {
            Entry entry = rows.get(rawItem);
            rows.put(rawItem, new Entry(entry.id(), entry.storageKey(), entry.createdAt(), state));
        }

        Optional<String> stateOf(UUID rawItem) {
            return Optional.ofNullable(rows.get(rawItem)).map(Entry::state);
        }

        int rowCount() {
            return rows.size();
        }
    }

    static final class RecordingLifecycle implements RawMediaLifecycle {

        final List<String> moved = new ArrayList<>();

        @Override
        public void moveToCold(String storageKey) {
            moved.add(storageKey);
        }
    }

    private static RetentionSettings settings(boolean enabled) {
        return new RetentionSettings(enabled, MAX_AGE, 100);
    }

    @Test
    void disabledPolicyMovesNothing() throws Exception {
        InMemoryCatalog catalog = new InMemoryCatalog();
        catalog.add(OLD, "objects/old", NOW.minus(Duration.ofDays(40)));
        RecordingLifecycle lifecycle = new RecordingLifecycle();

        int moved = new RetentionRun(settings(false), catalog, lifecycle).run(NOW);

        assertEquals(0, moved, "a disabled policy moves no media");
        assertTrue(lifecycle.moved.isEmpty(), "the object store is not asked to move anything");
    }

    @Test
    void onlyRawMediaOlderThanTheLimitIsMovedToCold() throws Exception {
        InMemoryCatalog catalog = new InMemoryCatalog();
        catalog.add(OLD, "objects/old", NOW.minus(Duration.ofDays(40)));
        catalog.add(RECENT, "objects/recent", NOW.minus(Duration.ofDays(5)));
        RecordingLifecycle lifecycle = new RecordingLifecycle();

        int moved = new RetentionRun(settings(true), catalog, lifecycle).run(NOW);

        assertEquals(1, moved, "one recording is older than the limit");
        assertEquals(List.of("objects/old"), lifecycle.moved, "only the old recording is moved");
        assertEquals("cold", catalog.stateOf(OLD).orElseThrow(), "the old item records its new media state");
        assertEquals("available", catalog.stateOf(RECENT).orElseThrow(), "the recent item keeps its media");
    }

    @Test
    void movedItemKeepsItsRow() throws Exception {
        InMemoryCatalog catalog = new InMemoryCatalog();
        catalog.add(OLD, "objects/old", NOW.minus(Duration.ofDays(40)));

        new RetentionRun(settings(true), catalog, new RecordingLifecycle()).run(NOW);

        assertEquals(1, catalog.rowCount(), "moving media never removes the raw item row");
    }

    @Test
    void secondRunDoesNotMoveTheSameItemAgain() throws Exception {
        InMemoryCatalog catalog = new InMemoryCatalog();
        catalog.add(OLD, "objects/old", NOW.minus(Duration.ofDays(40)));
        RecordingLifecycle lifecycle = new RecordingLifecycle();
        RetentionRun run = new RetentionRun(settings(true), catalog, lifecycle);

        run.run(NOW);
        int second = run.run(NOW);

        assertEquals(0, second, "an item already cold is not selected again");
        assertEquals(1, lifecycle.moved.size(), "the object store is asked once");
    }
}
