package net.onelitefeather.menura.server.prototype.pool;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.server.prototype.DemoStore;
import net.onelitefeather.menura.server.prototype.pool.Asset.Kind;
import net.onelitefeather.menura.server.prototype.pool.Asset.Stage;
import net.onelitefeather.menura.server.prototype.pool.Asset.Transcript;
import net.onelitefeather.menura.server.prototype.pool.PoolModel.AssetDetail;
import net.onelitefeather.menura.server.prototype.pool.PoolModel.CreateRequest;
import net.onelitefeather.menura.server.prototype.pool.PoolModel.TagsRequest;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The rules of the pool: filtering and sorting, the transcript and clip lookups, and the two writes (tags and
 * simulated uploads). Errors are Problem Details with fixed German texts that never echo the input. A signed-in
 * subject is mapped to a person on its first write, under the name "Ich".
 */
@Singleton
@Requires(env = "prototype")
public class PoolService {

    /** The most items one list answers. */
    static final int LIST_LIMIT = 200;
    static final int MAX_TAGS = 20;
    static final int MAX_TITLE_LENGTH = 120;
    static final long MAX_DURATION_SECONDS = 86_400L;
    /** The owner filter value that stands for the signed-in person. */
    static final String ME = "me";
    /** Same size estimate as {@code PoolSeed}: about 6 Mbit/s of source footage. */
    private static final long BYTES_PER_SECOND = 740_000L;
    private static final Pattern TAG = Pattern.compile("[a-z0-9-]{2,30}");
    private static final Pattern UNPRINTABLE = Pattern.compile("[\\p{Cc}\\p{Cf}]");
    private static final Comparator<Asset> NEWEST_FIRST = Comparator.comparing(Asset::createdAt).reversed();

    /** The list filters as received; null means no filter. */
    public record Query(String kind, String ownerId, String personId, String tag, String stage, String q) {
    }

    private final AssetRepository repository;
    private final DemoStore store;
    private final Clock clock;

    public PoolService(AssetRepository repository, DemoStore store, Clock clock) {
        this.repository = repository;
        this.store = store;
        this.clock = clock;
    }

    public List<Person> people() {
        return repository.people();
    }

    /**
     * The assets matching every given filter, newest first, at most {@link #LIST_LIMIT}. The text filter matches
     * the title, the tags and the text of a ready transcript, ignoring case. {@code ownerId=me} matches the assets of
     * the signed-in subject's person; a subject without a person owns nothing.
     */
    public List<Asset> list(Query query, Optional<String> subject) {
        Kind kind = parseEnum(query.kind(), Kind.class);
        Stage stage = parseEnum(query.stage(), Stage.class);
        String owner = query.ownerId();
        if (ME.equals(owner)) {
            Optional<String> own = subject.flatMap(repository::personIdOf);
            if (own.isEmpty()) {
                return List.of();
            }
            owner = own.get();
        }
        String tag = query.tag() == null ? null : query.tag().toLowerCase(Locale.ROOT);
        String text = query.q() == null ? "" : query.q().trim().toLowerCase(Locale.ROOT);
        String ownerId = owner;
        return repository.all().stream()
                .filter(asset -> kind == null || asset.kind() == kind)
                .filter(asset -> ownerId == null || ownerId.equals(asset.ownerId()))
                .filter(asset -> query.personId() == null || asset.people().contains(query.personId()))
                .filter(asset -> tag == null || asset.tags().contains(tag))
                .filter(asset -> stage == null || asset.stage() == stage)
                .filter(asset -> text.isEmpty() || matchesText(asset, text))
                .sorted(NEWEST_FIRST)
                .limit(LIST_LIMIT)
                .toList();
    }

    public Asset find(String id) {
        return repository.find(id).orElseThrow(() -> new ProblemException(404, "Not Found", "Unbekanntes Asset."));
    }

    /** The asset with its transcript (empty unless ready) and the ids of the assets cut from it, newest first. */
    public AssetDetail detail(String id) {
        Asset asset = find(id);
        List<String> clips = repository.all().stream()
                .filter(candidate -> id.equals(candidate.sourceAssetId()))
                .sorted(NEWEST_FIRST)
                .map(Asset::id)
                .toList();
        return AssetDetail.of(asset, transcriptOf(asset), clips);
    }

    /** Replaces the people and tags of an asset. Both lists are required; an unknown person or a bad tag is a 422. */
    public Asset updateTags(String id, TagsRequest body, String subject) {
        Asset asset = find(id);
        if (body == null || body.tags() == null || body.people() == null) {
            throw unprocessable("Tags und Personen sind erforderlich.");
        }
        List<String> tags = validTags(body.tags());
        List<String> people = validPeople(body.people());
        personOf(subject);
        return repository.save(asset.withPeopleAndTags(people, tags));
    }

