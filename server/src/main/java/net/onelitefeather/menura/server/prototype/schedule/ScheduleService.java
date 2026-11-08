package net.onelitefeather.menura.server.prototype.schedule;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.server.prototype.DemoStore;
import net.onelitefeather.menura.server.prototype.Model;
import net.onelitefeather.menura.server.prototype.pool.Asset;
import net.onelitefeather.menura.server.prototype.pool.AssetRepository;
import net.onelitefeather.menura.server.prototype.schedule.Publication.Metadata;
import net.onelitefeather.menura.server.prototype.schedule.Publication.Status;
import net.onelitefeather.menura.server.prototype.schedule.PlatformCapabilities.Capability;
import io.micronaut.serde.annotation.Serdeable;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The rules of the publishing calendar. Published v1 posts are read from the {@link DemoStore} on every call and
 * shown as status published; everything else lives in the {@link PublicationRepository}. Time comes from the clock.
 */
@Singleton
@Requires(env = "prototype")
public class ScheduleService {

    /** A publication must lie at least this far in the future when it is created or moved. */
    static final Duration LEAD_TIME = Duration.ofMinutes(15);
    static final int MAX_TAGS = 15;
    static final int MAX_TITLE = 120;
    static final int MAX_DESCRIPTION = 5000;
    static final int MAX_PLAYLIST_URL = 300;
    static final int MAX_CHANNELS = 200;
    static final int MIN_COUNT = 1;
    static final int MAX_COUNT = 10;

    static final String NOT_RENDERED = "Nur fertig gerenderte Clips können eingeplant werden";
    static final String DUPLICATE = "Dieser Clip ist auf diesem Kanal bereits eingeplant.";
    static final String NOT_PLANNED = "Nur geplante Veröffentlichungen können geändert werden.";
    static final String NOT_DELETABLE = "Nur geplante oder hochgeladene Veröffentlichungen können gelöscht werden.";

    private static final Pattern HASHTAG = Pattern.compile("^#[a-z0-9-]{2,30}$");
    private static final Pattern CONTROL_OR_FORMAT = Pattern.compile("[\\p{Cc}\\p{Cf}]");
    private static final String PUBLISHED_PREFIX = "pub-";

    /** The body of POST /api/prototype/publications. Metadata is optional; its fields override the defaults. */
    @Serdeable
    public record CreateRequest(String assetId, List<String> channelIds, Instant publishAt, MetadataInput metadata) {
    }

    /** The body of PUT /api/prototype/publications/{id}. Absent fields keep their value. */
    @Serdeable
    public record UpdateRequest(Instant publishAt, MetadataInput metadata) {
    }

    /** The body of POST /api/prototype/series/{id}/plan. */
    @Serdeable
    public record PlanRequest(List<String> channelIds, Integer count) {
    }

    /** Metadata as the caller sends it. A null field keeps the default; an empty playlist removes it. */
    @Serdeable
    public record MetadataInput(String title, String description, List<String> tags, String playlist) {
    }

    private final Clock clock;
    private final DemoStore store;
    private final AssetRepository assets;
    private final PublicationRepository repository;

    public ScheduleService(Clock clock, DemoStore store, AssetRepository assets, PublicationRepository repository) {
        this.clock = clock;
        this.store = store;
        this.assets = assets;
        this.repository = repository;
    }

    public Map<String, Capability> platforms() {
        return PlatformCapabilities.all();
    }

    /**
     * Stored publications and published posts, sorted by publish time. {@code from} is inclusive, {@code to}
     * exclusive; every filter is optional and a null value means no filter.
     */
    public List<Publication> list(String from, String to, String channelId, String seriesId, String status) {
        Instant fromAt = instantOf(from, "from");
        Instant toAt = instantOf(to, "to");
        Status wanted = status == null ? null : statusOf(status);
        return Stream.concat(repository.all().stream(), publishedPosts().stream())
                .filter(p -> fromAt == null || !p.publishAt().isBefore(fromAt))
                .filter(p -> toAt == null || p.publishAt().isBefore(toAt))
                .filter(p -> channelId == null || channelId.equals(p.channelId()))
                .filter(p -> seriesId == null || seriesId.equals(p.seriesId()))
                .filter(p -> wanted == null || wanted == p.status())
                .sorted(Comparator.comparing(Publication::publishAt).thenComparing(Publication::id))
                .toList();
    }

