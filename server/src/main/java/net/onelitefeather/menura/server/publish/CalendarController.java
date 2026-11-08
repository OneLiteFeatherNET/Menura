package net.onelitefeather.menura.server.publish;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.core.publish.CalendarRange;
import net.onelitefeather.menura.core.publish.PostService;
import net.onelitefeather.menura.core.publish.PostStatus;
import net.onelitefeather.menura.core.publish.PublishSettings;
import net.onelitefeather.menura.server.auth.RequiresRole;
import net.onelitefeather.menura.server.publish.PostViews.CalendarPageView;

import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.UUID;

/**
 * The content calendar (change add-content-distribution): planned and published posts together in a month, a week or a
 * list of days, filtered by channel, series and status. Any signed-in person reads it. Times are shown in the installation
 * time zone; the range is computed here, so a client never has to know the zone rules.
 */
@Controller("/calendar")
public class CalendarController {

    private final PostService posts;
    private final Clock clock;
    private final PublishSettings settings;

    public CalendarController(PostService posts, Clock clock, PublishSettings settings) {
        this.posts = posts;
        this.clock = clock;
        this.settings = settings;
    }

    @Get
    @RequiresRole
    @ExecuteOn(TaskExecutors.BLOCKING)
    CalendarPageView read(@QueryValue Optional<String> view, @QueryValue Optional<String> date,
                          @QueryValue Optional<Integer> days, @QueryValue Optional<UUID> channelId,
                          @QueryValue Optional<UUID> seriesId, @QueryValue Optional<String> status) throws SQLException {
        CalendarRange.View chosen = CalendarRange.View.parse(view.orElse("month"))
                .orElseThrow(() -> new ProblemException(400, "Bad Request", "The view must be one of: month, week, list."));
        LocalDate day = parseDate(date.orElse(null));
        PostStatus wanted = null;
        if (status.isPresent()) {
            wanted = PostStatus.parse(status.get())
                    .orElseThrow(() -> new ProblemException(400, "Bad Request", "The status is not a post status."));
        }
        CalendarRange range;
        try {
            range = CalendarRange.of(chosen, day, days.orElse(CalendarRange.LIST_DAYS_DEFAULT), settings.zone());
        } catch (IllegalArgumentException outOfRange) {
            throw new ProblemException(400, "Bad Request", "A list covers between 1 and 92 days.");
        }
        return PostViews.calendar(range, posts.calendar(range, channelId.orElse(null), seriesId.orElse(null), wanted));
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return LocalDate.ofInstant(clock.instant(), settings.zone());
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException invalid) {
            throw new ProblemException(400, "Bad Request", "The date must be a day as YYYY-MM-DD.");
        }
    }
}
