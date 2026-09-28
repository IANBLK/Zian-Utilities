package com.zianblk.zianutilities.neoforge.gacha;

import com.zianblk.zianutilities.core.economy.EconomyMutationResult;
import com.zianblk.zianutilities.core.economy.EconomyOperation;
import com.zianblk.zianutilities.neoforge.ZianFeatureSettings;
import com.zianblk.zianutilities.neoforge.economy.AvecoinsContractProbe;
import com.zianblk.zianutilities.neoforge.economy.AvecoinsEconomyPort;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Server-owned, journaled first gacha slice. Files are per world and per player. */
public final class GachaRuntime {
    private static final Logger LOG = LoggerFactory.getLogger("ZianUtilities/Gacha");
    public static boolean enabled() { return ZianFeatureSettings.get().gachas(); }
    public static boolean paymentsEnabled() { return ZianFeatureSettings.get().gachaPaymentsActive(); }
    public static final List<String> TICKETS = List.of(
        "avecoins:goldticket", "avecoins:diamondticket", "avecoins:netheriteticket");
    private static final int MAX_POOLS = 8;
    private static final int MAX_PRIZES = 12;
    private static final int MAX_PENDING = 50;

    private GachaRuntime() {}

    public record Prize(ItemStack item, int weight) {
        public Prize { item = item.copy(); }
    }
    public record Pool(int id, String name, String ticket, int cost, boolean enabled, List<Prize> prizes) {
        public Pool { prizes = List.copyOf(prizes); }
    }
    public record Pending(UUID id, ItemStack item, String poolName, String phase) {
        public Pending { item = item.copy(); }
    }
    public record View(List<Pool> pools, List<Pending> pending, boolean admin, boolean paymentEnabled) {}

    private static Path root(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data/zianutilities/gacha_v1");
    }

    private static CompoundTag read(Path path) throws IOException {
        if (!Files.exists(path)) return new CompoundTag();
        return NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
    }

    private static void write(Path path, CompoundTag data) throws IOException {
        Files.createDirectories(path.getParent());
        Path temp = Files.createTempFile(path.getParent(), "gacha-", ".tmp");
        try {
            NbtIo.writeCompressed(data, temp);
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static Path playerPath(ServerPlayer player) {
        return root(player.getServer()).resolve("players").resolve(player.getUUID() + ".nbt");
    }

    private static CompoundTag stackTag(ServerPlayer player, ItemStack stack) {
        var encoded = stack.save(player.registryAccess());
        if (encoded instanceof CompoundTag compound) return compound;
        throw new IllegalArgumentException("Unsupported item stack encoding");
    }

    private static ItemStack stack(ServerPlayer player, CompoundTag tag) {
        return ItemStack.parseOptional(player.registryAccess(), tag);
    }

    private static Pool decodePool(ServerPlayer player, CompoundTag tag) {
        List<Prize> prizes = new ArrayList<>();
        ListTag entries = tag.getList("prizes", 10);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            ItemStack item = stack(player, entry.getCompound("item"));
            int weight = entry.getInt("weight");
            if (!item.isEmpty() && weight > 0) prizes.add(new Prize(item, weight));
        }
        return new Pool(tag.getInt("id"), tag.getString("name"), tag.getString("ticket"),
            tag.getInt("cost"), tag.getBoolean("enabled"), prizes);
    }

    private static CompoundTag encodePool(ServerPlayer player, Pool pool) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", pool.id());
        tag.putString("name", pool.name());
        tag.putString("ticket", pool.ticket());
        tag.putInt("cost", pool.cost());
        tag.putBoolean("enabled", pool.enabled());
        ListTag prizes = new ListTag();
        for (Prize prize : pool.prizes()) {
            CompoundTag entry = new CompoundTag();
            entry.put("item", stackTag(player, prize.item()));
            entry.putInt("weight", prize.weight());
            prizes.add(entry);
        }
        tag.put("prizes", prizes);
        return tag;
    }

    private static List<Pool> pools(ServerPlayer player) throws IOException {
        List<Pool> result = new ArrayList<>();
        ListTag tags = read(root(player.getServer()).resolve("pools.nbt")).getList("pools", 10);
        for (int i = 0; i < tags.size(); i++) result.add(decodePool(player, tags.getCompound(i)));
        return result;
    }

    private static void savePools(ServerPlayer player, List<Pool> pools) throws IOException {
        CompoundTag data = new CompoundTag();
        data.putInt("schema", 1);
        ListTag tags = new ListTag();
        for (Pool pool : pools) tags.add(encodePool(player, pool));
        data.put("pools", tags);
        write(root(player.getServer()).resolve("pools.nbt"), data);
    }

    private static ListTag operations(ServerPlayer player) throws IOException {
        return read(playerPath(player)).getList("operations", 10);
    }

    private static void saveOperations(ServerPlayer player, ListTag operations) throws IOException {
        int terminal = 0;
        for (int i = operations.size() - 1; i >= 0; i--) {
            String phase = operations.getCompound(i).getString("phase");
            if (phase.equals("DELIVERED") || phase.equals("REJECTED")) {
                terminal++;
                if (terminal > 100) operations.remove(i);
            }
        }
        CompoundTag data = new CompoundTag();
        data.putInt("schema", 1);
        data.put("operations", operations);
        write(playerPath(player), data);
    }

