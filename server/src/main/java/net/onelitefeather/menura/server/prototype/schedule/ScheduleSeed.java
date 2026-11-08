package net.onelitefeather.menura.server.prototype.schedule;

import net.onelitefeather.menura.server.prototype.DemoStore;
import net.onelitefeather.menura.server.prototype.Model;
import net.onelitefeather.menura.server.prototype.pool.Asset;
import net.onelitefeather.menura.server.prototype.pool.AssetRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The demo publications: ten planned or scheduled on the series slots of the next three weeks, and one failed TikTok
 * upload. They are placed relative to the injected clock, on the Berlin date of "now" plus a day offset. The rendered
 * clips a-107 and a-109 are left unscheduled for the plan endpoint.
 */
final class ScheduleSeed {

    private static final String API_AUDIT_DETAIL = "Ohne API-Audit nur privat veröffentlicht";

    private ScheduleSeed() {
    }

    private record Planned(String assetId, String channelId, int dayOffset, Publication.Status status) {
    }

    private static final List<Planned> PLAN = List.of(
            new Planned("a-104", "yt-de", 2, Publication.Status.scheduled),
            new Planned("a-104", "tt-de", 2, Publication.Status.planned),
            new Planned("a-104", "ig-de", 2, Publication.Status.planned),
            new Planned("a-105", "yt-de", 5, Publication.Status.scheduled),
            new Planned("a-105", "tt-de", 5, Publication.Status.failed),
            new Planned("a-106", "yt-de", 9, Publication.Status.scheduled),
            new Planned("a-106", "tt-de", 9, Publication.Status.planned),
            new Planned("a-106", "ig-de", 9, Publication.Status.planned),
            new Planned("a-108", "yt-de", 16, Publication.Status.scheduled),
            new Planned("a-108", "tt-de", 16, Publication.Status.planned),
            new Planned("a-108", "ig-de", 16, Publication.Status.planned));

    static List<Publication> publications(Instant now, AssetRepository assets, DemoStore store) {
        LocalDate today = now.atZone(Slots.BERLIN).toLocalDate();
        String cta = store.brandKit().cta().de();
        List<Publication> seeded = new ArrayList<>();
        int number = 0;
        for (Planned planned : PLAN) {
            Asset asset = assets.find(planned.assetId()).orElseThrow();
            Model.Series series = seriesOf(store, asset.seriesId());
            String channelId = planned.channelId();
            Model.Channel channel = store.channels().stream().filter(c -> c.id().equals(channelId)).findFirst()
                    .orElseThrow();
            LocalTime slot = LocalTime.parse(series.slot());
            Instant publishAt = ZonedDateTime.of(today.plusDays(planned.dayOffset()), slot, Slots.BERLIN).toInstant();
            seeded.add(new Publication("pub-" + (++number), asset.id(), channel.id(), channel.platform(),
                    asset.seriesId(), publishAt, PlatformCapabilities.modeOf(channel.platform()), planned.status(),
                    ScheduleService.defaultMetadata(series, asset, channel.id(), cta), stepsOf(planned, channel, now),
                    null, List.of()));
        }
        return seeded;
    }

    private static List<Step> stepsOf(Planned planned, Model.Channel channel, Instant now) {
        return switch (planned.status()) {
            case failed -> List.of(
                    new Step(Step.StepName.upload, Step.StepState.failed, now, API_AUDIT_DETAIL),
                    new Step(Step.StepName.schedule, Step.StepState.waiting, null, null),
                    new Step(Step.StepName.metadata, Step.StepState.waiting, null, null),
                    new Step(Step.StepName.publish, Step.StepState.waiting, null, null),
                    new Step(Step.StepName.metrics, Step.StepState.waiting, null, null));
            case scheduled -> {
                if (!PlatformCapabilities.modeOf(channel.platform()).equals(Publication.NATIVE)) {
                    throw new IllegalStateException("only a native platform holds a scheduled publication");
                }
                yield List.of(
                        new Step(Step.StepName.upload, Step.StepState.done, now, null),
                        new Step(Step.StepName.schedule, Step.StepState.done, now, null),
                        new Step(Step.StepName.metadata, Step.StepState.done, now, null),
                        new Step(Step.StepName.publish, Step.StepState.waiting, null, null),
                        new Step(Step.StepName.metrics, Step.StepState.waiting, null, null));
            }
            default -> Step.allWaiting();
        };
    }

    private static Model.Series seriesOf(DemoStore store, String seriesId) {
        return store.series().stream().filter(s -> s.id().equals(seriesId)).findFirst().orElseThrow();
    }
}
