package uz.hayotbank.p2ptransfer.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

// the spec itself is built from the controllers; here only what can't be guessed from the code
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("P2P transfers")
                        .version("1.0")
                        .description("Money transfers between users. Every transfer is confirmed by an external vendor: "
                                + "the money is reserved first and moves only after the vendor approves. "
                                + "Log in with POST /api/auth/login and send the token as `Authorization: Bearer <token>`. "
                                + "Errors come as RFC 9457 problem details (application/problem+json)."))
                .components(new Components()
                        .addSchemas("ProblemDetail", problemDetailSchema())
                        // the "Authorize" button in Swagger UI: paste the accessToken from the login there
                        .addSecuritySchemes("bearer", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                // every endpoint needs the token, except the ones with an empty @SecurityRequirements
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }

    // every error has the same format, so it is set here once and not on each endpoint
    @Bean
    public OperationCustomizer errorsAsProblemDetails() {
        Content problemDetail = new Content().addMediaType("application/problem+json",
                new MediaType().schema(new Schema<>().$ref("#/components/schemas/ProblemDetail")));

        return (operation, handlerMethod) -> {
            for (Map.Entry<String, ApiResponse> response : operation.getResponses().entrySet()) {
                if (response.getKey().startsWith("4") || response.getKey().startsWith("5")) {
                    response.getValue().setContent(problemDetail);
                }
            }
            return operation;
        };
    }

    // what GlobalExceptionHandler sends back
    private static Schema<?> problemDetailSchema() {
        Schema<?> fieldError = new ObjectSchema()
                .addProperty("field", new StringSchema().example("amount"))
                .addProperty("message", new StringSchema().example("must be greater than 0"));

        return new ObjectSchema()
                .addProperty("type", new StringSchema().example("about:blank"))
                .addProperty("title", new StringSchema().example("Unprocessable Content"))
                .addProperty("status", new IntegerSchema().example(422))
                .addProperty("detail", new StringSchema().example("User 4 does not have enough money for this transfer"))
                .addProperty("instance", new StringSchema().example("/api/transfers"))
                .addProperty("errors", new ArraySchema()
                        .description("only when fields or parameters are invalid")
                        .items(fieldError));
    }
}
