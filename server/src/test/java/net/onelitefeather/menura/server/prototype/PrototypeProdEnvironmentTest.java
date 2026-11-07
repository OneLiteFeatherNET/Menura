package net.onelitefeather.menura.server.prototype;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The deployment sets MICRONAUT_ENVIRONMENTS=prod,prototype. The server must start and answer health without a
 * database, even though the production configuration names one.
 */
@MicronautTest(environments = {"prod", "prototype"})
class PrototypeProdEnvironmentTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    ApplicationContext context;

    @Test
    void prodPlusPrototypeStartsAndAnswersHealthWithoutADatabase() {
        Map<?, ?> body = client.toBlocking().retrieve(HttpRequest.GET("/health"), Map.class);

        assertEquals("IN_MEMORY", body.get("database"));
    }

    @Test
    void prodPlusPrototypeCreatesNoDataSource() {
        assertFalse(context.containsBean(DataSource.class));
    }
}
