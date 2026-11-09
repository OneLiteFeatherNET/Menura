package net.onelitefeather.menura.server.library;

import net.onelitefeather.menura.core.asset.AssetDeclaration;
import net.onelitefeather.menura.core.brand.BrandKit;
import net.onelitefeather.menura.core.brand.InitialBrandKit;
import net.onelitefeather.menura.server.library.ContentRepository.RawItem;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Wires the content services to the in-memory collaborators of {@link LibraryFixture}, with a clock the test moves by
 * hand. Each test builds its own fixture.
 */
final class ContentFixture {

    static final long TRANSCRIPT_LIMIT = 10L * 1024 * 1024;
    static final UUID PERSON_A = UUID.fromString("3d4e5f6a-7b8c-4d9e-8f0a-2b3c4d5e6f70");
    static final UUID PERSON_B = UUID.fromString("4e5f6a7b-8c9d-4e0f-9a1b-3c4d5e6f7081");
    /** The templates of the initial brand kit, the ones a timeline may refer to. */
    static final LayoutTemplates BRAND_KIT_TEMPLATES = () -> InitialBrandKit.create().templates().stream()
            .map(BrandKit.Template::format).collect(Collectors.toUnmodifiableSet());

    final LibraryFixture base = new LibraryFixture();
    final InMemoryContentRepository content = new InMemoryContentRepository();
    final ContentLibrary library = new ContentLibrary(base.transactions, base.clock, base.identifiers, content,
            base.auditLog);
    final Transcripts transcripts = new Transcripts(base.transactions, base.clock, content, base.auditLog,
            TRANSCRIPT_LIMIT);
    final Timelines timelines = new Timelines(base.transactions, base.clock, base.identifiers, content, base.assets,
            base.auditLog, BRAND_KIT_TEMPLATES);

    /** A raw item of the given duration, uploaded by the person, recorded on the given stream date. */
    ContentFixture() {
        content.addPerson(PERSON_A);
        content.addPerson(PERSON_B);
    }

    UUID rawItem(UUID uploader, String title, LocalDate streamDate, int durationSeconds) {
        UUID id = base.identifiers.next();
        content.addRawItem(new RawItem(id, uploader, title, streamDate, durationSeconds, base.clock.instant(),
                "objects/" + id, "available"));
        return id;
    }

    /** A verified finished video in the asset table, not yet registered in the pool. */
    UUID finishedVideo(UUID uploader) {
        UUID id = base.identifiers.next();
        AssetDeclaration declaration = LibraryFixture.video(LibraryFixture.MIB, LibraryFixture.checksumFor(LibraryFixture.MIB,
                LibraryFixture.MIB));
        base.assets.insert(null, new AssetRecord(id, uploader, declaration, "objects/" + id, base.clock.instant()));
        return id;
    }

    /** A verified raw-material asset in the asset table, for the registration rules that refuse it. */
    UUID rawMaterialAsset(UUID uploader) {
        UUID id = base.identifiers.next();
        AssetDeclaration declaration = LibraryFixture.rawRecording(LibraryFixture.MIB,
                LibraryFixture.checksumFor(LibraryFixture.MIB, LibraryFixture.MIB));
        base.assets.insert(null, new AssetRecord(id, uploader, declaration, "objects/" + id, base.clock.instant()));
        return id;
    }

    Instant now() {
        return base.clock.instant();
    }
}
