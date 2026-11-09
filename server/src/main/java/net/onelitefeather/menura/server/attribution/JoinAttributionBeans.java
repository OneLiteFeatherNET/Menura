package net.onelitefeather.menura.server.attribution;

import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;
import net.onelitefeather.menura.core.attribution.JdbcJoinStore;
import net.onelitefeather.menura.core.attribution.JoinService;
import net.onelitefeather.menura.core.attribution.JoinStore;
import net.onelitefeather.menura.core.attribution.SettingsJoinDomain;
import net.onelitefeather.menura.core.config.AnalyticsSettings;
import net.onelitefeather.menura.core.db.Transactions;
import net.onelitefeather.menura.core.series.JoinDomain;

import java.time.Clock;

/** Wires the join attribution (change add-join-attribution) into the server, and the join domain the series renders read. */
@Factory
public class JoinAttributionBeans {

    @Singleton
    JoinStore joinStore() {
        return new JdbcJoinStore();
    }

    @Singleton
    JoinService joinService(Transactions transactions, Clock clock, JoinStore store) {
        return new JoinService(transactions, clock, AnalyticsSettings.from(System.getenv()).timeZone(), store);
    }

    @Singleton
    JoinDomain joinDomain(JoinService joins) {
        return new SettingsJoinDomain(joins);
    }
}
