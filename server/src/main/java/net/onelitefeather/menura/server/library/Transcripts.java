package net.onelitefeather.menura.server.library;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import net.onelitefeather.menura.core.db.Transactions;
import net.onelitefeather.menura.core.library.TranscriptFormat;
import net.onelitefeather.menura.core.library.TranscriptParser;
import net.onelitefeather.menura.core.library.TranscriptSegment;
import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.server.audit.AuditLog;
import net.onelitefeather.menura.server.auth.CurrentPerson;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Attaches the transcript of a raw item, uploaded by an editor as a WebVTT or SRT file. The file is parsed before
 * anything is written, so a refused file leaves no segment and no audit entry. Replacing a transcript replaces all its
 * segments and writes one audit entry. Nothing here transcribes media.
 */
@Singleton
public class Transcripts {

    private final Transactions transactions;
    private final Clock clock;
    private final ContentRepository content;
    private final AuditLog auditLog;
    private final long maxBytes;

    @Inject
    public Transcripts(Transactions transactions, Clock clock, ContentRepository content, AuditLog auditLog,
                       LibrarySettings settings) {
        this(transactions, clock, content, auditLog, settings.transcriptMaxBytes);
    }

    Transcripts(Transactions transactions, Clock clock, ContentRepository content, AuditLog auditLog, long maxBytes) {
        this.transactions = transactions;
        this.clock = clock;
        this.content = content;
        this.auditLog = auditLog;
        this.maxBytes = maxBytes;
    }

    /** Parses the file and stores its segments for the raw item, replacing any earlier transcript. */
    public ContentViews.TranscriptView upload(CurrentPerson viewer, UUID rawItem, String file, String requestId)
            throws SQLException {
        ContentLibrary.requireEditor(viewer);
        if (file == null || file.isBlank()) {
            throw new ProblemException(400, "Invalid transcript", "The transcript file is empty.");
        }
        boolean exists = transactions.inTransaction(connection -> {
            if (content.findRawItem(connection, rawItem).isEmpty()) {
                throw new ProblemException(404, "Not Found", "No raw item with this id.");
            }
            return content.hasTranscript(connection, rawItem);
        });
        TranscriptFormat format = TranscriptFormat.detect(file);
        List<TranscriptSegment> segments = TranscriptParser.parse(file, maxBytes);
        Instant now = clock.instant();
        String action = exists ? "transcript.replace" : "transcript.upload";
        boolean replaced = auditLog.write(AssetUploads.caller(viewer), action, "raw_item:" + rawItem, requestId, connection -> {
            if (content.findRawItem(connection, rawItem).isEmpty()) {
                throw new ProblemException(404, "Not Found", "No raw item with this id.");
            }
            return content.replaceTranscript(connection, rawItem, format.name().toLowerCase(java.util.Locale.ROOT),
                    viewer.personId(), now, segments);
        });
        return new ContentViews.TranscriptView(rawItem, format.name().toLowerCase(java.util.Locale.ROOT), segments.size(),
                replaced, now);
    }
}