    /** One publication per channel, all at the same time. Refused as a whole when any channel fails a rule. */
    public List<Publication> create(CreateRequest request) {
        Asset asset = request.assetId() == null ? null : assets.find(request.assetId()).orElse(null);
        if (asset == null) {
            throw unprocessable("assetId: unbekannter Clip.");
        }
        if (asset.stage() != Asset.Stage.rendered) {
            throw unprocessable(NOT_RENDERED);
        }
        requirePublishAt(request.publishAt());
        List<Model.Channel> channels = requireChannels(request.channelIds());
        MetadataInput input = validated(request.metadata());
        Model.Series series = seriesOf(asset.seriesId());
        String cta = store.brandKit().cta().de();

        return repository.locked(() -> {
            for (Model.Channel channel : channels) {
                if (repository.hasActive(asset.id(), channel.id())) {
                    throw conflict(DUPLICATE);
                }
            }
            List<Publication> created = new ArrayList<>();
            for (Model.Channel channel : channels) {
                Metadata defaults = defaultMetadata(series, asset, channel.id(), cta);
                created.add(newPublication(repository.nextId(), asset, channel, request.publishAt(),
                        merge(defaults, input)));
            }
            repository.insertAll(created);
            return created;
        });
    }

    /** Moves or re-describes a planned publication. A published v1 post has no plan to change: 409. */
    public Publication update(String id, UpdateRequest request) {
        return repository.locked(() -> {
            Publication current = findOrRefuse(id, NOT_PLANNED);
            if (current.status() != Status.planned) {
                throw conflict(NOT_PLANNED);
            }
            Publication updated = current;
            if (request.publishAt() != null) {
                requirePublishAt(request.publishAt());
                updated = updated.withPublishAt(request.publishAt());
            }
            if (request.metadata() != null) {
                updated = updated.withMetadata(merge(current.metadata(), validated(request.metadata())));
            }
            repository.replace(updated);
            return updated;
        });
    }

    /** Removes a publication that has not been published yet. */
    public void delete(String id) {
        repository.locked(() -> {
            Publication current = findOrRefuse(id, NOT_DELETABLE);
            if (current.status() != Status.planned && current.status() != Status.scheduled) {
                throw conflict(NOT_DELETABLE);
            }
            repository.remove(id);
            return null;
        });
    }

    /**
     * Puts the oldest rendered, unscheduled clips of the series on its next free slots. A slot is skipped when any of
     * the channels already has a publication at that instant. Asks for {@code count} slots but plans only as many as
     * there are clips; 422 when there is none.
     */
    public List<Publication> plan(String seriesId, PlanRequest request) {
        Model.Series series = store.series().stream().filter(s -> s.id().equals(seriesId)).findFirst()
                .orElseThrow(() -> new ProblemException(404, "Not Found", "unbekannte Serie."));
        List<Model.Channel> channels = requireChannels(request.channelIds());
        Integer count = request.count();
        if (count == null || count < MIN_COUNT || count > MAX_COUNT) {
            throw unprocessable("count: erlaubt sind 1 bis 10.");
        }
        return repository.locked(() -> {
            List<Asset> clips = assets.all().stream()
                    .filter(a -> seriesId.equals(a.seriesId()) && a.stage() == Asset.Stage.rendered)
                    .filter(a -> !repository.hasActive(a.id()))
                    .sorted(Comparator.comparing(Asset::createdAt).thenComparing(Asset::id))
                    .toList();
            if (clips.isEmpty()) {
                throw unprocessable("Für diese Serie gibt es keinen fertig gerenderten, noch nicht eingeplanten Clip.");
            }
            int wanted = Math.min(count, clips.size());
            List<Instant> slots = Slots.next(
                    DayOfWeek.valueOf(series.weekday().toUpperCase(Locale.ROOT)),
                    LocalTime.parse(series.slot()),
                    clock.instant().plus(LEAD_TIME),
                    wanted,
                    at -> channels.stream().anyMatch(c -> repository.occupied(c.id(), at)));
            String cta = store.brandKit().cta().de();
            List<Publication> created = new ArrayList<>();
            for (int i = 0; i < slots.size(); i++) {
                Asset clip = clips.get(i);
                for (Model.Channel channel : channels) {
                    created.add(newPublication(repository.nextId(), clip, channel, slots.get(i),
                            defaultMetadata(series, clip, channel.id(), cta)));
                }
            }
            repository.insertAll(created);
            return created;
        });
    }

