package com.zianblk.zianutilities.core.pase;

/** A player reached a level. P2 hands out the reward and then drops the event. {@code extra} levels count from 1 after the 200th. */
public record LevelEvent(String seasonId, int level, boolean extra, long atMillis) {}
