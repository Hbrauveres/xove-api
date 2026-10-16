package dev.hbrauveres.xove.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hbrauveres.xove.TestcontainersConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * With the servers' settings (XOVE_API_DOCS not set), the API serves no description and no
 * interactive page: their addresses answer 404, and springdoc's usual ones never answer 200.
 */
// Off on purpose, even when a developer's terminal has XOVE_API_DOCS=true
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "XOVE_API_DOCS=false")
@Import(TestcontainersConfiguration.class)
class ApiDocsOffTest {

    @Value("${local.server.port}")
    int port;

    @Test
    void theDescriptionAndItsPageAreNotServed() throws Exception {
        // Every address /api/docs/** lets through without sign-in: nothing answers them
        for (String path : new String[] {"/api/docs", "/api/docs/", "/api/docs/ui", "/api/docs/swagger-config",
                "/api/docs/swagger-ui/index.html", "/api/docs/swagger-ui/swagger-ui.css"}) {
            assertThat(get(path)).as(path).isEqualTo(404);
        }
    }

    // springdoc's usual addresses need sign-in like any other: never open to everyone
    @Test
    void springdocsUsualAddressesAreNotServedEither() throws Exception {
        for (String path : new String[] {"/v3/api-docs", "/swagger-ui.html", "/swagger-ui/index.html"}) {
            assertThat(get(path)).as(path).isNotEqualTo(200);
        }
    }

    private int get(String path) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
