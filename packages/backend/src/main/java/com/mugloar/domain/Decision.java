package com.mugloar.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A strategy decision. Modeled as one flat record with a {@code type} discriminator and
 * null-omitted fields so the JSON matches the frontend's discriminated-union contract exactly:
 *   {@code {type:"solve", adId, ad, probability, expectedValue, reason}}
 *   {@code {type:"buy",   itemId, item, reason}}
 *   {@code {type:"stop",  reason}}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Decision(
    DecisionType type,
    String adId,
    Ad ad,
    String itemId,
    ShopItem item,
    Double probability,
    Double expectedValue,
    String reason) {

  public static Decision solve(Ad ad, double probability, double expectedValue, String reason) {
    return new Decision(
        DecisionType.SOLVE, ad.adId(), ad, null, null, probability, expectedValue, reason);
  }

  public static Decision buy(ShopItem item, String reason) {
    return new Decision(DecisionType.BUY, null, null, item.id(), item, null, null, reason);
  }

  public static Decision stop(String reason) {
    return new Decision(DecisionType.STOP, null, null, null, null, null, null, reason);
  }
}
