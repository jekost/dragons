package com.mugloar.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Title and description for the generated spec; the endpoints themselves are scanned. */
@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI dragonsOpenApi() {
    return new OpenAPI()
        .info(new Info()
            .title("Dragons of Mugloar backend")
            .version("1.0.0")
            .description("Plays Dragons of Mugloar on the SPA's behalf. Every move endpoint spends an"
                + " upstream turn, including investigate."));
  }
}
