package net.onelitefeather.menura.server.library;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

/**
 * One cursor page of assets. {@code nextCursor} is null on the last page, and {@code items} is sent even when empty:
 * the contract names both members, so they are always included.
 */
@Serdeable
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AssetPageView(List<AssetView> items, String nextCursor) {
}
