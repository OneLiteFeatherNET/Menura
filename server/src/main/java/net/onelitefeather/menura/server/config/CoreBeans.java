package net.onelitefeather.menura.server.config;

import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;
import net.onelitefeather.menura.core.id.IdentifierGenerator;
import net.onelitefeather.menura.core.secret.SecretGenerator;

import java.security.SecureRandom;

/** Provides the core utilities to the server's beans. The clock is provided by {@code ClockConfiguration}. */
@Factory
public class CoreBeans {

    @Singleton
    SecureRandom secureRandom() {
        return new SecureRandom();
    }

    @Singleton
    IdentifierGenerator identifierGenerator(SecureRandom random) {
        return new IdentifierGenerator(random);
    }

    @Singleton
    SecretGenerator secretGenerator(SecureRandom random) {
        return new SecretGenerator(random);
    }
}
