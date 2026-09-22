package com.mugloar.game;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Decodes encrypted quest fields.
 *
 * <p>PROVENANCE (validated against live samples on 2026-09-15):
 * <ul>
 *   <li>{@code encrypted == 1} → Base64 (e.g. "U3VpY2lkZSBtaXNzaW9u" → "Suicide mission")</li>
 *   <li>{@code encrypted == 2} → ROT13   (e.g. "Fhvpvqr zvffvba"      → "Suicide mission")</li>
 *   <li>{@code null} → plaintext</li>
 * </ul>
 * Decoding never throws: a field that cannot be decoded keeps its raw value.
 */
public final class MessageDecoder {

  private MessageDecoder() {}

  public static String decodeField(String value, Integer encrypted) {
    if (encrypted == null) {
      return value;
    }
    return switch (encrypted) {
      case 1 -> base64(value);
      case 2 -> rot13(value);
      default -> value;
    };
  }

  private static String base64(String value) {
    try {
      return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      return value; // not valid Base64 — keep the raw value
    }
  }

  private static String rot13(String value) {
    char[] chars = value.toCharArray();
    for (int i = 0; i < chars.length; i++) {
      char c = chars[i];
      if (c >= 'a' && c <= 'z') {
        chars[i] = rotate(c, 'a');
      } else if (c >= 'A' && c <= 'Z') {
        chars[i] = rotate(c, 'A');
      }
    }
    return new String(chars);
  }

  /** Rotates one letter 13 places within its own 26-letter alphabet, {@code base} being its first. */
  private static char rotate(char c, char base) {
    return (char) (base + (c - base + 13) % 26);
  }
}
