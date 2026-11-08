package net.onelitefeather.menura.server.library;

import net.onelitefeather.menura.core.paging.PageRequest;
import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.server.audit.AuditEntry;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static net.onelitefeather.menura.server.library.LibraryFixture.EDITOR;
import static net.onelitefeather.menura.server.library.LibraryFixture.compositeOf;
import static net.onelitefeather.menura.server.library.LibraryFixture.editor;
import static net.onelitefeather.menura.server.library.LibraryFixture.filled;
import static net.onelitefeather.menura.server.library.LibraryFixture.image;
import static net.onelitefeather.menura.server.library.LibraryFixture.otherEditor;
import static net.onelitefeather.menura.server.library.LibraryFixture.rawRecording;
import static net.onelitefeather.menura.server.library.LibraryFixture.viewer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetsTest {

    private static final Duration LINK_LIFETIME = Duration.ofMinutes(5);

    /** A finished image of the editor, listed. The seed makes each one's bytes, and so its checksum, distinct. */
    private static UUID finished(LibraryFixture f, long seed) throws SQLException {
        List<byte[]> bytes = List.of(filled(10, (int) seed));
        return f.uploadAsset(editor(), image(10, compositeOf(bytes)), bytes);
    }

    private static UUID raw(LibraryFixture f) throws SQLException {
        List<byte[]> bytes = List.of(filled(10, 7));
        return f.uploadAsset(editor(), rawRecording(10, compositeOf(bytes)), bytes);
    }

    @Test
    void listContainsOnlyTheAssetsTheViewerMaySee() throws SQLException {
        LibraryFixture f = new LibraryFixture();
        UUID shared = finished(f, 1);
        UUID ownRaw = raw(f);

        AssetPageView forViewer = f.library.list(viewer(), PageRequest.of(null, null));
        AssetPageView forOther = f.library.list(otherEditor(), PageRequest.of(null, null));
        AssetPageView forOwner = f.library.list(editor(), PageRequest.of(null, null));

        assertEquals(List.of(shared), ids(forViewer), "a viewer sees the finished asset and no raw material");
        assertEquals(List.of(shared), ids(forOther), "another editor does not see raw material of someone else");
        assertEquals(2, forOwner.items().size(), "the uploader sees their own raw material");
        assertTrue(ids(forOwner).contains(ownRaw));
    }

    @Test
    void listPagesNewestFirstWithAnOpaqueCursor() throws SQLException {
        LibraryFixture f = new LibraryFixture();
        UUID first = finished(f, 1);
        f.clock.advance(Duration.ofSeconds(1));
        UUID second = finished(f, 2);
        f.clock.advance(Duration.ofSeconds(1));
        UUID third = finished(f, 3);

        AssetPageView page1 = f.library.list(viewer(), PageRequest.of(2, null));
        AssetPageView page2 = f.library.list(viewer(), PageRequest.of(2, page1.nextCursor()));

        assertEquals(List.of(third, second), ids(page1), "the newest assets come first");
        assertNotNull(page1.nextCursor(), "a full page carries a cursor for the next one");
        assertEquals(List.of(first), ids(page2), "the next page continues after the cursor");
        assertNull(page2.nextCursor(), "the last page has no cursor");
    }

    @Test
    void hiddenRawMaterialAnswersNotFoundForGetPreviewAndDeletionAlike() throws SQLException {
        LibraryFixture f = new LibraryFixture();
        UUID hidden = raw(f);

        assertEquals(404, assertThrows(ProblemException.class, () -> f.library.get(otherEditor(), hidden)).status());
        assertEquals(404, assertThrows(ProblemException.class,
                () -> f.library.link(otherEditor(), hidden, false)).status());
        assertEquals(404, assertThrows(ProblemException.class,
                () -> f.library.delete(otherEditor(), hidden, "req-d")).status());
        assertEquals(1, f.assets.rows().size(), "a hidden asset is not deleted by the caller who may not see it");
    }

    @Test
    void previewLinkExpiresWithinTheConfiguredShortLifetime() throws SQLException {
        LibraryFixture f = new LibraryFixture();
        UUID asset = finished(f, 1);

        SignedLinkView link = f.library.link(viewer(), asset, false);

        assertEquals(f.clock.instant().plus(LINK_LIFETIME), link.expiresAt(), "the link lives for the configured lifetime");
        assertTrue(f.storage.accepts(link.url()), "a fresh link is served");
    }

    @Test
    void linkOlderThanItsLifetimeIsRefusedByTheStorage() throws SQLException {
        LibraryFixture f = new LibraryFixture();
        UUID asset = finished(f, 1);
        SignedLinkView link = f.library.link(viewer(), asset, false);

        f.clock.advance(Duration.ofMinutes(6));

        assertFalse(f.storage.accepts(link.url()), "the storage refuses a link older than its lifetime");
    }

    @Test
    void readingAssetsWritesNoAuditEntryAndDeletesNothing() throws SQLException {
        LibraryFixture f = new LibraryFixture();
        UUID asset = finished(f, 1);
        int auditBefore = f.audit.entries().size();

        f.library.list(viewer(), PageRequest.of(null, null));
        f.library.get(viewer(), asset);
        f.library.link(viewer(), asset, true);

        assertEquals(auditBefore, f.audit.entries().size(), "reads are not writes and leave no audit entry");
        assertEquals(1, f.assets.rows().size(), "reads delete nothing");
    }

    @Test
    void deletionRemovesAnUnreferencedAssetWithExactlyOneAuditEntry() throws SQLException {
        LibraryFixture f = new LibraryFixture();
        UUID asset = finished(f, 1);
        int auditBefore = f.audit.entries().size();

        f.library.delete(editor(), asset, "req-d");

        assertTrue(f.assets.rows().isEmpty(), "the asset is removed");
        assertEquals(auditBefore + 1, f.audit.entries().size(), "the deletion writes exactly one audit entry");
        AuditEntry entry = f.audit.entries().get(f.audit.entries().size() - 1);
        assertEquals("asset.delete", entry.action());
        assertEquals("asset:" + asset, entry.target());
        assertEquals(EDITOR, entry.personId());
        assertEquals("req-d", entry.requestId());
    }

    @Test
    void deletionRefusedWhileAPostReferencesTheAssetNamesThePostAndDeletesNothing() throws SQLException {
        LibraryFixture f = new LibraryFixture();
        UUID asset = finished(f, 1);
        UUID post = UUID.fromString("3d4e5f6a-7b8c-4d9e-8f0a-1b2c3d4e5f60");
        f.references.referencedBy(post);
        int auditBefore = f.audit.entries().size();

        ProblemException refused = assertThrows(ProblemException.class, () -> f.library.delete(editor(), asset, "req-d"));

        assertEquals(409, refused.status());
        assertTrue(refused.detail().contains(post.toString()), "the refusal names the referencing post");
        assertEquals(1, f.assets.rows().size(), "a referenced asset is not deleted");
        assertEquals(auditBefore, f.audit.entries().size(), "a refused deletion writes no audit entry");
    }

    private static List<UUID> ids(AssetPageView page) {
        return page.items().stream().map(AssetView::id).toList();
    }
}
