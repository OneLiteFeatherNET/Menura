package net.onelitefeather.menura.server.brand;

import io.micronaut.serde.annotation.SerdeImport;
import net.onelitefeather.menura.core.brand.BrandKit;

/**
 * Makes the brand kit types of the core serialisable for the REST API (request bodies, responses and the stored JSON).
 * The core stays free of the framework; the types listed here are the whole document, nested types included.
 */
@SerdeImport(BrandKit.class)
@SerdeImport(BrandKit.ColourToken.class)
@SerdeImport(BrandKit.TonalRamp.class)
@SerdeImport(BrandKit.Tone.class)
@SerdeImport(BrandKit.TextRole.class)
@SerdeImport(BrandKit.TypographyRole.class)
@SerdeImport(BrandKit.Mark.class)
@SerdeImport(BrandKit.MarkVariant.class)
@SerdeImport(BrandKit.SafeArea.class)
@SerdeImport(BrandKit.LanguageText.class)
@SerdeImport(BrandKit.Template.class)
@SerdeImport(BrandKit.Slot.class)
@SerdeImport(BrandKit.Timing.class)
@SerdeImport(BrandKit.Parameter.class)
final class BrandKitSerdes {

    private BrandKitSerdes() {
    }
}
