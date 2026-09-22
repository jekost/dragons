package com.mugloar.domain;

import java.util.List;

/** The full picture of a game at a point in time, served to the frontend. */
public record GameSnapshot(
    GameState state,
    List<ShopItem> shop,
    List<Ad> ads,
    Decision recommendation) {}
