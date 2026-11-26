package com.zianblk.zianutilities.neoforge.raids;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Applies the raid duel mixin only when Cobblemon Raid Dens is loaded; without it the module is inert. */
public final class RaidsMixinPlugin implements IMixinConfigPlugin {
  public static final String RAID_DENS = "cobblemonraiddens";

  static boolean applies(boolean raidDensLoaded) { return raidDensLoaded; }

  @Override public void onLoad(String mixinPackage) {}
  @Override public String getRefMapperConfig() { return null; }

  @Override
  public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
    return applies(net.neoforged.fml.loading.LoadingModList.get().getModFileById(RAID_DENS) != null);
  }

  @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
  @Override public List<String> getMixins() { return null; }
  @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
  @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
