package net.onelitefeather.menura.server.series;

import jakarta.inject.Singleton;
import net.onelitefeather.menura.core.brand.BrandKit.ColourToken;
import net.onelitefeather.menura.core.series.BrandTokens;
import net.onelitefeather.menura.server.brand.BrandKits;

import java.sql.SQLException;
import java.util.Optional;

/** The brand-kit tokens of the newest published version, which the series check their colours against. */
@Singleton
public class BrandKitTokens implements BrandTokens {

    private final BrandKits kits;

    public BrandKitTokens(BrandKits kits) {
        this.kits = kits;
    }

    @Override
    public Optional<ColourToken> colour(String name) throws SQLException {
        return kits.current().kit().colour(name);
    }

    @Override
    public boolean hasTemplate(String format) throws SQLException {
        return kits.current().kit().template(format).isPresent();
    }
}
