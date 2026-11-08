package net.onelitefeather.menura.server.library;

import jakarta.inject.Singleton;
import net.onelitefeather.menura.core.asset.AssetDeclaration;
import net.onelitefeather.menura.core.asset.PartPlan;
import net.onelitefeather.menura.core.asset.UploadLimits;
import net.onelitefeather.menura.core.db.Transactions;
import net.onelitefeather.menura.core.id.IdentifierGenerator;
import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.server.audit.AuditLog;
import net.onelitefeather.menura.server.auth.CurrentPerson;
import net.onelitefeather.menura.server.security.Caller;
import net.onelitefeather.menura.server.web.ObjectRepository;
import net.onelitefeather.menura.server.web.VisibilityGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The upload sessions of finished assets and raw material: start (or flag a known checksum), resume, confirm a
 * flagged upload, complete with the checksum check, and cancel. The server issues presigned part URLs and reads the
 * storage's part list; it never receives a media byte. Every write runs in one audited transaction.
 */
@Singleton
public class AssetUploads {

    private static final Logger LOG = LoggerFactory.getLogger(AssetUploads.class);

    private final Transactions transactions;
    private final Clock clock;
    private final IdentifierGenerator identifiers;
    private final ObjectStorage storage;
    private final UploadSessionRepository sessions;
    private final AssetRepository assets;
    private final AuditLog auditLog;
    private final UploadLimits limits;
    private final Duration uploadUrlLifetime;
    private final VisibilityGuard<UUID, UploadSession> guard;

    public AssetUploads(Transactions transactions, Clock clock, IdentifierGenerator identifiers, ObjectStorage storage,
                        UploadSessionRepository sessions, AssetRepository assets, AuditLog auditLog,
                        LibrarySettings settings) {
        this.transactions = transactions;
        this.clock = clock;
        this.identifiers = identifiers;
        this.storage = storage;
        this.sessions = sessions;
        this.assets = assets;
        this.auditLog = auditLog;
        this.limits = settings.limits();
        this.uploadUrlLifetime = settings.uploadUrlLifetime();
        this.guard = new VisibilityGuard<>(sessionRepository(), "upload session");
    }

    /**
     * Starts an upload. A checksum already recorded for the same uploader flags the new upload instead: it gets an id
     * and no storage upload until the editor confirms it. Otherwise a multipart upload is created and presigned part
     * URLs are issued.
     */
    public UploadSessionView start(CurrentPerson viewer, AssetDeclaration declaration, String requestId) throws SQLException {
        limits.requireWithinLimit(declaration.kind(), declaration.sizeBytes());
        PartPlan plan = PartPlan.of(declaration.sizeBytes(), limits.partSizeBytes());
        if (declaration.checksum().partCount() != plan.partCount()) {
            throw new ProblemException(400, "Invalid asset", "The checksum names " + declaration.checksum().partCount()
                    + " parts but the file needs " + plan.partCount() + " parts of the configured size.");
        }
        UUID id = identifiers.next();
        Instant now = clock.instant();
        String key = UploadSession.keyFor(id);
        Caller caller = caller(viewer);
        Optional<AssetRecord> duplicate = transactions.inTransaction(connection ->
                assets.findDuplicate(connection, viewer.personId(), declaration.checksum().value()));
        if (duplicate.isPresent()) {
            UploadSession flagged = new UploadSession(id, viewer.personId(), declaration, plan, UploadState.FLAGGED,
                    duplicate.get().id(), key, null, null, null, now, now);
            auditLog.write(caller, "asset_upload.flag", target(id), requestId, connection -> {
                sessions.insert(connection, flagged);
                return null;
            });
            return view(flagged, Set.of(), List.of());
        }
        String uploadId = storage.createMultipartUpload(key);
        UploadSession uploading = new UploadSession(id, viewer.personId(), declaration, plan, UploadState.UPLOADING,
                null, key, uploadId, null, null, now, now);
        try {
            auditLog.write(caller, "asset_upload.start", target(id), requestId, connection -> {
                sessions.insert(connection, uploading);
                return null;
            });
        } catch (SQLException | RuntimeException failure) {
            abortQuietly(key, uploadId);
            throw failure;
        }
        return view(uploading, Set.of(), presignMissing(uploading, Set.of()));
    }

