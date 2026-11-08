package net.onelitefeather.menura.server.brand;

import net.onelitefeather.menura.core.brand.BrandKit;
import net.onelitefeather.menura.core.brand.BrandKitEvents;
import net.onelitefeather.menura.core.brand.BrandKit.ColourToken;
import net.onelitefeather.menura.core.brand.InitialBrandKit;
import net.onelitefeather.menura.core.auth.Role;
import net.onelitefeather.menura.core.id.IdentifierGenerator;
import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.server.audit.AuditLog;
import net.onelitefeather.menura.server.audit.Channel;
import net.onelitefeather.menura.server.audit.InMemoryAuditRepository;
import net.onelitefeather.menura.server.auth.CurrentPerson;
import net.onelitefeather.menura.server.db.DirectTransactions;
import net.onelitefeather.menura.server.support.DeterministicRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The brand kit service on in-memory collaborators, each test with its own fixture and a fixed clock. */
class BrandKitsTest {

    private static final UUID EDITOR = UUID.fromString("0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d");
    private static final CurrentPerson EDITOR_PERSON = new CurrentPerson(EDITOR, "subject", Set.of(Role.EDITOR), Channel.WEB);

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-08T10:00:00Z"), ZoneOffset.UTC);
    private final InMemoryBrandKitRepository repository = new InMemoryBrandKitRepository();
    private final InMemoryAuditRepository audit = new InMemoryAuditRepository();
    private final DirectTransactions transactions = new DirectTransactions();
    private final IdentifierGenerator identifiers = new IdentifierGenerator(new DeterministicRandom());
    private final InMemoryBrandKitAssets assets = new InMemoryBrandKitAssets();
    private final RecordingEvents events = new RecordingEvents();
    private final BrandKits kits = new BrandKits(transactions, new AuditLog(transactions, clock, identifiers, audit),
            repository, assets, events, clock);

    @BeforeEach
    void startEmpty() {
        repository.clear();
        assets.clear();
    }

    @Test
    void aPublicationNotifiesTheEventSinkOnceWithTheVersionAndTheClockTime() throws SQLException {
        kits.publish(EDITOR_PERSON, changedPrimary("#2a1a5e"), "req-n1");

        assertEquals(List.of(new BrandKitEvents.KitPublished(2, clock.instant())), events.published,
                "one notification naming version 2, stamped with the injected clock");
    }

    @Test
    void aRefusedPublicationNotifiesNobody() {
        assertThrows(ProblemException.class, () -> kits.publish(EDITOR_PERSON, withSurfaceWithoutDark(), "req-n2"));

        assertEquals(List.of(), events.published, "a refused publication emits no notification");
    }

    @Test
    void aFontOrMarkThatIsNotAVerifiedAssetRefusesThePublicationAndNamesIt() {
        UUID font = UUID.fromString("3f1c2a4e-5b6d-4c7e-9f8a-0b1c2d3e4f50");
        BrandKit kit = withFont(font.toString());

        ProblemException refused = assertThrows(ProblemException.class, () -> kits.publish(EDITOR_PERSON, kit, "req-a1"));

        assertEquals(422, refused.status());
        assertTrue(refused.detail().contains(font.toString()), "the detail names the unknown asset: " + refused.detail());
    }

    @Test
    void aFontThatIsAVerifiedAssetPublishes() throws SQLException {
        UUID font = UUID.fromString("3f1c2a4e-5b6d-4c7e-9f8a-0b1c2d3e4f50");
        assets.verify(font);

        StoredKitVersion published = kits.publish(EDITOR_PERSON, withFont(font.toString()), "req-a2");

        assertEquals(2, published.version(), "a verified asset is accepted and the version is stored");
    }

    static final class RecordingEvents implements BrandKitEvents {
        final List<BrandKitEvents.KitPublished> published = new ArrayList<>();

        @Override
        public void published(BrandKitEvents.KitPublished event) {
            published.add(event);
        }
    }

    private static BrandKit withFont(String asset) {
        BrandKit base = InitialBrandKit.create();
        return new BrandKit(base.colours(), base.ramps(), base.textRoles(),
                List.of(new BrandKit.TypographyRole("body", asset, List.of(32))), base.marks(), base.voice(),
                base.texts(), base.templates());
    }

    @Test
    void theFirstTouchSeedsVersionOneFromTheOverlayTheme() throws SQLException {
        StoredKitVersion first = kits.current();

        assertEquals(1, first.version(), "the seed is version 1");
        assertEquals(new ColourToken("primary", "#6638e4", "#ccbdff"), first.kit().colour("primary").orElseThrow(),
                "the seed carries the overlay theme roles");
        assertEquals(BrandKits.SYSTEM_PERSON, first.publishedBy(), "the system, not a person, publishes the seed");
    }

    @Test
    void seedingWritesOnlyOnce() throws SQLException {
        kits.current();
        kits.current();

        assertEquals(1, repository.latest(null).orElseThrow().version(), "the second read finds the seed and writes nothing");
        assertEquals(List.of(), audit.entries(), "the seed writes no audit entry: the audit log has no system actor");
    }

