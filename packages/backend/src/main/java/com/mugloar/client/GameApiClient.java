package com.mugloar.client;

import com.mugloar.config.GameProperties;
import com.mugloar.domain.Ad;
import com.mugloar.domain.BuyResponse;
import com.mugloar.domain.GameState;
import com.mugloar.domain.Reputation;
import com.mugloar.domain.ShopItem;
import com.mugloar.domain.SolveResponse;
import com.mugloar.error.GameApiException;
import com.mugloar.game.MessageDecoder;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Typed client for the Dragons of Mugloar API.
 *
 * <p><b>Direction:</b> every method here makes an <em>outbound</em> call to
 * {@code dragonsofmugloar.com} — this is us consuming their API. It is not a controller and holds
 * no routes. The inbound counterpart, which serves the SPA at {@code /api/**}, is
 * {@code web.GameController}; several method names deliberately match it, but the two face
 * opposite ways: a browser calls {@code GameController.solve}, which eventually calls
 * {@code GameApiClient.solve}, which calls the game server.
 *
 * <p>Request building, URL encoding and JSON binding are {@link RestClient}'s. What stays hand
 * written is the part it does not cover: retry with backoff on transient failures, and the ad
 * decoding, which needs the raw JSON because each field is decrypted individually.
 */
@Component
public class GameApiClient {

  private final RestClient http;
  private final ObjectMapper mapper;
  private final int maxRetries;

  @Autowired
  public GameApiClient(GameProperties props, RestClient.Builder builder, ObjectMapper mapper) {
    this(props.api(), builder, mapper);
  }

  /**
   * For use outside Spring (the characterization tool), where the caller supplies
   * {@code RestClient.builder()} itself.
   */
  public GameApiClient(GameProperties.Api api, RestClient.Builder builder, ObjectMapper mapper) {
    this.mapper = mapper;
    this.maxRetries = api.maxRetries();
    this.http = builder
        .baseUrl(api.baseUrl().replaceAll("/$", ""))
        .defaultHeader("Accept", "application/json")
        .defaultHeader("User-Agent", "DragonsOfMugloarBot/1.0")
        .requestFactory(requestFactory(Duration.ofMillis(api.timeoutMs())))
        // Every non-2xx becomes a GameApiException carrying the upstream status, which is what
        // withRetry() reads to decide whether the failure is worth another attempt.
        // Note the braces: ErrorHandler.handle returns void, so an expression lambda here would
        // build the exception and silently discard it, and RestClient would carry on parsing the
        // error body as if it were a result.
        .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
          throw upstreamError(
              response.getStatusCode().value(), request.getMethod(), request.getURI());
        })
        .build();
  }

  public GameState startGame() {
    return withRetry("POST /game/start",
        () -> http.post().uri("/game/start").retrieve().body(GameState.class));
  }

  public List<ShopItem> getShop(String gameId) {
    return withRetry("GET /{gameId}/shop", () -> List.of(http.get()
        .uri("/{gameId}/shop", gameId)
        .retrieve()
        .body(ShopItem[].class)));
  }

  public SolveResponse solve(String gameId, String adId) {
    return withRetry("POST /{gameId}/solve/{adId}", () -> http.post()
        .uri("/{gameId}/solve/{adId}", gameId, adId)
        .retrieve()
        .body(SolveResponse.class));
  }

  public BuyResponse buy(String gameId, String itemId) {
    return withRetry("POST /{gameId}/shop/buy/{itemId}", () -> http.post()
        .uri("/{gameId}/shop/buy/{itemId}", gameId, itemId)
        .retrieve()
        .body(BuyResponse.class));
  }

  public Reputation getReputation(String gameId) {
    return withRetry("POST /{gameId}/investigate/reputation", () -> http.post()
        .uri("/{gameId}/investigate/reputation", gameId)
        .retrieve()
        .body(Reputation.class));
  }

  /**
   * The one endpoint that is not a plain bind. Each field carries its own encryption and
   * {@code reward}/{@code expiresIn} sometimes arrive as strings, so the board is read as a tree
   * and assembled by hand.
   */
  public List<Ad> getMessages(String gameId) {
    String json = withRetry("GET /{gameId}/messages",
        () -> http.get().uri("/{gameId}/messages", gameId).retrieve().body(String.class));
    List<Ad> ads = new ArrayList<>();
    for (JsonNode node : mapper.readTree(json)) {
      ads.add(toAd(node));
    }
    return ads;
  }

  private static Ad toAd(JsonNode node) {
    JsonNode cipher = node.get("encrypted");
    Integer encrypted = cipher.isNull() ? null : cipher.asInt();
    return new Ad(
        decoded(node, "adId", encrypted),
        decoded(node, "message", encrypted),
        coerceInt(node.get("reward")),
        coerceInt(node.get("expiresIn")),
        encrypted,
        decoded(node, "probability", encrypted));
  }

  private static String decoded(JsonNode node, String field, Integer encrypted) {
    return MessageDecoder.decodeField(node.get(field).asString(), encrypted);
  }

  /**
   * Retries transient failures only: a 5xx or an unreachable server. A 4xx is the game telling us
   * something — a dead or expired game surfaces that way — so it is surfaced immediately.
   */
  private <T> T withRetry(String what, Supplier<T> call) {
    GameApiException last = null;
    for (int attempt = 0; attempt <= maxRetries; attempt++) {
      try {
        return call.get();
      } catch (GameApiException e) {
        if (!isTransient(e)) {
          throw e;
        }
        last = e;
      } catch (ResourceAccessException e) {
        last = new GameApiException(502, "Game API unreachable on " + what + ": " + e.getMessage());
      }
      if (attempt >= maxRetries) {
        throw last;
      }
      backoff(attempt);
    }
    throw last != null ? last
        : new GameApiException(502, "Game API unreachable after " + (maxRetries + 1)
            + " attempts on " + what);
  }

  private static boolean isTransient(GameApiException e) {
    return e.upstreamStatus() == null || e.upstreamStatus() >= 500;
  }

  private static GameApiException upstreamError(int status, Object method, Object uri) {
    return new GameApiException(
        mapUpstream(status), "Game API responded " + status + " on " + method + " " + uri, status);
  }

  private static void backoff(int attempt) {
    try {
      Thread.sleep(backoffMillis(attempt));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /** Doubles per attempt: 200ms, 400ms, 800ms, … */
  private static long backoffMillis(int attempt) {
    return 200L * (1L << attempt);
  }

  private static int mapUpstream(int status) {
    // A dead/expired/gone game surfaces as 400/403/404/410 upstream — expose it as 404 (not a 502).
    return switch (status) {
      case 400, 403, 404, 410 -> 404;
      default -> 502;
    };
  }

  private static ClientHttpRequestFactory requestFactory(Duration timeout) {
    JdkClientHttpRequestFactory factory =
        new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build());
    factory.setReadTimeout(timeout);
    return factory;
  }

  private static int coerceInt(JsonNode node) {
    if (node == null || node.isNull()) {
      return 0;
    }
    if (node.isNumber()) {
      return node.asInt();
    }
    try {
      return Integer.parseInt(node.asString().trim());
    } catch (NumberFormatException e) {
      return 0;
    }
  }
}
