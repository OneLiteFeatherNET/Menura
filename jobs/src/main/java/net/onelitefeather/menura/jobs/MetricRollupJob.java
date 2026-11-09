package net.onelitefeather.menura.jobs;

import net.onelitefeather.menura.core.analytics.JdbcMetricStore;
import net.onelitefeather.menura.core.analytics.MetricRollups;
import net.onelitefeather.menura.core.analytics.PublishSlot;
import net.onelitefeather.menura.core.config.AnalyticsSettings;
import net.onelitefeather.menura.core.config.MenuraSettings;
import net.onelitefeather.menura.core.db.DataSourceTransactions;
import net.onelitefeather.menura.core.id.IdentifierGenerator;
import org.postgresql.ds.PGSimpleDataSource;

import java.security.SecureRandom;
import java.util.Objects;

/**
 * Rebuilds the daily, weekly and equal-age rollups of every day that a metric collection or an internal source marked
 * (change add-content-analytics). It runs as a CronJob shortly after the collection, so the dashboard lags the points by
 * one run. It prints counts only.
 */
public final class MetricRollupJob implements Job {

    public static final String NAME = "metric-rollup";

    private final MenuraSettings database;
    private final AnalyticsSettings analytics;

    public MetricRollupJob(MenuraSettings database, AnalyticsSettings analytics) {
        this.database = Objects.requireNonNull(database, "database");
        this.analytics = Objects.requireNonNull(analytics, "analytics");
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void execute() throws Exception {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(database.databaseUrl());
        dataSource.setUser(database.databaseUser());
        dataSource.setPassword(database.databasePassword());

        DataSourceTransactions transactions = new DataSourceTransactions(dataSource);
        JdbcMetricStore store = new JdbcMetricStore(new IdentifierGenerator(new SecureRandom()),
                new PublishSlot(analytics.timeZone()));
        MetricRollups.Report report = new MetricRollups(transactions, store).rebuild();
        System.out.println("metric-rollup days=" + report.days() + " weeks=" + report.weeks());
    }
}
