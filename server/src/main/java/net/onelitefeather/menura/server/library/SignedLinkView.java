package net.onelitefeather.menura.server.library;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;

/** A signed link to one object and the instant after which the storage refuses it. */
@Serdeable
@JsonInclude(JsonInclude.Include.ALWAYS)
public record SignedLinkView(String url, Instant expiresAt) {
}
