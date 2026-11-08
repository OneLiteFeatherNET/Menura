package net.onelitefeather.menura.core.library.retention;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * One retention run: selects the raw items older than the policy allows, moves their media to cold storage and records
 * the new media state on each item. Clips, transcripts, timelines, finished assets and lineage are not touched.
 * A disabled policy does nothing.
 */
public final class RetentionRun {

    public static final String COLD = "cold";

    private final RetentionSettings settings;
    private final RawMediaCatalog catalog;
    private final RawMediaLifecycle lifecycle;

    public RetentionRun(RetentionSettings settings, RawMediaCatalog catalog, RawMediaLifecycle lifecycle) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
    }

    /** Moves one batch of expired raw media and returns how many recordings were moved. */
    public int run(Instant now) throws Exception {
        if (!settings.enabled()) {
            return 0;
        }
        Instant cutoff = now.minus(settings.rawMaxAge());
        List<RawMediaRef> expired = catalog.olderThan(cutoff, settings.batchSize());
        for (RawMediaRef item : expired) {
            lifecycle.moveToCold(item.storageKey());
            catalog.setMediaState(item.id(), COLD);
        }
        return expired.size();
    }
}
