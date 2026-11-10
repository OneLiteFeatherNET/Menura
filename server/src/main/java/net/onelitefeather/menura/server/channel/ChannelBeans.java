package net.onelitefeather.menura.server.channel;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import net.onelitefeather.menura.core.channel.ChannelAudit;
import net.onelitefeather.menura.core.channel.ChannelStore;
import net.onelitefeather.menura.core.channel.ConnectionService;
import net.onelitefeather.menura.core.channel.HttpTransport;
import net.onelitefeather.menura.core.channel.JdbcChannelStore;
import net.onelitefeather.menura.core.channel.JdkHttpTransport;
import net.onelitefeather.menura.core.channel.PlatformAdapter;
import net.onelitefeather.menura.core.channel.PlatformRegistry;
import net.onelitefeather.menura.core.channel.youtube.YouTubeAdapter;
import net.onelitefeather.menura.core.channel.youtube.YouTubeAdapters;
import net.onelitefeather.menura.core.channel.youtube.YouTubeMode;
import net.onelitefeather.menura.core.db.Transactions;
import net.onelitefeather.menura.core.id.IdentifierGenerator;
import net.onelitefeather.menura.core.secret.SecretGenerator;
import net.onelitefeather.menura.core.secret.TokenCipher;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;

/**
 * Wires the channel connection layer of the core into the server. The token cipher is created eagerly, so a server
 * without {@code MENURA_TOKEN_ENCRYPTION_KEY} stops at startup with the message that names the variable.
 */
@Factory
public class ChannelBeans {

    /** Fails the startup when the primary key is missing or malformed. */
    @Context
    @Singleton
    TokenCipher tokenCipher(SecureRandom random,
                            @Value("${menura.channels.token-encryption-key:}") String primaryKey,
                            @Value("${menura.channels.token-encryption-key-previous:}") String previousKey) {
        return TokenCipher.of(primaryKey, previousKey, random);
    }

    @Singleton
    ChannelStore channelStore(TokenCipher cipher) {
        return new JdbcChannelStore(cipher);
    }

    @Singleton
    HttpTransport httpTransport() {
        return new JdkHttpTransport();
    }

    /** Live by default; {@code simulated} runs YouTube locally, and an unknown mode stops the startup. */
    @Singleton
    PlatformAdapter youTubeAdapter(Clock clock, HttpTransport http,
                                   @Value("${menura.channels.youtube.client-id:}") String clientId,
                                   @Value("${menura.channels.youtube.client-secret:}") String clientSecret,
                                   @Value("${menura.channels.youtube.redirect-uri}") String redirectUri,
                                   @Value("${menura.channels.youtube.mode:live}") String mode) {
        return YouTubeAdapters.create(clock, new YouTubeAdapter.Settings(clientId, clientSecret, redirectUri),
                YouTubeMode.parse(mode), http);
    }

    @Singleton
    PlatformRegistry platformRegistry(PlatformAdapter youtube) {
        return new PlatformRegistry(java.util.List.of(youtube));
    }

    @Singleton
    ConnectionService connectionService(Transactions transactions, Clock clock, IdentifierGenerator identifiers,
                                        SecretGenerator secrets, ChannelStore store, ChannelAudit audit,
                                        PlatformRegistry platforms,
                                        @Value("${menura.channels.refresh-margin:PT10M}") String refreshMargin,
                                        @Value("${menura.channels.quota-warning-ratio:0.8}") double quotaWarningRatio) {
        return new ConnectionService(transactions, clock, identifiers, secrets, store, audit, platforms,
                Duration.parse(refreshMargin), quotaWarningRatio);
    }
}
