package net.onelitefeather.menura.server.series;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import jakarta.inject.Singleton;
import net.onelitefeather.menura.core.series.BrandTokens;
import net.onelitefeather.menura.core.series.SeriesStore;
import net.onelitefeather.menura.core.series.support.FixedBrandTokens;
import net.onelitefeather.menura.core.series.support.InMemorySeriesStore;

/**
 * Test-only replacements of the series store and the brand-kit tokens: rows and colours live in memory, so no test needs a
 * database or a published brand kit. The tokens are the ones of the initial brand kit that the tests use.
 */
@Factory
@Requires(env = Environment.TEST)
class SeriesTestFakes {

    @Singleton
    InMemorySeriesStore inMemorySeriesStore() {
        return new InMemorySeriesStore();
    }

    @Singleton
    @Replaces(factory = SeriesBeans.class, bean = SeriesStore.class)
    SeriesStore seriesStore(InMemorySeriesStore store) {
        return store;
    }

    @Singleton
    FixedBrandTokens fixedBrandTokens() {
        return new FixedBrandTokens()
                .colour("on-primary", "#ffffff")
                .colour("primary", "#6638e4")
                .colour("pale", "#959595")
                .template("short-9x16");
    }

    @Singleton
    @Replaces(BrandKitTokens.class)
    BrandTokens brandTokens(FixedBrandTokens tokens) {
        return tokens;
    }
}
