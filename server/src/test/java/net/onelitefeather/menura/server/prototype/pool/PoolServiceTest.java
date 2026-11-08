package net.onelitefeather.menura.server.prototype.pool;

import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.server.prototype.DemoStore;
import net.onelitefeather.menura.server.prototype.pool.Asset.Kind;
import net.onelitefeather.menura.server.prototype.pool.Asset.Stage;
import net.onelitefeather.menura.server.prototype.pool.Asset.Transcript;
import net.onelitefeather.menura.server.prototype.pool.PoolModel.AssetDetail;
import net.onelitefeather.menura.server.prototype.pool.PoolModel.CreateRequest;
import net.onelitefeather.menura.server.prototype.pool.PoolModel.TagsRequest;
import net.onelitefeather.menura.server.prototype.pool.PoolService.Query;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every test builds its own repository and service on a fixed clock, so no test sees another test's writes. */
class PoolServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-08T12:00:00Z");
    private static final Optional<String> NOBODY = Optional.empty();

    private static PoolService service() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        return new PoolService(new AssetRepository(), new DemoStore(clock), clock);
    }

    private static Query none() {
        return new Query(null, null, null, null, null, null);
    }

    private static CreateRequest rawUpload(String title) {
        return new CreateRequest("raw", title, List.of("ebpf"), List.of("p-a"), 600L, null);
    }

    private static TagsRequest tags(List<String> tags, List<String> people) {
        return new TagsRequest(tags, people);
    }

    private static ProblemException refusal(Runnable action) {
        return assertThrows(ProblemException.class, action::run);
    }

    @Test
    void kindFilterKeepsOnlyTheRequestedKind() {
        List<Asset> raw = service().list(new Query("raw", null, null, null, null, null), NOBODY);

        assertFalse(raw.isEmpty());
        assertTrue(raw.stream().allMatch(asset -> asset.kind() == Kind.raw), "every result is raw material");
    }

    @Test
    void ownerFilterKeepsOnlyTheAssetsOfThatOwner() {
        List<Asset> owned = service().list(new Query(null, "p-b", null, null, null, null), NOBODY);

        assertFalse(owned.isEmpty());
        assertTrue(owned.stream().allMatch(asset -> asset.ownerId().equals("p-b")), "every result is owned by p-b");
    }

    @Test
    void personFilterKeepsOnlyAssetsWithThatPersonInThem() {
        List<Asset> withD = service().list(new Query(null, null, "p-d", null, null, null), NOBODY);

        assertFalse(withD.isEmpty());
        assertTrue(withD.stream().allMatch(asset -> asset.people().contains("p-d")), "p-d appears in every result");
    }

    @Test
    void tagFilterKeepsOnlyAssetsWithThatTag() {
        List<Asset> cilium = service().list(new Query(null, null, null, "cilium", null, null), NOBODY);

        assertFalse(cilium.isEmpty());
        assertTrue(cilium.stream().allMatch(asset -> asset.tags().contains("cilium")), "every result has the tag");
    }

    @Test
    void stageFilterKeepsOnlyTheRequestedStage() {
        List<Asset> rendered = service().list(new Query(null, null, null, null, "rendered", null), NOBODY);

        assertEquals(6, rendered.size(), "the seed holds six rendered clips");
        assertTrue(rendered.stream().allMatch(asset -> asset.stage() == Stage.rendered), "every result is rendered");
    }

    @Test
    void textFilterMatchesTheTitleIgnoringCase() {
        List<Asset> found = service().list(new Query(null, null, null, null, null, "CILIUM-UMBAU"), NOBODY);

        assertTrue(found.stream().anyMatch(asset -> asset.id().equals("a-102")), "the title of a-102 matches");
    }

    @Test
    void textFilterMatchesTheTags() {
        List<Asset> found = service().list(new Query(null, null, null, null, null, "ebpf"), NOBODY);

        assertTrue(found.stream().anyMatch(asset -> asset.id().equals("a-101")), "the tag of a-101 matches");
    }

    @Test
    void textFilterMatchesTheTextOfAReadyTranscript() {
        List<Asset> found = service().list(new Query(null, null, null, null, null, "Stabilisierungsfenster"), NOBODY);

        assertTrue(found.stream().anyMatch(asset -> asset.id().equals("a-100")),
                "the transcript of a-100 names the stabilisation window");
    }

    @Test
    void meFilterIsEmptyForASubjectWithoutAPerson() {
        List<Asset> mine = service().list(new Query(null, PoolService.ME, null, null, null, null), Optional.of("nobody"));

        assertTrue(mine.isEmpty(), "a subject without a person owns nothing");
    }

    @Test
    void meFilterReturnsTheAssetsOfTheSignedInPerson() {
        PoolService service = service();
        Asset uploaded = service.create(rawUpload("Mein Upload"), "subject-one");

        List<Asset> mine = service.list(new Query(null, PoolService.ME, null, null, null, null),
                Optional.of("subject-one"));
        List<Asset> others = service.list(new Query(null, PoolService.ME, null, null, null, null),
                Optional.of("subject-two"));

        assertEquals(List.of(uploaded.id()), mine.stream().map(Asset::id).toList());
        assertTrue(others.isEmpty(), "another subject does not see the upload");
    }

    @Test
    void theFirstWriteCreatesThePersonIchForTheSubject() {
        PoolService service = service();

        service.create(rawUpload("Erster Upload"), "subject-new");

        Person ich = service.people().stream().filter(person -> person.name().equals("Ich")).findFirst().orElseThrow();
        assertEquals(PoolService.personIdFor("subject-new"), ich.id());
    }

    @Test
    void theListIsSortedNewestFirst() {
        List<Asset> all = service().list(none(), NOBODY);

        for (int index = 1; index < all.size(); index++) {
            assertFalse(all.get(index).createdAt().isAfter(all.get(index - 1).createdAt()),
                    "createdAt does not rise from one item to the next");
        }
    }

    @Test
    void aListAnswersAtMostTwoHundredItems() {
        PoolService service = service();
        IntStream.range(0, 200).forEach(index -> service.create(rawUpload("Fülle " + index), "subject-bulk"));

        assertEquals(PoolService.LIST_LIMIT, service.list(none(), NOBODY).size());
    }

    @Test
    void anUnknownKindIsRefusedWith422() {
        ProblemException refused = refusal(() -> service().list(new Query("video", null, null, null, null, null), NOBODY));

        assertEquals(422, refused.status());
    }

    @Test
    void theDetailNamesTheClipsCutFromTheAsset() {
        AssetDetail detail = service().detail("a-100");

        assertTrue(detail.clips().contains("a-110"), "a-110 is cut from a-100");
        assertFalse(detail.clips().contains("a-104"), "a-104 is cut from a-102, not from a-100");
    }

    @Test
    void theDetailHasTranscriptSegmentsWhenTheTranscriptIsReady() {
        AssetDetail detail = service().detail("a-100");

        assertEquals(Transcript.ready, detail.transcript());
        assertFalse(detail.transcriptSegments().isEmpty(), "a ready transcript has its segments");
    }

    @Test
    void theDetailHasNoTranscriptSegmentsWhileTheTranscriptIsPending() {
        AssetDetail detail = service().detail("a-107");

        assertEquals(Transcript.pending, detail.transcript());
        assertTrue(detail.transcriptSegments().isEmpty(), "a pending transcript shows no segments");
    }

    @Test
    void theDetailOfAnUnknownAssetIsA404() {
        ProblemException refused = refusal(() -> service().detail("a-999"));

        assertEquals(404, refused.status());
    }

    @Test
    void updatingTagsReplacesTagsAndPeopleAndKeepsTheRest() {
        PoolService service = service();
        Asset before = service.find("a-104");

        Asset after = service.updateTags("a-104", tags(List.of("ebpf"), List.of("p-a")), "subject-editor");

        assertEquals(List.of("ebpf"), after.tags());
        assertEquals(List.of("p-a"), after.people());
        assertEquals(before.title(), after.title(), "the title is not part of the edit");
        assertEquals(before.ownerId(), after.ownerId(), "the owner is not part of the edit");
    }

    @Test
    void anUppercaseTagIsRefusedWith422() {
        PoolService service = service();

        ProblemException refused = refusal(() -> service.updateTags("a-104", tags(List.of("Cilium"), List.of("p-c")),
                "subject-editor"));

        assertEquals(422, refused.status());
    }

    @Test
    void aControlCharacterInATagIsRefusedWith422() {
        PoolService service = service();

        ProblemException refused = refusal(() -> service.updateTags("a-104",
                tags(List.of("cil\u0007ium"), List.of("p-c")), "subject-editor"));

        assertEquals(422, refused.status());
    }

    @Test
    void twentyOneTagsAreRefusedWith422() {
        PoolService service = service();
        List<String> tooMany = IntStream.range(0, 21).mapToObj(index -> "tag-" + index).toList();

        ProblemException refused = refusal(() -> service.updateTags("a-104", tags(tooMany, List.of("p-c")),
                "subject-editor"));

        assertEquals(422, refused.status());
    }

    @Test
    void anUnknownPersonIsRefusedWith422() {
        PoolService service = service();

        ProblemException refused = refusal(() -> service.updateTags("a-104", tags(List.of("ebpf"), List.of("p-zz")),
                "subject-editor"));

        assertEquals(422, refused.status());
    }

    @Test
    void updatingTheTagsOfAnUnknownAssetIsA404() {
        PoolService service = service();

        ProblemException refused = refusal(() -> service.updateTags("a-999", tags(List.of("ebpf"), List.of()),
                "subject-editor"));

        assertEquals(404, refused.status());
    }

    @Test
    void aRawUploadStartsAtStageRawWithAPendingTranscript() {
        Asset created = service().create(rawUpload("Neues Rohmaterial"), "subject-upload");

        assertEquals(Kind.raw, created.kind());
        assertEquals(Stage.raw, created.stage());
        assertEquals(Transcript.pending, created.transcript());
        assertEquals(NOW, created.createdAt(), "the upload time comes from the injected clock");
        assertEquals(600L * 740_000L, created.sizeBytes());
    }

    @Test
    void aClipUploadStartsAtStageCut() {
        CreateRequest clip = new CreateRequest("clip", "Neuer Clip", List.of("ebpf"), List.of("p-b"), 45L, "k8s-30s");

        Asset created = service().create(clip, "subject-upload");

        assertEquals(Kind.clip, created.kind());
        assertEquals(Stage.cut, created.stage());
        assertEquals("k8s-30s", created.seriesId());
    }

    @Test
    void anUnknownUploadKindIsRefusedWith422() {
        PoolService service = service();

        ProblemException refused = refusal(() -> service.create(
                new CreateRequest("video", "Titel", List.of(), List.of(), 60L, null), "subject-upload"));

        assertEquals(422, refused.status());
    }

    @Test
    void aTitleWithAFormatCharacterIsRefusedWith422() {
        PoolService service = service();

        ProblemException refused = refusal(() -> service.create(
                new CreateRequest("raw", "Titel‮falsch", List.of(), List.of(), 60L, null), "subject-upload"));

        assertEquals(422, refused.status());
    }

    @Test
    void aTitleLongerThan120CharactersIsRefusedWith422() {
        PoolService service = service();

        ProblemException refused = refusal(() -> service.create(
                new CreateRequest("raw", "x".repeat(121), List.of(), List.of(), 60L, null), "subject-upload"));

        assertEquals(422, refused.status());
    }

    @Test
    void aZeroDurationIsRefusedWith422() {
        PoolService service = service();

        ProblemException refused = refusal(() -> service.create(
                new CreateRequest("raw", "Titel", List.of(), List.of(), 0L, null), "subject-upload"));

        assertEquals(422, refused.status());
    }

    @Test
    void aDurationAboveTwentyFourHoursIsRefusedWith422() {
        PoolService service = service();

        ProblemException refused = refusal(() -> service.create(
                new CreateRequest("raw", "Titel", List.of(), List.of(), 86_401L, null), "subject-upload"));

        assertEquals(422, refused.status());
    }

    @Test
    void anUnknownSeriesIsRefusedWith422() {
        PoolService service = service();

        ProblemException refused = refusal(() -> service.create(
                new CreateRequest("clip", "Titel", List.of(), List.of(), 60L, "unbekannt"), "subject-upload"));

        assertEquals(422, refused.status());
    }

    @Test
    void anUploadIntoAFullPoolIsRefusedWith422() {
        AssetRepository repository = new AssetRepository();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        PoolService service = new PoolService(repository, new DemoStore(clock), clock);
        int free = AssetRepository.CAPACITY - repository.all().size();
        IntStream.range(0, free).forEach(index -> service.create(rawUpload("Fülle " + index), "subject-bulk"));

        ProblemException refused = refusal(() -> service.create(rawUpload("Zu viel"), "subject-bulk"));

        assertEquals(422, refused.status());
        assertEquals("Der Pool ist voll", refused.detail());
    }
}
