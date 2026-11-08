package net.onelitefeather.menura.server.series;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import net.onelitefeather.menura.core.auth.Role;
import net.onelitefeather.menura.core.db.Transactions;
import net.onelitefeather.menura.core.series.SeriesReports;
import net.onelitefeather.menura.core.series.SeriesService;
import net.onelitefeather.menura.server.auth.RequiresRole;
import net.onelitefeather.menura.server.series.SeriesViews.RenderBundleView;

import java.sql.SQLException;
import java.util.UUID;

/**
 * The render bundle of a channel for the external renderer (change add-content-series). Only the renderer's operator role
 * reads it. The renderer is meant to be a machine client; that credential comes with the client authentication change, so
 * until then the operator role stands in for it.
 */
@Controller("/channels")
public class SeriesRenderController {

    private final SeriesService series;
    private final Transactions transactions;

    public SeriesRenderController(SeriesService series, Transactions transactions) {
        this.series = series;
        this.transactions = transactions;
    }

    @Get("/{id}/series-render-bundle")
    @RequiresRole(Role.OPERATOR)
    @ExecuteOn(TaskExecutors.BLOCKING)
    RenderBundleView bundle(@PathVariable UUID id) throws SQLException {
        SeriesReports.RenderBundle bundle = transactions.inTransaction(c -> series.renderBundle(c, id));
        return SeriesViews.of(bundle);
    }
}
