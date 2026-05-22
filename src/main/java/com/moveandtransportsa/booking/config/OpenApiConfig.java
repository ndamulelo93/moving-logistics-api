package com.moveandtransportsa.booking.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the OpenAPI / Swagger UI documentation.
 *
 * <p>The Bearer JWT security scheme is declared here so that all controllers
 * can reference it by name ("bearerAuth") via {@code @SecurityRequirement}.</p>
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Move and Transport SA API",
                version = "1.0",
                description = "Transport Booking Platform API"
        ),
        security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class OpenApiConfig {
}
