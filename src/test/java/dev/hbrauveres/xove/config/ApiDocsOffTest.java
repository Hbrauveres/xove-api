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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ApiDocsOffTest {

    @Value("${local.server.port}")
    int port;

    @Test
    void theDescriptionAndItsPageAreNotServed() throws Exception {
        assertThat(get("/api/docs")).isEqualTo(404);
        assertThat(get("/api/docs/ui")).isEqualTo(404);
    }

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
