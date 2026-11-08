package net.onelitefeather.menura.server.brand;

import io.micronaut.serde.ObjectMapper;
import net.onelitefeather.menura.core.brand.BrandKit;
import net.onelitefeather.menura.core.brand.BrandKit.Slot;
import net.onelitefeather.menura.core.brand.BrandKit.Template;
import net.onelitefeather.menura.core.brand.BrandKitEvents;
import net.onelitefeather.menura.core.brand.InitialBrandKit;
import net.onelitefeather.menura.core.db.DataSourceTransactions;
import net.onelitefeather.menura.core.db.Migrations;
import net.onelitefeather.menura.core.id.IdentifierGenerator;
import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.server.audit.AuditLog;
import net.onelitefeather.menura.server.audit.Channel;
import net.onelitefeather.menura.server.audit.JdbcAuditRepository;
import net.onelitefeather.menura.server.auth.CurrentPerson;
import net.onelitefeather.menura.server.support.DeterministicRandom;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The brand kit end to end on an empty PostgreSQL 17: every migration applies, then publication, the contrast refusal,
 * the safe-zone refusal, pinning and the publication notification run on the real JDBC store. One scenario shares one
 * database, so it is one test; the in-memory unit tests cover each rule on its own.
 */
@Tag("integration")
@Testcontainers
class BrandKitIntegrationTest {

    private static final UUID EDITOR = UUID.fromString("0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d");
    private static final CurrentPerson EDITOR_PERSON = new CurrentPerson(EDITOR, "subject", Set.of(), Channel.WEB);
    private static final Instant NOW = Instant.parse("2026-11-08T10:00:00Z");

    @Container
    PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17.11"))
            .withDatabaseName("menura")
            .withUsername("menura")
            .withPassword("menura");

    @Test
    void theBrandKitWorksEndToEndOnAnEmptyDatabase() throws Exception {
        Migrations.migrate(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        assertEquals(2, brandKitTables(), "the migration creates brand_kit_version and brand_kit_pin");

        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setUrl(postgres.getJdbcUrl());
        source.setUser(postgres.getUsername());
        source.setPassword(postgres.getPassword());
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        DataSourceTransactions transactions = new DataSourceTransactions(source);
        IdentifierGenerator identifiers = new IdentifierGenerator(new DeterministicRandom());
        RecordingEvents events = new RecordingEvents();
        BrandKits kits = new BrandKits(transactions, new AuditLog(transactions, clock, identifiers, new JdbcAuditRepository()),
                new JdbcBrandKitRepository(ObjectMapper.create(Map.of())), new JdbcBrandKitAssets(), events, clock);

        assertEquals(1, kits.current().version(), "the first touch seeds version 1 from the overlay theme");

        StoredKitVersion published = kits.publish(EDITOR_PERSON, InitialBrandKit.create(), "req-1");
        assertEquals(2, published.version(), "a publication after the seed is version 2");
        assertEquals(List.of(new BrandKitEvents.KitPublished(2, NOW)), events.published, "the publication is announced once");

        ProblemException contrast = assertThrows(ProblemException.class,
                () -> kits.publish(EDITOR_PERSON, withCaptionColour("#e0e0e0"), "req-2"));
        assertEquals(422, contrast.status(), "a text role below AA is refused");
        assertTrue(contrast.detail().contains("contrast"), "the refusal names the contrast: " + contrast.detail());

        ProblemException safeZone = assertThrows(ProblemException.class,
                () -> kits.publish(EDITOR_PERSON, withBadgeInTopBand(), "req-3"));
        assertTrue(safeZone.detail().contains("slot 'badge' of template 'short-9x16'"),
                "a badge in the top band is refused and named: " + safeZone.detail());

        assertEquals(2, kits.current().version(), "refused publications store no version");
        assertEquals(List.of(new BrandKitEvents.KitPublished(2, NOW)), events.published, "refusals are not announced");

        kits.pin(EDITOR_PERSON, "overlay-scene", 2, "req-4");
        StoredKitVersion third = kits.publish(EDITOR_PERSON, withPrimary("#2a1a5e"), "req-5");
        assertEquals(3, third.version(), "the next publication is version 3");
        assertEquals(2, kits.resolve(null, "overlay-scene").version(), "a pinned consumer keeps version 2");
        assertEquals(3, kits.resolve(null, "obs-plugin").version(), "an unpinned consumer follows version 3");
        assertEquals(List.of(new BrandKitEvents.KitPublished(2, NOW), new BrandKitEvents.KitPublished(3, NOW)),
                events.published, "each publication is announced with its version");
    }

    private int brandKitTables() throws SQLException {
        try (Connection connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
             Statement statement = connection.createStatement();
             ResultSet tables = statement.executeQuery("select count(*) from information_schema.tables "
                     + "where table_schema = 'menura' and table_name in ('brand_kit_version', 'brand_kit_pin')")) {
            tables.next();
            return tables.getInt(1);
        }
    }

    private static BrandKit withCaptionColour(String light) {
        BrandKit base = InitialBrandKit.create();
        List<BrandKit.ColourToken> colours = new ArrayList<>(base.colours());
        colours.replaceAll(token -> token.name().equals("on-surface")
                ? new BrandKit.ColourToken("on-surface", light, token.dark()) : token);
        return copy(base, colours, base.templates());
    }

    private static BrandKit withPrimary(String light) {
        BrandKit base = InitialBrandKit.create();
        List<BrandKit.ColourToken> colours = new ArrayList<>(base.colours());
        colours.replaceAll(token -> token.name().equals("primary")
                ? new BrandKit.ColourToken("primary", light, token.dark()) : token);
        return copy(base, colours, base.templates());
    }

    private static BrandKit withBadgeInTopBand() {
        BrandKit base = InitialBrandKit.create();
        List<Template> templates = base.templates().stream().map(template -> {
            if (!template.format().equals("short-9x16")) {
                return template;
            }
            List<Slot> slots = template.slots().stream().map(slot -> slot.name().equals("badge")
                    ? new Slot(slot.name(), slot.kind(), slot.colour(), slot.background(), slot.parameter(), slot.x(), 5,
                    slot.width(), slot.height(), slot.timing())
                    : slot).toList();
            return new Template(template.format(), template.name(), template.width(), template.height(), slots,
                    template.parameters());
        }).toList();
        return copy(base, base.colours(), templates);
    }

    private static BrandKit copy(BrandKit base, List<BrandKit.ColourToken> colours, List<Template> templates) {
        return new BrandKit(colours, base.ramps(), base.textRoles(), base.typography(), base.marks(), base.voice(),
                base.texts(), templates);
    }

    /** Collects the notifications the publications send, in order. */
    static final class RecordingEvents implements BrandKitEvents {
        final List<BrandKitEvents.KitPublished> published = new ArrayList<>();

        @Override
        public void published(BrandKitEvents.KitPublished event) {
            published.add(event);
        }
    }
}
