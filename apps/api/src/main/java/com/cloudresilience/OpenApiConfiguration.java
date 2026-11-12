package com.cloudresilience;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(title = "CloudResilience API", version = "v1", description = "Identity and application foundation API"))
public class OpenApiConfiguration {}
