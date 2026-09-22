package com.mugloar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Bound from {@code game.*} in application.properties. */
@ConfigurationProperties("game")
public record GameProperties(Api api, Cors cors) {

  public record Api(String baseUrl, long timeoutMs, int maxRetries) {}

  public record Cors(String allowedOrigin) {}
}
