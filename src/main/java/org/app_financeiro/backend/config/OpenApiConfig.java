package org.app_financeiro.backend.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Configuração global do OpenAPI (Swagger).
 * Define informações gerais da API e esquema de segurança JWT Bearer.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Equilibra API - Sistema de Controle Financeiro",
                version = "1.0",
                description = "API REST para gestão de finanças pessoais, incluindo transações, contas, categorias e investimentos.",
                contact = @Contact(
                        name = "João Victor",
                        email = "suporte@equilibra.com"
                )
        ),
        security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Entre com o token JWT (accessToken) recebido no login para autenticar as requisições."
)
public class OpenApiConfig {
}
