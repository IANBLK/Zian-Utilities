package com.zianblk.zianutilities.neoforge.pase;

import com.zianblk.zianutilities.core.pase.PlacedBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Blocks placed by players in one dimension (see {@link PlacedBlocks}), saved with the world. A future format is left untouched.
 * Known limit: a piston moves a block but not its mark, so a placed block pushed by a piston can count at its new position.
 */
public final class PaseBlockMarks extends SavedData {
  static final int FORMAT_VERSION = 1;
  static final String NAME = "zianutilities_pase_marks";
  static final SavedData.Factory<PaseBlockMarks> FACTORY = new SavedData.Factory<>(PaseBlockMarks::new, PaseBlockMarks::load);

  private final PlacedBlocks marks = new PlacedBlocks();
  private boolean frozen;

  static PaseBlockMarks of(ServerLevel level) {
    return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
  }

  boolean mark(BlockPos pos) {
    if (frozen || !marks.mark(pos.getX(), pos.getY(), pos.getZ())) return false;
    setDirty();
    return true;
  }

  /** True if a player had placed the block (the mark is removed). */
  boolean consume(BlockPos pos) {
    if (frozen || !marks.consume(pos.getX(), pos.getY(), pos.getZ())) return false;
    setDirty();
    return true;
  }

  /** The chunk ran out of marks: nothing broken there pays. */
  boolean saturated(BlockPos pos) { return marks.saturated(pos.getX(), pos.getZ()); }

  boolean isMarked(BlockPos pos) { return marks.isMarked(pos.getX(), pos.getY(), pos.getZ()); }

  int size() { return marks.size(); }

  static PaseBlockMarks load(CompoundTag tag, HolderLookup.Provider registries) {
    PaseBlockMarks data = new PaseBlockMarks();
    if (tag.getInt("formatVersion") > FORMAT_VERSION) {
      PaseModule.LOG.error("Las marcas de bloques colocados son de una versión más nueva; no se leen ni se tocan");
      data.frozen = true;
      return data;
    }
    for (long key : tag.getLongArray("saturated")) data.marks.loadSaturated(key);
    for (Tag t : tag.getList("chunks", Tag.TAG_COMPOUND)) {
      CompoundTag c = (CompoundTag) t;
      data.marks.load(c.getLong("chunk"), c.getIntArray("marks"));
    }
    return data;
  }

  @Override
  public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
    tag.putInt("formatVersion", FORMAT_VERSION);
    ListTag chunks = new ListTag();
    for (long key : marks.chunkKeys()) {
      CompoundTag c = new CompoundTag();
      c.putLong("chunk", key);
      c.putIntArray("marks", marks.marksOf(key));
      chunks.add(c);
    }
    tag.put("chunks", chunks);
    tag.putLongArray("saturated", marks.saturatedKeys().stream().mapToLong(Long::longValue).toArray());
    return tag;
  }

  @Override
  public boolean isDirty() { return !frozen && super.isDirty(); }
}
