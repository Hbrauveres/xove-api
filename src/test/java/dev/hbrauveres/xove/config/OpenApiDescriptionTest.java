package dev.hbrauveres.xove.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hbrauveres.xove.TestcontainersConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The committed description (docs/openapi.json) is exactly what the code generates, and
 * docs/api.md's table lists the same endpoints (ADR-0067). When an endpoint changes, this
 * fails until the file is written again:
 *
 *   ./mvnw test -Dtest=OpenApiDescriptionTest -Dopenapi.write=true
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "XOVE_API_DOCS=true")
@Import(TestcontainersConfiguration.class)
class OpenApiDescriptionTest {

    static final Path COMMITTED = Path.of("docs/openapi.json");
    static final Path GENERATED = Path.of("target/openapi.json");
    static final Path API_PAGE = Path.of("docs/api.md");

    /** In api.md's table but served by Spring Security or Actuator, not by a controller. */
    static final Set<String> NOT_CONTROLLERS = Set.of(
            "GET /api/health",
            "GET /api/oauth2/authorization/google",
            "GET /api/login/oauth2/code/google",
            "POST /api/auth/logout");

    static final JsonMapper JSON = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

    @Value("${local.server.port}")
    int port;

    @Test
    void theCommittedDescriptionIsWhatTheCodeGenerates() throws Exception {
        String generated = generated();
        Files.createDirectories(GENERATED.getParent());
        Files.writeString(GENERATED, generated);
        if (Boolean.getBoolean("openapi.write")) {
            Files.writeString(COMMITTED, generated);
        }

        assertThat(Files.exists(COMMITTED) ? Files.readString(COMMITTED) : "")
                .as("docs/openapi.json is out of date: run ./mvnw test -Dtest=OpenApiDescriptionTest -Dopenapi.write=true")
                .isEqualTo(generated);
    }

    @Test
    void apiMdListsTheSameEndpoints() throws Exception {
        Set<String> described = new TreeSet<>(NOT_CONTROLLERS);
        JSON.readTree(generated()).get("paths").properties().forEach(path ->
                path.getValue().propertyNames().forEach(method ->
                        described.add(method.toUpperCase() + " " + path.getKey())));

        Set<String> listed = new TreeSet<>();
        var row = Pattern.compile("^\\| `(GET|POST|PUT|DELETE|PATCH) (/api/[^`]*)` \\|", Pattern.MULTILINE).matcher(
                Files.readString(API_PAGE));
        while (row.find()) {
            listed.add(row.group(1) + " " + row.group(2));
        }

        assertThat(listed).as("docs/api.md's table").isEqualTo(described);
    }

    private String generated() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/docs")).build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode description = JSON.readTree(response.body());
        return JSON.writeValueAsString(description) + "\n";
    }
}