    public static synchronized View view(ServerPlayer player) throws IOException {
        List<Pool> visible = pools(player);
        if (!player.hasPermissions(2)) visible.removeIf(pool -> !pool.enabled());
        List<Pending> pending = new ArrayList<>();
        ListTag ops = operations(player);
        for (int i = ops.size() - 1; i >= 0; i--) {
            CompoundTag op = ops.getCompound(i);
            String phase = op.getString("phase");
            if (!phase.equals("READY") && !phase.equals("RECOVERY_REQUIRED")) continue;
            ItemStack prize = stack(player, op.getCompound("prize"));
            if (!prize.isEmpty()) {
                pending.add(new Pending(op.getUUID("id"), prize, op.getString("pool"), phase));
            }
        }
        return new View(visible, pending, player.hasPermissions(2), paymentsEnabled());
    }

    public static synchronized void create(ServerPlayer player) throws IOException {
        requireAdmin(player);
        List<Pool> pools = pools(player);
        if (pools.size() >= MAX_POOLS) throw new IllegalStateException("Máximo de gachas alcanzado");
        int id = pools.stream().mapToInt(Pool::id).max().orElse(0) + 1;
        pools.add(new Pool(id, "Gacha " + id, TICKETS.getFirst(), 1, false, List.of()));
        savePools(player, pools);
    }

    public static synchronized void delete(ServerPlayer player, int id, String expectedName)
        throws IOException {
        requireAdmin(player);
        List<Pool> pools = pools(player);
        Pool selected = pools.stream().filter(pool -> pool.id() == id).findFirst()
            .orElseThrow(() -> new IllegalStateException("Gacha inexistente"));
        if (!selected.name().equals(expectedName))
            throw new IllegalStateException("El gacha cambió; vuelve a abrirlo antes de eliminar");
        pools.removeIf(pool -> pool.id() == id);
        savePools(player, pools);
        // Player prize journals contain the awarded item and remain claimable after pool deletion.
        LOG.info("[ZIAN-AUDIT] action=gacha_delete admin={} pool={} name={}",
            player.getUUID(), id, selected.name());
    }

    public static synchronized void edit(ServerPlayer player, int id, byte action, int index, String text)
        throws IOException {
        requireAdmin(player);
        List<Pool> pools = pools(player);
        int pos = -1;
        for (int i = 0; i < pools.size(); i++) if (pools.get(i).id() == id) pos = i;
        if (pos < 0) throw new IllegalStateException("Gacha inexistente");
        Pool old = pools.get(pos);
        String name = old.name();
        String ticket = old.ticket();
        int cost = old.cost();
        boolean enabled = old.enabled();
        List<Prize> prizes = new ArrayList<>(old.prizes());
        switch (action) {
            case 2 -> {
                String clean = text.trim();
                if (clean.isEmpty() || clean.length() > 32) throw new IllegalStateException("Nombre inválido");
                name = clean;
            }
            case 3 -> ticket = TICKETS.get((TICKETS.indexOf(ticket) + 1) % TICKETS.size());
            case 4 -> cost = Math.max(1, Math.min(64, cost + index));
            case 5 -> {
                ItemStack held = player.getMainHandItem();
                if (held.isEmpty() || prizes.size() >= MAX_PRIZES
                    || stackTag(player, held).toString().length() > 4096)
                    throw new IllegalStateException("Sostén un objeto válido de hasta 4 KB");
                prizes.add(new Prize(held.copy(), 1));
            }
            case 6 -> {
                if (index < 0 || index >= prizes.size()) throw new IllegalStateException("Premio inexistente");
                int delta = Integer.parseInt(text);
                Prize prize = prizes.get(index);
                prizes.set(index, new Prize(prize.item(), Math.max(1, Math.min(10000, prize.weight() + delta))));
            }
            case 7 -> {
                if (index < 0 || index >= prizes.size()) throw new IllegalStateException("Premio inexistente");
                prizes.remove(index);
                enabled = false;
            }
            case 8 -> {
                if (!enabled && (prizes.isEmpty() || !TICKETS.contains(ticket) || cost < 1))
                    throw new IllegalStateException("Añade al menos un premio y configura el ticket");
                enabled = !enabled;
            }
            default -> throw new IllegalStateException("Acción inválida");
        }
        // Changes to a published pool take effect only after explicitly republishing it.
        if (action != 8) enabled = false;
        pools.set(pos, new Pool(id, name, ticket, cost, enabled, prizes));
        savePools(player, pools);
        LOG.info("[ZIAN-AUDIT] action=gacha_edit admin={} pool={} kind={}", player.getUUID(), id, action);
    }

    private static void requireAdmin(ServerPlayer player) {
        if (!player.hasPermissions(2)) throw new IllegalStateException("Se requiere permiso de administrador");
    }

