package net.onelitefeather.menura.core.series;

import net.onelitefeather.menura.core.auth.Role;
import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.core.series.SeriesService.AssignmentCommand;
import net.onelitefeather.menura.core.series.support.SeriesFixture;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Series visibility: the roles that may see a series. A series a caller may not see answers 404, never 403. */
class VisibilityTest {

    private static final Set<Role> VIEWER = Set.of(Role.VIEWER);
    private static final Set<Role> EDITOR = Set.of(Role.EDITOR);
    private static final UUID ACTOR = UUID.fromString("5e6f7a8b-9c0d-4e1f-8a2b-3c4d5e6f7a8b");

    @Test
    void aSeriesWithoutAVisibilityIsVisibleToEveryRole() throws SQLException {
        SeriesFixture world = new SeriesFixture();
        ContentSeries series = world.service.create(null, SeriesFixture.draft("Offen", "#offen"));

        for (Role role : Role.values()) {
            assertEquals(series.id(), world.service.find(null, series.id(), Set.of(role)).id(),
                    "the default visibility includes the role " + role);
        }
        assertEquals(EnumSet.allOf(Role.class), series.visibleTo(), "the default names every role");
    }

    @Test
    void aViewerDoesNotFindASeriesVisibleOnlyToEditors() throws SQLException {
        SeriesFixture world = new SeriesFixture();
        ContentSeries series = world.service.create(null, withVisibility(SeriesFixture.draft("Redaktion", "#redaktion"),
                List.of("editor")));

        ProblemException refused = assertThrows(ProblemException.class,
                () -> world.service.find(null, series.id(), VIEWER));

        assertEquals(404, refused.status(), "a series the caller may not see answers 404, not 403");
    }

    @Test
    void anEditorFindsASeriesVisibleToEditors() throws SQLException {
        SeriesFixture world = new SeriesFixture();
        ContentSeries series = world.service.create(null, withVisibility(SeriesFixture.draft("Redaktion", "#redaktion"),
                List.of("editor")));

        assertEquals(series.id(), world.service.find(null, series.id(), EDITOR).id(), "the editor sees it");
    }

    @Test
    void theListLeavesOutTheSeriesTheCallerMayNotSee() throws SQLException {
        SeriesFixture world = new SeriesFixture();
        world.service.create(null, SeriesFixture.draft("Offen", "#offen"));
        world.service.create(null, withVisibility(SeriesFixture.draft("Redaktion", "#redaktion"), List.of("editor")));

        List<ContentSeries> seen = world.service.list(null, VIEWER);

        assertEquals(1, seen.size(), "the viewer lists only the open series");
        assertEquals("Offen", seen.get(0).name(), "the open series is the one listed");
    }

    @Test
    void anEditorOutsideTheVisibilityCannotChangeTheSeriesAndNothingIsWritten() throws SQLException {
        SeriesFixture world = new SeriesFixture();
        ContentSeries series = world.service.create(null, withVisibility(SeriesFixture.draft("Redaktion", "#redaktion"),
                List.of("editor")));
        int writes = world.series.writes();

        ProblemException refused = assertThrows(ProblemException.class, () -> world.service.update(null, series.id(),
                SeriesFixture.draft("Neu", "#redaktion"), Set.of(Role.OPERATOR)));

        assertEquals(404, refused.status(), "an invisible series answers 404 on write as well");
        assertEquals(writes, world.series.writes(), "the refused change writes nothing");
        assertEquals("Redaktion", world.service.find(null, series.id(), EDITOR).name(), "the series is unchanged");
    }

    @Test
    void anAssignmentToASeriesInvisibleToTheCallerAnswersNotFound() throws SQLException {
        SeriesFixture world = new SeriesFixture();
        ContentSeries series = world.service.create(null, withVisibility(SeriesFixture.draft("Redaktion", "#redaktion"),
                List.of("editor")));
        UUID channel = world.channel("UC1");
        var post = SeriesFixture.post(channel, "v1", net.onelitefeather.menura.core.post.ExternalPostFormat.SHORT,
                SeriesFixture.NOW);
        world.posts.insert(null, post);

        ProblemException refused = assertThrows(ProblemException.class, () -> world.service.assign(null, post.id(),
                new AssignmentCommand(series.id(), false, false), ACTOR, VIEWER));

        assertEquals(404, refused.status(), "the caller may not name a series it cannot see");
        assertTrue(world.series.findAssignment(null, post.id()).isEmpty(), "no decision is written");
    }

    @Test
    void theOverviewLeavesOutTheSeriesTheCallerMayNotSee() throws SQLException {
        SeriesFixture world = new SeriesFixture();
        world.service.create(null, withVisibility(SeriesFixture.draft("Redaktion", "#redaktion"), List.of("editor")));

        SeriesReports.Overview overview = world.service.overview(null, 1, VIEWER);

        assertTrue(overview.series().isEmpty(), "the overview shows the series the viewer may see");
        assertFalse(overview.warnings().isEmpty(), "the audience warning still counts every active series");
    }

    @Test
    void aVisibilityWithoutAnyRoleIsRefused() {
        SeriesFixture world = new SeriesFixture();

        ProblemException refused = assertThrows(ProblemException.class,
                () -> world.service.create(null, withVisibility(SeriesFixture.draft("Leer", "#leer"), List.of())));

        assertEquals(400, refused.status(), "a series nobody may see is refused");
    }

    @Test
    void anUnknownRoleInTheVisibilityIsRefusedNamingTheRoles() {
        SeriesFixture world = new SeriesFixture();

        ProblemException refused = assertThrows(ProblemException.class,
                () -> world.service.create(null, withVisibility(SeriesFixture.draft("Fremd", "#fremd"), List.of("gast"))));

        assertEquals(400, refused.status(), "an unknown role is refused");
        assertTrue(refused.detail().contains("viewer"), "the answer names the allowed roles");
    }

    private static SeriesDraft withVisibility(SeriesDraft d, List<String> visibleTo) {
        return new SeriesDraft(d.name(), d.badgeText(), d.colourToken(), d.badgeToken(), d.openingFormula(),
                d.layoutTemplate(), d.hashtag(), d.audience(), d.weekday(), d.postingSlot(), d.active(), d.playlists(),
                visibleTo);
    }
}
