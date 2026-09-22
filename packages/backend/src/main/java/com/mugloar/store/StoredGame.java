package com.mugloar.store;

import com.mugloar.domain.Ad;
import com.mugloar.domain.GameState;
import com.mugloar.domain.ShopItem;
import java.util.List;

/**
 * One live session. {@code ads} is the board as it stood after the last action — the upstream API
 * has no get-state endpoint, so re-reading it costs a request. Caching it here is what lets a turn
 * reuse the board the previous turn already fetched instead of asking for it again.
 */
public record StoredGame(GameState state, List<ShopItem> shop, List<Ad> ads) {}