    public static synchronized void roll(ServerPlayer player, int poolId) throws IOException {
        if (!paymentsEnabled()) throw new IllegalStateException("Los pagos de gacha están desactivados");
        if (!AvecoinsContractProbe.inspect().compatible()) throw new IllegalStateException("AVECOINS compatible no disponible");
        Pool pool = pools(player).stream().filter(p -> p.id() == poolId && p.enabled()).findFirst()
            .orElseThrow(() -> new IllegalStateException("Gacha no disponible"));
        ListTag ops = operations(player);
        int waiting = 0;
        for (int i = 0; i < ops.size(); i++) {
            String phase = ops.getCompound(i).getString("phase");
            if (phase.equals("PREPARED") || phase.equals("DEBIT_PENDING")
                || phase.equals("DELIVERING") || phase.equals("RECOVERY_REQUIRED"))
                throw new IllegalStateException("Tienes una tirada pendiente de revisión");
            if (phase.equals("READY")) waiting++;
        }
        if (waiting >= MAX_PENDING) throw new IllegalStateException("Reclama tus premios pendientes");
        if (pool.prizes().isEmpty()) throw new IllegalStateException("Gacha sin premios");
        Prize winner = pool.prizes().get(GachaDraw.choose(
            pool.prizes().stream().map(Prize::weight).toList(), ThreadLocalRandom.current()));
        UUID id = UUID.randomUUID();
        CompoundTag op = new CompoundTag();
        op.putUUID("id", id);
        op.putString("player", player.getUUID().toString());
        op.putString("pool", pool.name());
        op.putString("ticket", pool.ticket());
        op.putInt("cost", pool.cost());
        op.put("prize", stackTag(player, winner.item()));
        op.putLong("created", System.currentTimeMillis());
        op.putString("phase", "PREPARED");
        ops.add(op);
        saveOperations(player, ops);
        op.putString("phase", "DEBIT_PENDING");
        saveOperations(player, ops);
        EconomyMutationResult debit = new AvecoinsEconomyPort().debit(
            new EconomyOperation(id, player.getUUID(), pool.ticket(), pool.cost()));
        if (debit instanceof EconomyMutationResult.Rejected rejected) {
            op.putString("phase", "REJECTED");
            op.putString("reason", rejected.getReason());
            saveOperations(player, ops);
            throw new IllegalStateException(rejected.getReason().equals("insufficient_funds")
                ? "No tienes tickets suficientes" : "No se pudo usar ese ticket");
        }
        if (debit instanceof EconomyMutationResult.Uncertain uncertain) {
            op.putString("phase", "RECOVERY_REQUIRED");
            op.putString("reason", uncertain.getReason());
            saveOperations(player, ops);
            throw new IllegalStateException("Cobro incierto; un admin debe revisar la tirada " + id);
        }
        op.putString("phase", "READY");
        saveOperations(player, ops);
        LOG.info("[ZIAN-AUDIT] action=gacha_roll playerUuid={} operation={} pool={} ticket={} cost={} result=READY",
            player.getUUID(), id, pool.id(), pool.ticket(), pool.cost());
        if (fits(player, winner.item())) {
            claim(player, id);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "Ganaste " + winner.item().getHoverName().getString() + "."));
        } else {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "Ganaste " + winner.item().getHoverName().getString()
                    + ". Libera espacio y reclámalo en Gachas."));
        }
    }

    public static synchronized void claim(ServerPlayer player, UUID id) throws IOException {
        ListTag ops = operations(player);
        CompoundTag op = null;
        for (int i = 0; i < ops.size(); i++) {
            if (ops.getCompound(i).getUUID("id").equals(id)) op = ops.getCompound(i);
        }
        if (op == null || !op.getString("phase").equals("READY"))
            throw new IllegalStateException("Premio no disponible");
        ItemStack prize = stack(player, op.getCompound("prize"));
        if (prize.isEmpty()) throw new IllegalStateException("Premio inválido");
        if (!fits(player, prize)) throw new IllegalStateException("Libera espacio en tu inventario");
        op.putString("phase", "DELIVERING");
        saveOperations(player, ops);
        ItemStack copy = prize.copy();
        player.getInventory().add(copy);
        if (!copy.isEmpty()) {
            op.putString("phase", "RECOVERY_REQUIRED");
            saveOperations(player, ops);
            throw new IllegalStateException("Entrega incierta; un admin debe revisar el premio");
        }
        op.putString("phase", "DELIVERED");
        saveOperations(player, ops);
        LOG.info("[ZIAN-AUDIT] action=gacha_claim playerUuid={} operation={} result=DELIVERED",
            player.getUUID(), id);
    }

    private static boolean fits(ServerPlayer player, ItemStack prize) {
        int capacity = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack slot = player.getInventory().getItem(i);
            if (slot.isEmpty()) capacity += prize.getMaxStackSize();
            else if (ItemStack.isSameItemSameComponents(slot, prize))
                capacity += Math.max(0, Math.min(slot.getMaxStackSize(), player.getInventory().getMaxStackSize()) - slot.getCount());
            if (capacity >= prize.getCount()) return true;
        }
        return false;
    }
}
