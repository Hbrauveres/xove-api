package dev.hbrauveres.xove.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The parts of the OpenAPI description the controllers can't say themselves (ADR-0067): its
 * title and version, a server of "/" (so the file never names a host or a port), how a caller
 * signs in, and the errors every protected call can get. Only used when XOVE_API_DOCS=true.
 */
@Configuration
public class OpenApiConfig {

    static final String SESSION = "session";
    static final String PROBLEM = "Problem";
    public static final String PROBLEM_REF = "#/components/schemas/" + PROBLEM;
    public static final String PROBLEM_JSON = "application/problem+json";

    /** LiveKit's webhook has no session: it proves itself with its signature. */
    private static final String WEBHOOK = "/api/livekit/webhook";

    @Bean
    OpenAPI xoveOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Xovê API")
                        .version("v1")
                        .description("""
                                The API of Xovê, a private room where invited friends share their screens \
                                and cameras. Every path is under /api, on the web app's own origin. A \
                                signed-in call carries the session cookie; every POST, PUT and DELETE also \
                                sends the XSRF-TOKEN cookie's value in the X-XSRF-TOKEN header. Errors are \
                                RFC 9457 problem details. Signing in (Google) and logging out aren't listed: \
                                see docs/api.md."""))
                .servers(List.of(new Server().url("/")))
                .components(new Components()
                        .addSecuritySchemes(SESSION, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("XOVE_SESSION")
                                .description("The session cookie, set after signing in with Google"))
                        .addSchemas(PROBLEM, problemSchema()));
    }

    /** Every operation but the webhook needs a session, and can answer 401 and 403. */
    @Bean
    OpenApiCustomizer protectedCallsCustomizer() {
        return openApi -> openApi.getPaths().forEach((path, item) -> {
            if (path.equals(WEBHOOK)) {
                return;
            }
            item.readOperationsMap().forEach((method, operation) -> protect(method, operation));
        });
    }

    private static void protect(PathItem.HttpMethod method, Operation operation) {
        operation.addSecurityItem(new SecurityRequirement().addList(SESSION));
        ApiResponses responses = operation.getResponses();
        responses.putIfAbsent("401", problem("Not signed in"));
        responses.putIfAbsent("403", problem(method == PathItem.HttpMethod.GET
                ? "Not allowed (a member or admin call from someone who isn't one)"
                : "Without the CSRF token, or not allowed"));
    }

    static ApiResponse problem(String description) {
        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType(PROBLEM_JSON,
                        new MediaType().schema(new Schema<>().$ref(PROBLEM_REF))));
    }

    private static Schema<?> problemSchema() {
        return new ObjectSchema()
                .description("RFC 9457 problem details; the web app shows detail")
                .addProperty("type", new StringSchema())
                .addProperty("title", new StringSchema())
                .addProperty("status", new IntegerSchema())
                .addProperty("detail", new StringSchema().description("A message for the person"))
                .addProperty("instance", new StringSchema())
                .addProperty("reason", new StringSchema()
                        .description("Only on some answers, for the web app: \"full\" when the 6 places are taken"));
    }
}
