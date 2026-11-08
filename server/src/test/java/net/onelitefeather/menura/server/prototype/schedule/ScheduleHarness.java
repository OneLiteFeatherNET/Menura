package net.onelitefeather.menura.server.prototype.schedule;

import net.onelitefeather.menura.server.prototype.DemoStore;
import net.onelitefeather.menura.server.prototype.pool.Asset;
import net.onelitefeather.menura.server.prototype.pool.AssetRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

/**
 * One fresh set of collaborators per test, with a fixed clock. Nothing is shared between tests, so the tests can run
 * in any order.
 */
final class ScheduleHarness {

    /** The seed "now" of the prototype: Sunday 8 Nov 2026, 13:00 in Berlin. */
    static final Instant NOW = Instant.parse("2026-11-08T12:00:00Z");

    final Clock clock;
    final DemoStore store;
    final AssetRepository assets;
    final PublicationRepository repository;
    final ScheduleService service;

    private ScheduleHarness(Clock clock, DemoStore store, AssetRepository assets, PublicationRepository repository) {
        this.clock = clock;
        this.store = store;
        this.assets = assets;
        this.repository = repository;
        this.service = new ScheduleService(clock, store, assets, repository);
    }

    static ScheduleHarness at(Instant now) {
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        DemoStore store = new DemoStore(clock);
        AssetRepository assets = new AssetRepository();
        return new ScheduleHarness(clock, store, assets, new PublicationRepository(clock, assets, store));
    }

    static ScheduleHarness seeded() {
        return at(NOW);
    }

    /** The id of the stored publication of a clip on a channel; the seed or a test must have created it. */
    String idOf(String assetId, String channelId) {
        return repository.all().stream()
                .filter(p -> assetId.equals(p.assetId()) && channelId.equals(p.channelId()))
                .findFirst().orElseThrow().id();
    }

    /** A rendered clip of a series, cut from a seeded recording, saved into this harness's pool. */
    Asset saveRenderedClip(String id, String seriesId, String createdAt) {
        Asset clip = new Asset(id, Asset.Kind.clip, "Clip " + id, "p-a", List.of("p-a"), List.of("live-migration"), 60,
                Instant.parse(createdAt), "a-100", seriesId, Asset.Stage.rendered, Asset.Transcript.ready, 44_400_000L);
        return assets.save(clip);
    }

    /** A minimal stored publication, for tests that only need rows to count. */
    static Publication row(String id) {
        return new Publication(id, "a-fill", "yt-de", "youtube", "live-migration", NOW.plusSeconds(86_400),
                Publication.NATIVE, Publication.Status.planned, new Publication.Metadata("Zeile", "", List.of(), null),
                Step.allWaiting(), null, List.of());
    }
}
