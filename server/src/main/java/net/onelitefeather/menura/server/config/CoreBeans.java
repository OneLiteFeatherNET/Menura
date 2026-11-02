package net.onelitefeather.menura.server.config;

import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;
import net.onelitefeather.menura.core.id.IdentifierGenerator;
import net.onelitefeather.menura.core.secret.SecretGenerator;

import java.security.SecureRandom;
import java.time.Clock;

/** Provides the core utilities and the system clock to the server's beans. Tests replace them where they need control. */
@Factory
public class CoreBeans {

    @Singleton
    SecureRandom secureRandom() {
        return new SecureRandom();
    }

    @Singleton
    Clock clock() {
        return Clock.systemUTC();
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
