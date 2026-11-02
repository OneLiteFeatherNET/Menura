package net.onelitefeather.menura.server.secret;

import io.micronaut.serde.annotation.Serdeable;

/** The answer to issuing a token. The token is shown here once; the server cannot show it again. */
@Serdeable
public record IssuedCalendarFeedToken(String token) {
}