    // Rules shared with the seed.

    /** The defaults of a publication: title = asset title; description = opening formula, title, CTA; tags = series hashtag and asset tags as hashtags (at most 15); playlist = the series playlist of the channel. */
    static Metadata defaultMetadata(Model.Series series, Asset asset, String channelId, String cta) {
        List<String> tags = new ArrayList<>();
        if (series != null) {
            tags.add(series.hashtag());
        }
        asset.tags().forEach(tag -> tags.add("#" + tag));
        List<String> limited = tags.stream().distinct().limit(MAX_TAGS).toList();
        String description = Stream.of(series == null ? null : series.openingFormula(), asset.title(), cta)
                .filter(text -> text != null && !text.isBlank())
                .reduce((a, b) -> a + " · " + b)
                .orElse("");
        String playlist = series == null ? null : series.playlists().get(channelId);
        return new Metadata(asset.title(), description, limited, playlist);
    }

    // Internals.

    private Publication newPublication(String id, Asset asset, Model.Channel channel, Instant publishAt,
                                       Metadata metadata) {
        return new Publication(id, asset.id(), channel.id(), channel.platform(), asset.seriesId(), publishAt,
                PlatformCapabilities.modeOf(channel.platform()), Status.planned, metadata, Step.allWaiting(), null,
                List.of());
    }

    private Publication findOrRefuse(String id, String conflictDetail) {
        return repository.find(id).orElseGet(() -> {
            if (isPublishedPost(id)) {
                throw conflict(conflictDetail);
            }
            throw new ProblemException(404, "Not Found", "unbekannte Veröffentlichung.");
        });
    }

    private boolean isPublishedPost(String id) {
        return id.startsWith(PUBLISHED_PREFIX) && publishedPosts().stream().anyMatch(p -> p.id().equals(id));
    }

    /** The v1 posts as published publications: all steps done, the post id set, no snapshots. */
    private List<Publication> publishedPosts() {
        return store.posts(null, null).stream().map(post -> {
            Instant at = Instant.parse(post.publishedAt());
            Model.Series series = seriesOf(post.seriesId());
            String playlist = post.inPlaylist() && series != null ? series.playlists().get(post.channelId()) : null;
            List<Step> steps = Stream.of(Step.StepName.values())
                    .map(name -> new Step(name, Step.StepState.done, at, null))
                    .toList();
            return new Publication(PUBLISHED_PREFIX + post.id(), null, post.channelId(), post.platform(),
                    post.seriesId(), at, PlatformCapabilities.modeOf(post.platform()), Status.published,
                    new Metadata(post.title(), "", post.hashtags(), playlist), steps, post.id(), List.of());
        }).toList();
    }

    private Model.Series seriesOf(String seriesId) {
        if (seriesId == null) {
            return null;
        }
        return store.series().stream().filter(s -> s.id().equals(seriesId)).findFirst().orElse(null);
    }

    private void requirePublishAt(Instant publishAt) {
        if (publishAt == null) {
            throw unprocessable("publishAt: ist erforderlich.");
        }
        if (publishAt.isBefore(clock.instant().plus(LEAD_TIME))) {
            throw unprocessable("publishAt: mindestens 15 Minuten in der Zukunft.");
        }
    }