    /**
     * The session as the storage sees it: the confirmed parts are read from the storage, recorded, and the URLs of the
     * other parts are issued. A reload returns the same session, and the upload continues with the first part that is
     * not confirmed.
     */
    public UploadSessionView read(CurrentPerson viewer, UUID id) throws SQLException {
        UploadSession session = visible(viewer, id);
        if (session.state() != UploadState.UPLOADING) {
            return view(session, Set.of(), List.of());
        }
        List<ObjectStorage.StoredPart> stored = storage.listParts(session.storageKey(), session.storageUploadId());
        transactions.inTransaction(connection -> {
            sessions.recordParts(connection, id, stored);
            return null;
        });
        Set<Integer> confirmed = stored.stream().map(ObjectStorage.StoredPart::partNumber).collect(Collectors.toSet());
        return view(session, confirmed, presignMissing(session, confirmed));
    }

    /** Continues a flagged upload the editor confirmed as intended: the storage upload starts now. */
    public UploadSessionView confirmDuplicate(CurrentPerson viewer, UUID id, String requestId) throws SQLException {
        UploadSession flagged = visible(viewer, id);
        requireState(flagged, UploadState.FLAGGED, "Only a flagged upload awaits confirmation.");
        String uploadId = storage.createMultipartUpload(flagged.storageKey());
        UploadSession uploading = flagged.withStorageUpload(uploadId, UploadState.UPLOADING, clock.instant());
        try {
            auditLog.write(caller(viewer), "asset_upload.confirm_duplicate", target(id), requestId, connection -> {
                requireState(lockedSession(connection, id), UploadState.FLAGGED, "Only a flagged upload awaits confirmation.");
                sessions.update(connection, uploading);
                return null;
            });
        } catch (SQLException | RuntimeException failure) {
            abortQuietly(flagged.storageKey(), uploadId);
            throw failure;
        }
        return view(uploading, Set.of(), presignMissing(uploading, Set.of()));
    }

    /**
     * Completes an upload. Every part must be in the storage; the storage then joins the parts, and the stored object's
     * size and checksum are compared with the declaration. Only a match lists the asset. A mismatch marks the session
     * failed, removes the object and answers 422, so no bytes of a wrong file stay in the library.
     */
    public UploadSessionView complete(CurrentPerson viewer, UUID id, String requestId) throws SQLException {
        UploadSession session = visible(viewer, id);
        requireState(session, UploadState.UPLOADING, "The upload is not in progress.");
        List<ObjectStorage.StoredPart> parts = new ArrayList<>(storage.listParts(session.storageKey(), session.storageUploadId()));
        parts.sort(Comparator.comparingInt(ObjectStorage.StoredPart::partNumber));
        Set<Integer> have = parts.stream().map(ObjectStorage.StoredPart::partNumber).collect(Collectors.toSet());
        for (int part = 1; part <= session.plan().partCount(); part++) {
            if (!have.contains(part)) {
                throw new ProblemException(409, "Upload Incomplete", "Part " + part + " has not been uploaded yet.");
            }
        }
        storage.completeMultipartUpload(session.storageKey(), session.storageUploadId(), parts);
        Optional<ObjectStorage.StoredObject> stored = storage.head(session.storageKey());
        AssetDeclaration declaration = session.declaration();
        Instant now = clock.instant();
        boolean matches = stored.isPresent() && stored.get().sizeBytes() == declaration.sizeBytes()
                && declaration.checksum().value().equals(stored.get().checksumSha256());
        Caller caller = caller(viewer);
        if (matches) {
            UploadSession available = session.available(id, now);
            AssetRecord asset = new AssetRecord(id, session.uploaderId(), declaration, session.storageKey(), now);
            auditLog.write(caller, "asset.register", "asset:" + id, requestId, connection -> {
                requireState(lockedSession(connection, id), UploadState.UPLOADING, "The upload is not in progress.");
                assets.insert(connection, asset);
                sessions.update(connection, available);
                return null;
            });
            return view(available, Set.of(), List.of());
        }
        UploadSession failed = session.failed("checksum_mismatch", now);
        auditLog.write(caller, "asset_upload.fail", target(id), requestId, connection -> {
            sessions.update(connection, failed);
            return null;
        });
        deleteQuietly(session.storageKey());
        throw new ProblemException(422, "Checksum Mismatch", "The stored file does not match the declared size and "
                + "checksum. The upload failed and no asset was listed.");
    }

