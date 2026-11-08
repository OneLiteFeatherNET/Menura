package net.onelitefeather.menura.server.prototype.pool;

import io.micronaut.serde.annotation.Serdeable;

/** A demo person of the pool, pseudonymous by name. */
@Serdeable
public record Person(String id, String name) {
}
