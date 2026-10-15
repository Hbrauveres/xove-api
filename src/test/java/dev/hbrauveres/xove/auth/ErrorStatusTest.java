package dev.hbrauveres.xove.auth;

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
 * The status a caller gets on a real server. A refused request is forwarded to the error
 * page; MockMvc never does that forward, so only a running server shows that the error page
 * passes the real status on, signed in or not.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ErrorStatusTest {

    @Value("${local.server.port}")
    int port;

    @Test
    void aSignedOutChangeWithoutTheCsrfTokenGets403() throws Exception {
        var streams = send("POST", "/api/streams");
        assertThat(streams.statusCode()).isEqualTo(403);
        assertThat(send("POST", "/api/auth/logout").statusCode()).isEqualTo(403);

        // The error page says what happened, never how: no stack trace or exception
        assertThat(streams.body()).doesNotContain("trace").doesNotContain("exception");
    }

    @Test
    void aSignedOutReadStillGets401() throws Exception {
        assertThat(send("GET", "/api/me").statusCode()).isEqualTo(401);
    }

    private HttpResponse<String> send(String method, String path) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
}