    @Test
    void aPublicationCreatesANewVersionAndTheEarlierVersionStaysReadable() throws SQLException {
        StoredKitVersion published = kits.publish(EDITOR_PERSON, changedPrimary("#2a1a5e"), "req-1");

        assertEquals(2, published.version(), "a publication after the seed is version 2");
        assertEquals("#2a1a5e", published.kit().colour("primary").orElseThrow().light(), "the change is in version 2");
        assertEquals("#6638e4", kits.resolve(1, null).kit().colour("primary").orElseThrow().light(),
                "version 1 is still readable unchanged");
    }

    @Test
    void aRefusedPublicationCreatesNoVersionAndNoAuditEntry() throws SQLException {
        kits.current();
        int auditBefore = audit.entries().size();
        BrandKit refused = withSurfaceWithoutDark();

        assertThrows(ProblemException.class, () -> kits.publish(EDITOR_PERSON, refused, "req-2"));

        assertEquals(1, repository.latest(null).orElseThrow().version(), "no new version is stored");
        assertEquals(auditBefore, audit.entries().size(), "a refused publication writes no audit entry");
    }

    @Test
    void aPublicationWritesOneAuditEntryOnTheSameConnectionAsTheVersion() throws SQLException {
        kits.publish(EDITOR_PERSON, changedPrimary("#2a1a5e"), "req-3");

        int index = indexOfAction("brand_kit.publish");
        assertEquals(EDITOR, audit.entries().get(index).personId(), "the audit entry names the editor");
        assertEquals("req-3", audit.entries().get(index).requestId(), "the audit entry carries the request id");
        assertSame(transactions.connections().get(transactions.connections().size() - 1), audit.connections().get(index),
                "the entry is written on the transaction of the publication");
    }

    private int indexOfAction(String action) {
        for (int index = 0; index < audit.entries().size(); index++) {
            if (audit.entries().get(index).action().equals(action)) {
                return index;
            }
        }
        throw new AssertionError("no audit entry for " + action);
    }

    @Test
    void aPinnedConsumerKeepsItsVersionAfterANewPublication() throws SQLException {
        kits.pin(EDITOR_PERSON, "overlay-scene", 1, "req-4");

        kits.publish(EDITOR_PERSON, changedPrimary("#2a1a5e"), "req-5");

        assertEquals(1, kits.resolve(null, "overlay-scene").version(), "the pinned overlay still gets version 1");
    }

    @Test
    void anUnpinnedConsumerReceivesTheLatestVersion() throws SQLException {
        kits.publish(EDITOR_PERSON, changedPrimary("#2a1a5e"), "req-6");

        assertEquals(2, kits.resolve(null, "obs-plugin").version(), "a consumer without a pin follows the newest version");
    }

    @Test
    void unpinningMakesAConsumerFollowTheLatestVersionAgain() throws SQLException {
        kits.publish(EDITOR_PERSON, changedPrimary("#2a1a5e"), "req-7");
        kits.pin(EDITOR_PERSON, "overlay-scene", 1, "req-8");

        kits.unpin(EDITOR_PERSON, "overlay-scene", "req-9");

        assertEquals(2, kits.resolve(null, "overlay-scene").version(), "without its pin the consumer follows the newest");
    }

    @Test
    void pinningAVersionThatDoesNotExistIsNotFound() throws SQLException {
        ProblemException refused = assertThrows(ProblemException.class,
                () -> kits.pin(EDITOR_PERSON, "overlay-scene", 42, "req-10"));

        assertEquals(404, refused.status(), "a pin to an unknown version names nothing and is refused");
    }

    @Test
    void aConsumerNameWithUpperCaseLettersIsRefused() {
        ProblemException refused = assertThrows(ProblemException.class, () -> BrandKits.requireConsumer("Overlay"));

        assertEquals(400, refused.status(), "an invalid consumer name is a bad request");
    }

    @Test
    void aNamedVersionThatDoesNotExistIsNotFound() {
        ProblemException refused = assertThrows(ProblemException.class, () -> kits.resolve(99, null));

        assertEquals(404, refused.status(), "an unknown version is not found");
    }

    @Test
    void theInitialDraftIsPublishableAsItIs() throws SQLException {
        StoredKitVersion published = kits.publish(EDITOR_PERSON, InitialBrandKit.create(), "req-11");

        assertFalse(published.kit().ramps().get(0).tones().isEmpty(), "the publication derives the tones");
        assertNotEquals(1, published.version(), "the publication is version 2, after the seed");
    }

    private static BrandKit changedPrimary(String light) {
        BrandKit base = InitialBrandKit.create();
        List<ColourToken> colours = new ArrayList<>(base.colours());
        colours.replaceAll(token -> token.name().equals("primary") ? new ColourToken("primary", light, token.dark()) : token);
        return new BrandKit(colours, base.ramps(), base.textRoles(), base.typography(), base.marks(), base.voice(),
                base.texts(), base.templates());
    }

    private static BrandKit withSurfaceWithoutDark() {
        BrandKit base = InitialBrandKit.create();
        List<ColourToken> colours = new ArrayList<>(base.colours());
        colours.replaceAll(token -> token.name().equals("surface") ? new ColourToken("surface", "#fffbff", null) : token);
        return new BrandKit(colours, base.ramps(), base.textRoles(), base.typography(), base.marks(), base.voice(),
                base.texts(), base.templates());
    }
}
