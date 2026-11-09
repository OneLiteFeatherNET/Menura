package net.onelitefeather.menura.server.library;

import jakarta.inject.Singleton;
import io.micronaut.serde.ObjectMapper;
import net.onelitefeather.menura.core.db.Transactions;
import net.onelitefeather.menura.core.id.IdentifierGenerator;
import net.onelitefeather.menura.core.library.ChapterMark;
import net.onelitefeather.menura.core.library.ChapterMarks;
import net.onelitefeather.menura.core.library.Stage;
import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.core.asset.AssetKind;
import net.onelitefeather.menura.server.audit.AuditLog;
import net.onelitefeather.menura.server.auth.CurrentPerson;
import net.onelitefeather.menura.server.library.ContentRepository.ClipRecord;
import net.onelitefeather.menura.server.library.ContentRepository.RegisteredAsset;
import net.onelitefeather.menura.server.library.ContentRepository.TimelineRecord;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Timelines as data, and the registration of finished assets in the pool. A timeline may only use existing clips and
 * valid chapter marks. Registering a finished asset requires its verified upload and an existing timeline; it records
 * the lineage and moves the clips of that timeline to stage rendered.
 */
@Singleton
public class Timelines {

    private static final ObjectMapper JSON = ObjectMapper.create(Map.of());

    private final Transactions transactions;
    private final Clock clock;
    private final IdentifierGenerator identifiers;
    private final ContentRepository content;
    private final AssetRepository assets;
    private final AuditLog auditLog;
    private final LayoutTemplates layoutTemplates;

    public Timelines(Transactions transactions, Clock clock, IdentifierGenerator identifiers, ContentRepository content,
                     AssetRepository assets, AuditLog auditLog, LayoutTemplates layoutTemplates) {
        this.transactions = transactions;
        this.clock = clock;
        this.identifiers = identifiers;
        this.content = content;
        this.assets = assets;
        this.auditLog = auditLog;
        this.layoutTemplates = layoutTemplates;
    }

    /** Stores a timeline after checking its clips and chapter marks. The document is returned unchanged by {@link #get}. */
    public ContentViews.TimelineView store(CurrentPerson viewer, String title, TimelineDocument document, String requestId)
            throws SQLException {
        ContentLibrary.requireEditor(viewer);
        String checkedTitle = ContentLibrary.requireTitle(title);
        if (document == null || document.sourceClipIds().isEmpty()) {
            throw new ProblemException(400, "Invalid timeline", "A timeline needs at least one source range.");
        }
        requireLayoutTemplate(document.layoutTemplate());
        List<ChapterMark> marks = document.chapters().stream()
                .map(chapter -> new ChapterMark(chapter.seconds(), chapter.title())).toList();
        ChapterMarks.validate(marks);
        Set<UUID> clips = new LinkedHashSet<>(document.sourceClipIds());
        UUID id = identifiers.next();
        Instant now = clock.instant();
        String json = json(document);
        return auditLog.write(AssetUploads.caller(viewer), "timeline.create", "timeline:" + id, requestId, connection -> {
            for (UUID clip : clips) {
                if (content.findClip(connection, clip).isEmpty()) {
                    throw new ProblemException(400, "Invalid timeline", "The clip " + clip + " does not exist.");
                }
            }
            content.insertTimeline(connection, new TimelineRecord(id, viewer.personId(), checkedTitle, json, now));
            for (UUID clip : clips) {
                content.insertEdge(connection, "timeline", id, "clip", clip);
            }
            return new ContentViews.TimelineView(id, checkedTitle, viewer.personId(), document, now);
        });
    }

    /** The timeline as it was stored, for any person who may read the pool. */
    public ContentViews.TimelineView get(CurrentPerson viewer, UUID id) throws SQLException {
        if (!AssetAccess.mayRead(viewer)) {
            throw timelineNotFound();
        }
        TimelineRecord record = transactions.inTransaction(connection -> content.findTimeline(connection, id))
                .orElseThrow(() -> timelineNotFound());
        return new ContentViews.TimelineView(record.id(), record.title(), record.uploaderId(), parse(record.documentJson()),
                record.createdAt());
    }

    /**
     * Registers a verified finished video or image in the pool, made from the timeline. The clips of the timeline move
     * to stage rendered. A missing asset, an unverified upload or a missing timeline rejects the registration.
     */
    public ContentViews.RegisteredAssetView register(CurrentPerson viewer, UUID assetId, UUID timelineId, String title,
                                                     String requestId) throws SQLException {
        ContentLibrary.requireEditor(viewer);
        String checkedTitle = ContentLibrary.requireTitle(title);
        Instant now = clock.instant();
        return auditLog.write(AssetUploads.caller(viewer), "asset.register", "asset:" + assetId, requestId, connection -> {
            AssetRecord asset = assets.find(connection, assetId).orElseThrow(() -> new ProblemException(404, "Not Found",
                    "No asset with this id."));
            AssetKind kind = asset.kind();
            if (kind != AssetKind.VIDEO && kind != AssetKind.IMAGE) {
                throw new ProblemException(400, "Invalid asset", "Only a finished video or image joins the pool.");
            }
            if (content.findRegistration(connection, assetId).isPresent()) {
                throw new ProblemException(409, "Already Registered", "This asset is already in the pool.");
            }
            if (content.findTimeline(connection, timelineId).isEmpty()) {
                throw timelineNotFound();
            }
            RegisteredAsset registration = new RegisteredAsset(assetId, asset.uploaderId(), checkedTitle, timelineId, now);
            content.insertRegistration(connection, registration);
            content.insertEdge(connection, "asset", assetId, "timeline", timelineId);
            for (UUID clipId : content.clipsOf(connection, timelineId)) {
                Optional<ClipRecord> clip = content.findClip(connection, clipId);
                if (clip.isPresent() && clip.get().stage() == Stage.CUT) {
                    ClipRecord rendered = new ClipRecord(clip.get().id(), clip.get().rawItemId(), clip.get().uploaderId(),
                            clip.get().title(), clip.get().range(), clip.get().status(), Stage.RENDERED,
                            clip.get().createdAt(), now);
                    content.updateClip(connection, rendered);
                }
            }
            return new ContentViews.RegisteredAssetView(assetId, checkedTitle, asset.uploaderId(), timelineId, now);
        });
    }

    /**
     * A layout template, when the timeline names one, must be a template of the brand kit; an unknown one is named in the
     * problem. The field stays optional, as the contract describes it.
     */
    private void requireLayoutTemplate(String layoutTemplate) throws SQLException {
        if (layoutTemplate == null) {
            return;
        }
        if (!layoutTemplates.formats().contains(layoutTemplate)) {
            throw new ProblemException(400, "Invalid timeline", "The layout template '" + layoutTemplate
                    + "' is not a template of the brand kit.");
        }
    }

    private static ProblemException timelineNotFound() {
        return new ProblemException(404, "Not Found", "No timeline with this id.");
    }

    private static String json(TimelineDocument document) {
        try {
            return JSON.writeValueAsString(document);
        } catch (IOException failure) {
            throw new IllegalStateException("the timeline document cannot be written", failure);
        }
    }

    private static TimelineDocument parse(String json) {
        try {
            return JSON.readValue(json, TimelineDocument.class);
        } catch (IOException failure) {
            throw new IllegalStateException("the stored timeline document cannot be read", failure);
        }
    }
}