    private List<Model.Channel> requireChannels(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            throw unprocessable("channelIds: mindestens ein Kanal ist erforderlich.");
        }
        if (ids.size() > MAX_CHANNELS) {
            throw unprocessable("channelIds: höchstens " + MAX_CHANNELS + " Einträge erlaubt.");
        }
        if (ids.stream().distinct().count() != ids.size()) {
            throw unprocessable("channelIds: jeder Kanal höchstens einmal.");
        }
        List<Model.Channel> channels = new ArrayList<>();
        for (String id : ids) {
            Model.Channel channel = store.channels().stream().filter(c -> c.id().equals(id)).findFirst()
                    .orElseThrow(() -> unprocessable("channelIds: unbekannter Kanal."));
            channels.add(channel);
        }
        return channels;
    }

    /** Checks the caller's metadata; null stays null. */
    private static MetadataInput validated(MetadataInput input) {
        if (input == null) {
            return null;
        }
        if (input.title() != null) {
            if (input.title().isBlank()) {
                throw unprocessable("metadata.title: ist erforderlich.");
            }
            if (input.title().length() > MAX_TITLE) {
                throw unprocessable("metadata.title: höchstens " + MAX_TITLE + " Zeichen erlaubt.");
            }
            rejectControlCharacters(input.title(), "metadata.title");
        }
        if (input.description() != null) {
            if (input.description().length() > MAX_DESCRIPTION) {
                throw unprocessable("metadata.description: höchstens " + MAX_DESCRIPTION + " Zeichen erlaubt.");
            }
            rejectControlCharacters(input.description(), "metadata.description");
        }
        if (input.tags() != null) {
            if (input.tags().size() > MAX_TAGS) {
                throw unprocessable("metadata.tags: höchstens " + MAX_TAGS + " Einträge erlaubt.");
            }
            for (String tag : input.tags()) {
                if (tag == null || !HASHTAG.matcher(tag).matches()) {
                    throw unprocessable("metadata.tags: erlaubt sind Hashtags aus # und 2–30 Kleinbuchstaben, Ziffern oder Bindestrichen.");
                }
            }
        }
        if (input.playlist() != null && !input.playlist().isBlank() && !isHttpsUrl(input.playlist())) {
            throw unprocessable("metadata.playlist: erlaubt ist eine https-URL mit höchstens "
                    + MAX_PLAYLIST_URL + " Zeichen.");
        }
        return input;
    }

    /** The defaults with the caller's fields laid over them. */
    private static Metadata merge(Metadata base, MetadataInput input) {
        if (input == null) {
            return base;
        }
        String playlist = input.playlist() == null ? base.playlist()
                : input.playlist().isBlank() ? null : input.playlist();
        return new Metadata(
                input.title() == null ? base.title() : input.title(),
                input.description() == null ? base.description() : input.description(),
                input.tags() == null ? base.tags() : input.tags().stream().distinct().toList(),
                playlist);
    }

    private static Instant instantOf(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeException malformed) {
            throw unprocessable(field + ": erlaubt ist ein ISO-8601-Zeitpunkt in UTC, z. B. 2026-11-08T00:00:00Z.");
        }
    }

    private static Status statusOf(String value) {
        try {
            return Status.valueOf(value);
        } catch (IllegalArgumentException unknown) {
            throw unprocessable("status: unbekannter Status.");
        }
    }

    private static boolean isHttpsUrl(String value) {
        if (value.length() > MAX_PLAYLIST_URL) {
            return false;
        }
        try {
            URI uri = new URI(value);
            return "https".equals(uri.getScheme()) && uri.getHost() != null;
        } catch (URISyntaxException malformed) {
            return false;
        }
    }

    private static void rejectControlCharacters(String value, String field) {
        if (CONTROL_OR_FORMAT.matcher(value).find()) {
            throw unprocessable(field + ": Steuer- und Formatzeichen sind nicht erlaubt.");
        }
    }

    private static ProblemException unprocessable(String detail) {
        return new ProblemException(422, "Unprocessable Content", detail);
    }

    private static ProblemException conflict(String detail) {
        return new ProblemException(409, "Conflict", detail);
    }
}
