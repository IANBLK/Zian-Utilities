package com.zianblk.zianutilities.neoforge.equipment;

import com.zianblk.zianutilities.neoforge.ZianUtilitiesMod;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Server-only area mining. Every additional block goes through the normal player break path. */
public final class PlaneMining {
    private static final ResourceKey<Enchantment> ENCHANTMENT = ResourceKey.create(
        Registries.ENCHANTMENT, ResourceLocation.fromNamespaceAndPath(ZianUtilitiesMod.MOD_ID, "mineria_3x3"));
    private static final List<Pending> PENDING = new ArrayList<>();
    private static boolean processing;

    private PlaneMining() {}

    public static void install() {
        NeoForge.EVENT_BUS.addListener(PlaneMining::onBreak);
        NeoForge.EVENT_BUS.addListener(PlaneMining::onTick);
    }

    private static void onBreak(BlockEvent.BreakEvent event) {
        if (processing || event.isCanceled() || !(event.getPlayer() instanceof ServerPlayer player)
            || player.isCreative() || player.isSpectator()) return;
        ServerLevel level = player.serverLevel();
        ItemStack tool = player.getMainHandItem();
        if (!tool.is(ItemTags.PICKAXES) || !event.getState().is(BlockTags.MINEABLE_WITH_PICKAXE)
            || event.getState().hasBlockEntity()) return;
        if (!enchanted(player, tool)) return;
        HitResult hit = player.pick(player.blockInteractionRange() + 1.0D, 0.0F, false);
        if (!(hit instanceof BlockHitResult blockHit) || !blockHit.getBlockPos().equals(event.getPos())) return;
        PENDING.add(new Pending(player.getUUID(), level, event.getPos().immutable(), event.getState(),
            blockHit.getDirection(), tool.getItem()));
    }

    private static void onTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) return;
        List<Pending> pending = List.copyOf(PENDING);
        PENDING.clear();
        processing = true;
        try {
            for (Pending job : pending) mine(job, event.getServer().getPlayerList().getPlayer(job.player()));
        } finally {
            processing = false;
        }
    }

    private static void mine(Pending job, ServerPlayer player) {
        if (player == null || player.serverLevel() != job.level() || player.isCreative() || player.isSpectator()
            || player.getMainHandItem().getItem() != job.item()
            || !enchanted(player, player.getMainHandItem())) return;
        ServerLevel level = job.level();
        // If a claim/protection canceled the original break, leave every neighbor alone.
        if (level.getBlockState(job.origin()).equals(job.original())) return;
        float initialHardness = job.original().getDestroySpeed(level, job.origin());
        if (initialHardness < 0) return;
        for (BlockPos pos : plane(job.origin(), job.face())) {
            if (!level.isLoaded(pos) || player.getMainHandItem().isEmpty()) break;
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.hasBlockEntity() || !state.is(BlockTags.MINEABLE_WITH_PICKAXE)
                || !player.hasCorrectToolForDrops(state, level, pos)) continue;
            float hardness = state.getDestroySpeed(level, pos);
            if (hardness < 0 || hardness > initialHardness + 0.001F) continue;
            player.gameMode.destroyBlock(pos);
        }
    }

    private static boolean enchanted(ServerPlayer player, ItemStack stack) {
        Optional<Holder.Reference<Enchantment>> holder = player.registryAccess()
            .registryOrThrow(Registries.ENCHANTMENT).getHolder(ENCHANTMENT);
        return holder.isPresent() && EnchantmentHelper.getItemEnchantmentLevel(holder.get(), stack) > 0;
    }

    public static List<BlockPos> plane(BlockPos center, Direction face) {
        List<BlockPos> positions = new ArrayList<>(8);
        for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) {
            if (a == 0 && b == 0) continue;
            positions.add(switch (face.getAxis()) {
                case X -> center.offset(0, a, b);
                case Y -> center.offset(a, 0, b);
                case Z -> center.offset(a, b, 0);
            });
        }
        return positions;
    }

    private record Pending(UUID player, ServerLevel level, BlockPos origin, BlockState original,
                           Direction face, net.minecraft.world.item.Item item) {}
}
