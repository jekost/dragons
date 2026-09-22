package com.mugloar.web;

import com.mugloar.config.GameProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Enables CORS for the SPA against the /api routes. */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

  private final GameProperties props;

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/api/**")
        .allowedOrigins(props.cors().allowedOrigin())
        .allowedMethods("GET", "POST", "OPTIONS");
  }
}