    /** Cancels a flagged or running upload and aborts its storage upload. A completed upload is not cancelled. */
    public UploadSessionView cancel(CurrentPerson viewer, UUID id, String requestId) throws SQLException {
        UploadSession session = visible(viewer, id);
        if (session.state() != UploadState.UPLOADING && session.state() != UploadState.FLAGGED) {
            throw new ProblemException(409, "Upload Not Cancellable", "Only a flagged or running upload can be cancelled.");
        }
        if (session.storageUploadId() != null) {
            storage.abortMultipartUpload(session.storageKey(), session.storageUploadId());
        }
        UploadSession cancelled = session.withState(UploadState.CANCELLED, clock.instant());
        auditLog.write(caller(viewer), "asset_upload.cancel", target(id), requestId, connection -> {
            sessions.update(connection, cancelled);
            return null;
        });
        return view(cancelled, Set.of(), List.of());
    }

    private UploadSession visible(CurrentPerson viewer, UUID id) {
        return guard.read(id, AssetAccess.sessionAccess(viewer.personId()));
    }

    private UploadSession lockedSession(java.sql.Connection connection, UUID id) throws SQLException {
        return sessions.findForUpdate(connection, id).orElseThrow(() -> new ProblemException(404, "Not Found",
                "No upload session with this id."));
    }

    private static void requireState(UploadSession session, UploadState wanted, String detail) {
        if (session.state() != wanted) {
            throw new ProblemException(409, "Upload State Conflict", detail);
        }
    }

    private List<PartUrl> presignMissing(UploadSession session, Set<Integer> confirmed) {
        List<PartUrl> urls = new ArrayList<>();
        for (int part = 1; part <= session.plan().partCount(); part++) {
            if (!confirmed.contains(part)) {
                ObjectStorage.SignedUrl url = storage.presignUploadPart(session.storageKey(), session.storageUploadId(),
                        part, uploadUrlLifetime);
                urls.add(new PartUrl(part, session.plan().sizeOf(part), url.url(), url.expiresAt()));
            }
        }
        return urls;
    }

    private UploadSessionView view(UploadSession session, Set<Integer> confirmed, List<PartUrl> parts) {
        AssetDeclaration declaration = session.declaration();
        List<Integer> confirmedSorted = confirmed.stream().sorted().toList();
        Integer next = null;
        for (int part = 1; part <= session.plan().partCount(); part++) {
            if (!confirmed.contains(part)) {
                next = part;
                break;
            }
        }
        return new UploadSessionView(session.id(), session.state().value(), declaration.kind().value(),
                declaration.format(), declaration.durationSeconds(), declaration.width(), declaration.height(),
                declaration.sizeBytes(), declaration.checksum().value(), session.plan().partSizeBytes(),
                session.plan().partCount(), declaration.streamTitle(),
                declaration.streamDate(), session.duplicateOf(), session.assetId(), session.failureReason(),
                confirmedSorted, next, parts, session.createdAt(), session.updatedAt());
    }

    private void abortQuietly(String key, String uploadId) {
        try {
            storage.abortMultipartUpload(key, uploadId);
        } catch (RuntimeException failure) {
            LOG.warn("An abandoned storage upload could not be aborted: {}", failure.getClass().getSimpleName());
        }
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException failure) {
            LOG.warn("A rejected object could not be removed: {}", failure.getClass().getSimpleName());
        }
    }

    private ObjectRepository<UUID, UploadSession> sessionRepository() {
        return id -> {
            try {
                return transactions.inTransaction(connection -> sessions.find(connection, id));
            } catch (SQLException failure) {
                throw new IllegalStateException("upload session lookup failed", failure);
            }
        };
    }

    static Caller caller(CurrentPerson viewer) {
        return new Caller(viewer.personId(), viewer.channel());
    }

    static String target(UUID id) {
        return "upload_session:" + id;
    }
}
