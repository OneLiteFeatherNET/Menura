package net.onelitefeather.menura.server.library;

import jakarta.inject.Singleton;
import net.onelitefeather.menura.core.db.Transactions;
import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.server.audit.AuditLog;
import net.onelitefeather.menura.server.auth.CurrentPerson;
import net.onelitefeather.menura.server.library.ContentRepository.RawItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.UUID;

/**
 * Explicit removal of a raw item's media. The row, its transcript, its clips and its lineage stay; only the recording
 * is removed from the object storage and the item's media state becomes removed. Only the editor role may remove, and
 * the removal writes one audit entry in the same transaction as the state change.
 */
@Singleton
public class RawMedia {

    private static final Logger LOG = LoggerFactory.getLogger(RawMedia.class);
    static final String REMOVED = "removed";

    private final Transactions transactions;
    private final ContentRepository content;
    private final ObjectStorage storage;
    private final AuditLog auditLog;

    public RawMedia(Transactions transactions, ContentRepository content, ObjectStorage storage, AuditLog auditLog) {
        this.transactions = transactions;
        this.content = content;
        this.storage = storage;
        this.auditLog = auditLog;
    }

    /** Removes the media of a raw item; a second removal is refused with 409. */
    public void removeMedia(CurrentPerson viewer, UUID rawItemId, String requestId) throws SQLException {
        ContentLibrary.requireEditor(viewer);
        String key = auditLog.write(AssetUploads.caller(viewer), "raw_item.media_delete", "raw_item:" + rawItemId, requestId,
                connection -> {
                    RawItem raw = content.findRawItem(connection, rawItemId).orElseThrow(() -> new ProblemException(404,
                            "Not Found", "No raw item with this id."));
                    if (REMOVED.equals(raw.mediaState())) {
                        throw new ProblemException(409, "Media Removed", "The media of this raw item has been removed.");
                    }
                    content.setRawMediaState(connection, rawItemId, REMOVED);
                    return raw.storageKey();
                });
        try {
            storage.delete(key);
        } catch (ProblemException failure) {
            LOG.warn("The media of raw item {} could not be removed from the storage", rawItemId);
        }
    }
}
