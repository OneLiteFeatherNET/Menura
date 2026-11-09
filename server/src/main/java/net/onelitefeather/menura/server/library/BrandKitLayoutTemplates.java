package net.onelitefeather.menura.server.library;

import jakarta.inject.Singleton;
import net.onelitefeather.menura.core.brand.BrandKit;
import net.onelitefeather.menura.server.brand.BrandKits;

import java.sql.SQLException;
import java.util.Set;
import java.util.stream.Collectors;

/** Reads the layout template formats from the newest brand kit version. */
@Singleton
public class BrandKitLayoutTemplates implements LayoutTemplates {

    private final BrandKits kits;

    public BrandKitLayoutTemplates(BrandKits kits) {
        this.kits = kits;
    }

    @Override
    public Set<String> formats() throws SQLException {
        BrandKit kit = kits.current().kit();
        return kit.templates().stream().map(BrandKit.Template::format).collect(Collectors.toUnmodifiableSet());
    }
}
