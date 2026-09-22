package com.mugloar.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Pins the two ciphers. This is business logic, not transport: which cipher a given {@code
 * encrypted} value means was established by reading live samples, and getting it wrong corrupts
 * every affected quest silently rather than failing.
 *
 * <p>The vectors are the ones recorded in {@link MessageDecoder}'s own provenance note, so this
 * test and that documentation stand or fall together.
 */
class MessageDecoderTest {

  @Test
  void plaintextPassesThroughWhenNotEncrypted() {
    assertEquals("Suicide mission", MessageDecoder.decodeField("Suicide mission", null));
  }

  @Test
  void cipherOneIsBase64() {
    assertEquals("Suicide mission", MessageDecoder.decodeField("U3VpY2lkZSBtaXNzaW9u", 1));
  }

  @Test
  void cipherTwoIsRot13() {
    assertEquals("Suicide mission", MessageDecoder.decodeField("Fhvpvqr zvffvba", 2));
  }

  /**
   * The wrong cipher must not silently produce garbage. ROT13 text is not valid Base64, so cipher 1
   * falls back to the raw value — the same input only decodes under the cipher it was encoded with.
   */
  @Test
  void theWrongCipherDoesNotSilentlyProduceGarbage() {
    assertEquals("Fhvpvqr zvffvba", MessageDecoder.decodeField("Fhvpvqr zvffvba", 1));
    assertEquals("Suicide mission", MessageDecoder.decodeField("Fhvpvqr zvffvba", 2));
  }

  @Test
  void rot13LeavesNonLettersAlone() {
    assertEquals("Gur 3 qentbaf!", MessageDecoder.decodeField("The 3 dragons!", 2));
  }

  @Test
  void rot13IsItsOwnInverse() {
    String once = MessageDecoder.decodeField("Steal the crown jewels", 2);
    assertEquals("Steal the crown jewels", MessageDecoder.decodeField(once, 2));
  }

  /** "Decoding never throws" — an undecodable field keeps its raw value. */
  @Test
  void undecodableBase64KeepsTheRawValue() {
    assertEquals("not base64 !!", MessageDecoder.decodeField("not base64 !!", 1));
  }

  /** A cipher id this build has never seen must pass the value through, not mangle or drop it. */
  @Test
  void unknownCipherPassesThrough() {
    assertEquals("Escort a merchant", MessageDecoder.decodeField("Escort a merchant", 7));
  }
}