    /**
     * Simulates an upload: stores a new asset owned by the signed-in person. Kind raw starts at stage raw, kind clip at
     * stage cut; the transcript is pending. A full pool is a 422.
     */
    public Asset create(CreateRequest body, String subject) {
        if (body == null) {
            throw unprocessable("Angaben sind erforderlich.");
        }
        Kind kind = kindOf(body.kind());
        String title = titleOf(body.title());
        List<String> tags = validTags(body.tags() == null ? List.of() : body.tags());
        List<String> people = validPeople(body.people() == null ? List.of() : body.people());
        long duration = durationOf(body.durationSeconds());
        String seriesId = seriesOf(body.seriesId());
        Person owner = personOf(subject);
        Asset asset = new Asset(repository.nextAssetId(), kind, title, owner.id(), people, tags, duration,
                clock.instant(), null, seriesId, kind == Kind.raw ? Stage.raw : Stage.cut, Transcript.pending,
                duration * BYTES_PER_SECOND);
        try {
            return repository.save(asset);
        } catch (AssetCapacityExceededException full) {
            throw unprocessable("Der Pool ist voll");
        }
    }

    /** The person of the subject; the first write of an unknown subject creates the person "Ich". */
    Person personOf(String subject) {
        return repository.ensurePersonOf(subject, personIdFor(subject), "Ich");
    }

    /** A stable id for a subject, so the same subject gets the same id on every start. */
    static String personIdFor(String subject) {
        return "p-" + UUID.nameUUIDFromBytes(subject.getBytes(StandardCharsets.UTF_8)).toString().substring(0, 8);
    }

    /** The ready transcript of an asset; empty for every other transcript state. */
    private List<TranscriptSegment> transcriptOf(Asset asset) {
        return asset.transcript() == Transcript.ready ? repository.transcript(asset.id()) : List.of();
    }

    private boolean matchesText(Asset asset, String needle) {
        if (asset.title().toLowerCase(Locale.ROOT).contains(needle)) {
            return true;
        }
        if (asset.tags().stream().anyMatch(tag -> tag.contains(needle))) {
            return true;
        }
        return transcriptOf(asset).stream()
                .anyMatch(segment -> segment.text().toLowerCase(Locale.ROOT).contains(needle));
    }

    private static <E extends Enum<E>> E parseEnum(String raw, Class<E> type) {
        if (raw == null) {
            return null;
        }
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equals(raw)) {
                return constant;
            }
        }
        throw unprocessable("Ungültiger Filterwert.");
    }

    private static Kind kindOf(String raw) {
        if (raw == null) {
            throw unprocessable("Ungültige Art.");
        }
        for (Kind kind : Kind.values()) {
            if (kind.name().equals(raw)) {
                return kind;
            }
        }
        throw unprocessable("Ungültige Art.");
    }

    private static String titleOf(String raw) {
        if (raw == null) {
            throw unprocessable("Ungültiger Titel.");
        }
        String title = raw.trim();
        if (title.isEmpty() || title.length() > MAX_TITLE_LENGTH || UNPRINTABLE.matcher(title).find()) {
            throw unprocessable("Ungültiger Titel.");
        }
        return title;
    }

    private static List<String> validTags(List<String> raw) {
        if (raw.size() > MAX_TAGS) {
            throw unprocessable("Zu viele Tags.");
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String tag : raw) {
            if (tag == null || !TAG.matcher(tag).matches()) {
                throw unprocessable("Ungültiger Tag.");
            }
            unique.add(tag);
        }
        return List.copyOf(unique);
    }

    private List<String> validPeople(List<String> raw) {
        Set<String> unique = new LinkedHashSet<>();
        for (String personId : raw) {
            if (personId == null || repository.person(personId).isEmpty()) {
                throw unprocessable("Unbekannte Person.");
            }
            unique.add(personId);
        }
        return List.copyOf(unique);
    }

    private static long durationOf(Long raw) {
        if (raw == null || raw < 1 || raw > MAX_DURATION_SECONDS) {
            throw unprocessable("Ungültige Dauer.");
        }
        return raw;
    }

    private String seriesOf(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        boolean known = store.series().stream().anyMatch(series -> series.id().equals(raw));
        if (!known) {
            throw unprocessable("Unbekannte Serie.");
        }
        return raw;
    }

    private static ProblemException unprocessable(String detail) {
        return new ProblemException(422, "Unprocessable Content", detail);
    }
}
