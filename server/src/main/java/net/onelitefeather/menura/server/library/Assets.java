package net.onelitefeather.menura.server.library;

import jakarta.inject.Singleton;
import net.onelitefeather.menura.core.db.Transactions;
import net.onelitefeather.menura.core.paging.Cursor;
import net.onelitefeather.menura.core.paging.Page;
import net.onelitefeather.menura.core.paging.PageRequest;
import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.server.audit.AuditLog;
import net.onelitefeather.menura.server.auth.CurrentPerson;
import net.onelitefeather.menura.server.web.ObjectRepository;
import net.onelitefeather.menura.server.web.VisibilityGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Listing, preview and download links, and deletion of assets. A hidden asset answers 404 for every operation. Links
 * are signed for one object and expire after the configured short lifetime; deletion is an explicit, audited request
 * that the reference check can refuse.
 */
@Singleton
public class Assets {

    private static final Logger LOG = LoggerFactory.getLogger(Assets.class);
    private static final String CURSOR_SEPARATOR = "|";

    private final Transactions transactions;
    private final ObjectStorage storage;
    private final AssetRepository assets;
    private final AssetReferences references;
    private final AuditLog auditLog;
    private final Duration linkLifetime;
    private final VisibilityGuard<UUID, AssetRecord> guard;

    public Assets(Transactions transactions, ObjectStorage storage, AssetRepository assets, AssetReferences references,
                  AuditLog auditLog, LibrarySettings settings) {
        this.transactions = transactions;
        this.storage = storage;
        this.assets = assets;
        this.references = references;
        this.auditLog = auditLog;
        this.linkLifetime = settings.linkLifetime();
        this.guard = new VisibilityGuard<>(assetRepository(), "asset");
    }

    /** One page of the assets the viewer may see, newest first. */
    public AssetPageView list(CurrentPerson viewer, PageRequest request) throws SQLException {
        Instant after = null;
        UUID afterId = null;
        if (request.afterKey() != null) {
            String[] position = request.afterKey().split("\\" + CURSOR_SEPARATOR, -1);
            if (position.length != 2) {
                throw new ProblemException(400, "Invalid cursor", "The cursor is not valid for this collection.");
            }
            after = parseInstant(position[0]);
            afterId = parseUuid(position[1]);
        }
        boolean admin = AssetAccess.isAdmin(viewer);
        Instant seek = after;
        UUID seekId = afterId;
        int fetch = request.limit() + 1;
        List<AssetRecord> rows = transactions.inTransaction(connection ->
                assets.page(connection, seek, seekId, fetch, viewer.personId(), admin));
        boolean more = rows.size() > request.limit();
        List<AssetRecord> items = more ? rows.subList(0, request.limit()) : rows;
        String next = null;
        if (more) {
            AssetRecord last = items.get(items.size() - 1);
            next = Cursor.encode(last.createdAt() + CURSOR_SEPARATOR + last.id());
        }
        Page<AssetRecord> page = new Page<>(items, next);
        return new AssetPageView(page.items().stream().map(Assets::view).toList(), page.nextCursor());
    }

    /** One asset, visible to the viewer or answered 404. */
    public AssetView get(CurrentPerson viewer, UUID id) {
        return view(visible(viewer, id));
    }

    /** A short-lived link to preview the asset inline, or to download it as an attachment. */
    public SignedLinkView link(CurrentPerson viewer, UUID id, boolean attachment) {
        AssetRecord asset = visible(viewer, id);
        ObjectStorage.SignedUrl url = storage.presignGet(asset.storageKey(), asset.id() + "." + asset.declaration().format(),
                attachment, linkLifetime);
        return new SignedLinkView(url.url(), url.expiresAt());
    }

    /**
     * Deletes an asset that the caller may see and that no post references. The row and its single audit entry are
     * written in one transaction; a refusal rolls back and writes no entry. The object is removed afterwards.
     */
    public void delete(CurrentPerson viewer, UUID id, String requestId) throws SQLException {
        AssetRecord asset = visible(viewer, id);
        auditLog.write(AssetUploads.caller(viewer), "asset.delete", "asset:" + id, requestId, connection -> {
            Optional<UUID> post = references.findReferencingPost(connection, id);
            if (post.isPresent()) {
                throw new ProblemException(409, "Asset In Use",
                        "The asset is referenced by the post " + post.get() + " and cannot be deleted.");
            }
            assets.delete(connection, id);
            return null;
        });
        try {
            storage.delete(asset.storageKey());
        } catch (ProblemException failure) {
            LOG.warn("The object of deleted asset {} could not be removed from the storage", id);
        }
    }

    private AssetRecord visible(CurrentPerson viewer, UUID id) {
        return guard.read(id, AssetAccess.assetAccess(viewer));
    }

    private ObjectRepository<UUID, AssetRecord> assetRepository() {
        return id -> {
            try {
                return transactions.inTransaction(connection -> assets.find(connection, id));
            } catch (SQLException failure) {
                throw new IllegalStateException("asset lookup failed", failure);
            }
        };
    }

    static AssetView view(AssetRecord asset) {
        var declaration = asset.declaration();
        return new AssetView(asset.id(), declaration.kind().value(), declaration.format(), declaration.durationSeconds(),
                declaration.width(), declaration.height(), declaration.sizeBytes(), declaration.checksum().value(),
                asset.uploaderId(), declaration.streamTitle(), declaration.streamDate(), asset.createdAt());
    }

    private static Instant parseInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (RuntimeException invalid) {
            throw new ProblemException(400, "Invalid cursor", "The cursor is not valid for this collection.");
        }
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException invalid) {
            throw new ProblemException(400, "Invalid cursor", "The cursor is not valid for this collection.");
        }
    }
}
