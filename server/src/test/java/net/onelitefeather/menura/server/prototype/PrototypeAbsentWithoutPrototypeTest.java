package net.onelitefeather.menura.server.prototype;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The production environment alone, without {@code prototype}: nothing of the prototype exists, and its API answers as
 * an unknown route. The context starts without a reachable database, as in the production configuration.
 */
@MicronautTest(environments = "prod")
class PrototypeAbsentWithoutPrototypeTest {

    private static final String PACKAGE = PrototypeAbsentWithoutPrototypeTest.class.getPackageName();

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    ApplicationContext context;

    @Test
    void noBeanOfThePrototypePackageIsDefined() {
        // The serialisers Micronaut generates for the records are not beans of the prototype; they carry no behaviour.
        List<String> prototypeBeans = context.getAllBeanDefinitions().stream()
                .map(definition -> definition.getBeanType())
                .filter(type -> type.getPackageName().equals(PACKAGE))
                .filter(type -> !type.getSimpleName().startsWith("Serde"))
                .filter(type -> !type.equals(PrototypeAbsentWithoutPrototypeTest.class))
                .map(Class::getName)
                .toList();

        assertTrue(prototypeBeans.isEmpty(), "prototype beans in prod alone: " + prototypeBeans);
    }

    @Test
    void theDemoStoreIsNotInTheContext() {
        assertFalse(context.containsBean(DemoStore.class));
    }

    @Test
    void anAnonymousRequestToTheSeriesApiIsRefusedBeforeRouting() {
        HttpClientResponseException refused = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(HttpRequest.GET("/api/prototype/series"), String.class));

        assertEquals(HttpStatus.UNAUTHORIZED, refused.getStatus());
    }

    @Test
    void aSignedInRequestToTheSeriesApiFindsNoRoute() {
        HttpClientResponseException refused = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(HttpRequest.GET("/api/prototype/series")
                        .header("X-Test-Person", "tester-1")
                        .header("X-Test-Roles", "VIEWER"), String.class));

        assertEquals(HttpStatus.NOT_FOUND, refused.getStatus());
    }
}
