package com.albert.catalog.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun customOpenAPI(): OpenAPI {
        val securitySchemeName = "basicAuth"

        return OpenAPI()
            .info(
                Info()
                    .title("Product Catalog API")
                    .description("A comprehensive product catalog management system with CRUD operations and CSV import functionality")
                    .version("1.0.0")
                    .contact(
                        Contact()
                            .name("Albert Team")
                            .email("support@albert.com"),
                    )
                    .license(
                        License()
                            .name("MIT License")
                            .url("https://opensource.org/licenses/MIT"),
                    ),
            )
            .servers(
                listOf(
                    Server()
                        .url("http://localhost:8080")
                        .description("Development server"),
                    Server()
                        .url("https://api.albert.com")
                        .description("Production server"),
                ),
            )
            .components(
                Components()
                    .addSecuritySchemes(
                        securitySchemeName,
                        SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("basic")
                            .name("Basic Authentication")
                            .description("HTTP Basic Authentication. Use 'admin' as username and 'admin123' as password."),
                    ),
            )
            .addSecurityItem(SecurityRequirement().addList(securitySchemeName))
    }
}
