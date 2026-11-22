package com.margomarket.shared;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI specification and Swagger UI setup, switched on and off by the required SWAGGER_ENABLED flag.
 */
@Configuration
public class OpenApiConfig {

    static final String BEARER = "bearerAuth";
    private static final String ERROR_SCHEMA = "ApiError";

    @Bean
    public OpenAPI margoMarketOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("MargoMarket API")
                        .version("v1")
                        .description("""
                                REST API giełdy graczy Margonem: ogłoszenia przedmiotów i złota, aukcje, rozmowy, \
                                komentarze, opinie o sprzedających, ceny oraz panel administracji.

                                Uwierzytelnianie: token JWT z `POST /api/auth/login` przekazywany w nagłówku \
                                `Authorization: Bearer <token>` (przycisk **Authorize**). Publiczne endpointy GET \
                                działają bez tokenu, a `/api/admin/**` wymaga roli ADMIN.

                                Kwoty w PLN są podawane w groszach, a złoto w pojedynczych jednostkach. \
                                Czat w czasie rzeczywistym (STOMP, `/ws/conversations`) nie jest częścią tej specyfikacji."""))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }

    /** Adds the shared error body to every operation as a 4XX response. */
    @Bean
    public OpenApiCustomizer errorResponseCustomizer() {
        return openApi -> {
            var resolved = ModelConverters.getInstance().resolveAsResolvedSchema(new AnnotatedType(ApiError.class));
            openApi.getComponents().addSchemas(ERROR_SCHEMA, resolved.schema);
            resolved.referencedSchemas.forEach(openApi.getComponents()::addSchemas);
            var error = new ApiResponse()
                    .description("Błąd żądania (walidacja, brak uprawnień, ograniczenie konta, konflikt). "
                            + "Ograniczenia konta i limity zapytań zwracają dodatkowe pola, np. `code` i `retryAfterSeconds`.")
                    .content(new Content().addMediaType("application/json",
                            new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + ERROR_SCHEMA))));
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().values().forEach(path -> path.readOperations()
                    .forEach(operation -> operation.getResponses().addApiResponse("4XX", error)));
        };
    }

    @Bean
    public GroupedOpenApi userApi(OpenApiCustomizer errorResponseCustomizer) {
        return GroupedOpenApi.builder()
                .group("uzytkownik")
                .displayName("Użytkownik i publiczne")
                .pathsToMatch("/api/**")
                .pathsToExclude("/api/admin/**")
                .addOpenApiCustomizer(errorResponseCustomizer)
                .build();
    }

    @Bean
    public GroupedOpenApi adminApi(OpenApiCustomizer errorResponseCustomizer) {
        return GroupedOpenApi.builder()
                .group("administracja")
                .displayName("Administracja")
                .pathsToMatch("/api/admin/**")
                .addOpenApiCustomizer(errorResponseCustomizer)
                .build();
    }
}
