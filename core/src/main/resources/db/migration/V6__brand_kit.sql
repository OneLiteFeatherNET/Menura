-- The central brand kit and the pins of its consumers (change add-brand-kit).

-- One row per published version. The whole kit is one immutable document, so a pinned version is read as it was
-- published and never reconstructed from later changes. Version numbers start at 1 and never change.
CREATE TABLE menura.brand_kit_version (
    version      integer     PRIMARY KEY CHECK (version >= 1),
    document     jsonb       NOT NULL,
    published_by uuid        NOT NULL,
    created_at   timestamptz NOT NULL
);

-- The version a consumer (an overlay scene, an OBS plugin installation, the external renderer, a desktop
-- installation) has pinned. A consumer without a row follows the latest version.
CREATE TABLE menura.brand_kit_pin (
    consumer   text        PRIMARY KEY CHECK (char_length(consumer) BETWEEN 1 AND 64),
    version    integer     NOT NULL REFERENCES menura.brand_kit_version (version),
    updated_at timestamptz NOT NULL
);
