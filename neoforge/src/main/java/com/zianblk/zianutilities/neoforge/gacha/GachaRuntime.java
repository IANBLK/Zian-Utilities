package com.zianblk.zianutilities.neoforge.gacha;
import com.zianblk.zianutilities.neoforge.LocalizedException;
import com.zianblk.zianutilities.neoforge.Tr;
import com.zianblk.zianutilities.core.config.ConfigHistory;
import com.zianblk.zianutilities.core.storage.StorageFiles;

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

import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Server-owned, journaled first gacha slice. Files are per world and per player. */
public final class GachaRuntime {
    // Las tiradas reparten premios: no deben ser predecibles (mismo criterio que crianza).
    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();
    private static final Logger LOG = LoggerFactory.getLogger("ZianUtilities/Gacha");
    public static boolean enabled() { return ZianFeatureSettings.get().gachas(); }
    public static boolean paymentsEnabled() { return ZianFeatureSettings.get().gachaPaymentsActive(); }
    public static final List<String> TICKETS = List.of(
        "avecoins:goldticket", "avecoins:diamondticket", "avecoins:netheriteticket");
    private static final int MAX_POOLS = 8;
    private static final int MAX_PRIZES = 12;
    private static final int MAX_PENDING = 50;
    private static final long MAX_JOURNAL_BYTES = 8L * 1024 * 1024;

    private GachaRuntime() {}

    public static void installDeliveryScheduler() { GachaDeliveryScheduler.install(); }

    public record Prize(ItemStack item, int weight) {
        public Prize { item = item.copy(); }
    }
    public record Pool(int id, String name, String ticket, int cost, boolean enabled, List<Prize> prizes, ItemStack icon, String description) {
        public Pool(int id,String name,String ticket,int cost,boolean enabled,List<Prize> prizes){this(id,name,ticket,cost,enabled,prizes,ItemStack.EMPTY,"");}
        public Pool { prizes = List.copyOf(prizes);icon=icon.copyWithCount(1);if(description.length()>160)throw new LocalizedException.Argument("zianutilities.gacha.err.desc_too_long", "Descripción demasiado larga"); }
    }
    public record Pending(UUID id, ItemStack item, String poolName, String phase) {
        public Pending { item = item.copy(); }
    }
    public record Review(UUID id, String phase, String reason, String poolName, String ticket, int cost,
                         ItemStack prize) {
        public Review { prize = prize.copy(); }
    }
    public record View(List<Pool> pools, List<Pending> pending, boolean admin, boolean paymentEnabled) {}

    private static Path root(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data/zianutilities/gacha_v1");
    }

    private static CompoundTag read(Path path) throws IOException {
        if (!StorageFiles.exists(path)) return new CompoundTag();
        try(var input=StorageFiles.newInputStream(path)){return NbtIo.readCompressed(input, NbtAccounter.create(MAX_JOURNAL_BYTES));}
    }

    private static void write(Path path, CompoundTag data) throws IOException {
        StorageFiles.createDirectories(path.getParent());
        Path temp = StorageFiles.createTempFile(path.getParent(), "gacha-", ".tmp");
        try {
            try(var output=StorageFiles.newOutputStream(temp)){NbtIo.writeCompressed(data, output);}
            try (FileChannel channel = StorageFiles.channel(temp, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            StorageFiles.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            StorageFiles.deleteIfExists(temp);
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
            tag.getInt("cost"), tag.getBoolean("enabled"), prizes,stack(player,tag.getCompound("icon")),tag.getString("description"));
    }

    private static CompoundTag encodePool(ServerPlayer player, Pool pool) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", pool.id());
        tag.putString("name", pool.name());
        tag.putString("ticket", pool.ticket());
        tag.putInt("cost", pool.cost());
        tag.putBoolean("enabled", pool.enabled());
        if(!pool.icon().isEmpty())tag.put("icon",stackTag(player,pool.icon()));
        tag.putString("description",pool.description());
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

    private static ConfigHistory history(MinecraftServer server) {
        return new ConfigHistory(root(server).resolve("pools.nbt"), root(server).resolve("config-backups"),
            root(server).resolve("changes.jsonl"), 10, Clock.systemUTC());
    }

    private static void savePools(ServerPlayer player, List<Pool> pools, String summary) throws IOException {
        CompoundTag data = new CompoundTag();
        data.putInt("schema", 1);
        ListTag tags = new ListTag();
        for (Pool pool : pools) tags.add(encodePool(player, pool));
        data.put("pools", tags);
        ConfigHistory history = history(player.getServer());
        Path backup = history.backup();
        try {
            write(root(player.getServer()).resolve("pools.nbt"), data);
        } catch (IOException | RuntimeException e) {
            history.discard(backup);
            throw e;
        }
        record(history, player, summary);
    }

    private static void record(ConfigHistory history, ServerPlayer player, String summary) {
        try {
            history.record(player.getUUID(), player.getGameProfile().getName(), "gacha", summary);
        } catch (IOException | RuntimeException e) {
            LOG.warn("[ZIAN-AUDIT] No se pudo escribir changes.jsonl", e);
        }
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
        if (!com.zianblk.zianutilities.neoforge.UtilitiesPermissions.allows(player,"admin.gacha",true)) visible.removeIf(pool -> !pool.enabled());
        List<Pending> pending = new ArrayList<>();
        ListTag ops = operations(player);
        for (int i = ops.size() - 1; i >= 0; i--) {
            CompoundTag op = ops.getCompound(i);
            String phase = op.getString("phase");
            if (phase.equals("READY") && System.currentTimeMillis() < op.getLong("claimAfter")) continue;
            if (!phase.equals("READY") && !GachaRecovery.isBlocking(phase)) continue;
            ItemStack prize = stack(player, op.getCompound("prize"));
            if (!prize.isEmpty()) {
                pending.add(new Pending(op.getUUID("id"), prize, op.getString("pool"), phase));
            }
        }
        return new View(visible, pending, com.zianblk.zianutilities.neoforge.UtilitiesPermissions.allows(player,"admin.gacha",true), paymentsEnabled());
    }

    public static synchronized void create(ServerPlayer player) throws IOException {
        requireAdmin(player);
        List<Pool> pools = pools(player);
        if (pools.size() >= MAX_POOLS) throw new LocalizedException.State("zianutilities.gacha.err.max_pools", "Máximo de gachas alcanzado");
        int id = pools.stream().mapToInt(Pool::id).max().orElse(0) + 1;
        pools.add(new Pool(id, "Gacha " + id, TICKETS.getFirst(), 1, false, List.of()));
        savePools(player, pools, "crear gacha " + id);
    }

    public static synchronized void delete(ServerPlayer player, int id, String expectedName)
        throws IOException {
        requireAdmin(player);
        List<Pool> pools = pools(player);
        Pool selected = pools.stream().filter(pool -> pool.id() == id).findFirst()
            .orElseThrow(() -> new LocalizedException.State("zianutilities.gacha.err.no_pool", "Gacha inexistente"));
        if (!selected.name().equals(expectedName))
            throw new LocalizedException.State("zianutilities.gacha.err.pool_changed", "El gacha cambió; vuelve a abrirlo antes de eliminar");
        pools.removeIf(pool -> pool.id() == id);
        savePools(player, pools, "eliminar gacha " + id + " " + selected.name());
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
        if (pos < 0) throw new LocalizedException.State("zianutilities.gacha.err.no_pool", "Gacha inexistente");
        Pool old = pools.get(pos);
        String name = old.name();
        String ticket = old.ticket();
        int cost = old.cost();
        boolean enabled = old.enabled();
        ItemStack icon=old.icon();String description=old.description();
        List<Prize> prizes = new ArrayList<>(old.prizes());
        switch (action) {
            case 2 -> {
                String clean = text.trim();
                if (clean.isEmpty() || clean.length() > 32) throw new LocalizedException.State("zianutilities.gacha.err.invalid_name", "Nombre inválido");
                name = clean;
            }
            case 3 -> ticket = TICKETS.get((TICKETS.indexOf(ticket) + 1) % TICKETS.size());
            case 4 -> cost = Math.max(1, Math.min(64, cost + index));
            case 5 -> {
                ItemStack held = player.getMainHandItem();
                if (held.isEmpty() || prizes.size() >= MAX_PRIZES
                    || stackTag(player, held).toString().length() > 4096)
                    throw new LocalizedException.State("zianutilities.gacha.err.hold_valid_item", "Sostén un objeto válido de hasta 4 KB");
                prizes.add(new Prize(held.copy(), 1));
            }
            case 6 -> {
                if (index < 0 || index >= prizes.size()) throw new LocalizedException.State("zianutilities.gacha.err.no_prize", "Premio inexistente");
                int delta = Integer.parseInt(text);
                Prize prize = prizes.get(index);
                prizes.set(index, new Prize(prize.item(), Math.max(1, Math.min(10000, prize.weight() + delta))));
            }
            case 7 -> {
                if (index < 0 || index >= prizes.size()) throw new LocalizedException.State("zianutilities.gacha.err.no_prize", "Premio inexistente");
                prizes.remove(index);
                enabled = false;
            }
            case 8 -> {
                if (!enabled && (prizes.isEmpty() || !TICKETS.contains(ticket) || cost < 1))
                    throw new LocalizedException.State("zianutilities.gacha.err.need_prize_ticket", "Añade al menos un premio y configura el ticket");
                enabled = !enabled;
            }
            case 12 -> {
                ItemStack held=player.getMainHandItem();
                if(held.isEmpty()||stackTag(player,held).toString().length()>4096)throw new LocalizedException.State("zianutilities.gacha.err.hold_icon", "Sostén el icono que quieras usar (máximo 4 KB)");
                icon=held.copyWithCount(1);
            }
            case 13 -> {if(text.length()>160||text.codePoints().anyMatch(Character::isISOControl))throw new LocalizedException.State("zianutilities.gacha.err.invalid_desc", "Descripción inválida (máximo 160 caracteres)");description=text.trim();}
            default -> throw new LocalizedException.State("zianutilities.gacha.err.invalid_action", "Acción inválida");
        }
        // Changes to a published pool take effect only after explicitly republishing it.
        if (action != 8 && action != 12 && action != 13) enabled = false;
        pools.set(pos, new Pool(id, name, ticket, cost, enabled, prizes,icon,description));
        savePools(player, pools, "editar gacha " + id + " acción " + action);
        LOG.info("[ZIAN-AUDIT] action=gacha_edit admin={} pool={} kind={}", player.getUUID(), id, action);
    }

    /** Restores pools.nbt to its previous state, including each pool's enabled flag at that time. */
    public static synchronized void undo(ServerPlayer player) throws IOException {
        requireAdmin(player);
        ConfigHistory history = history(player.getServer());
        if (!history.undoLast()) throw new LocalizedException.State("zianutilities.gacha.err.nothing_to_undo", "No hay cambios que deshacer");
        record(history, player, "deshacer último cambio");
        LOG.info("[ZIAN-AUDIT] action=gacha_undo admin={}", player.getUUID());
    }

    private static void requireAdmin(ServerPlayer player) {
        if (!com.zianblk.zianutilities.neoforge.UtilitiesPermissions.allows(player,"admin.gacha",true)) throw new LocalizedException.State("zianutilities.gacha.err.admin_required", "Se requiere permiso de administrador");
    }

    /** Read-only details for an operator to reconcile a stalled roll with the player's inventory. */
    public static synchronized List<Review> review(ServerPlayer player) throws IOException {
        List<Review> result = new ArrayList<>();
        ListTag ops = operations(player);
        for (int i = ops.size() - 1; i >= 0; i--) {
            CompoundTag op = ops.getCompound(i);
            String phase = op.getString("phase");
            if (phase.equals("DELIVERED") || phase.equals("REJECTED")) continue;
            result.add(new Review(op.getUUID("id"), phase, op.getString("reason"), op.getString("pool"),
                op.getString("ticket"), op.getInt("cost"),
                stack(player, op.getCompound("prize"))));
        }
        return result;
    }

    /** Acknowledges delivery only after an operator has verified the prize was received. */
    public static synchronized void confirmDelivered(ServerPlayer player, UUID id, UUID admin)
        throws IOException {
        ListTag ops = operations(player);
        for (int i = 0; i < ops.size(); i++) {
            CompoundTag op = ops.getCompound(i);
            if (!op.getUUID("id").equals(id)) continue;
            if (!GachaRecovery.isDeliveryReview(op.getString("phase"), op.getString("reason")))
                throw new LocalizedException.State("zianutilities.gacha.err.confirm_interrupted", "Solo se puede confirmar una entrega interrumpida");
            op.putString("phase", "DELIVERED");
            op.putString("resolution", "operator_confirmed_delivery");
            saveOperations(player, ops);
            LOG.info("[ZIAN-AUDIT] action=gacha_reconcile admin={} playerUuid={} operation={} result=CONFIRMED_DELIVERED",
                admin, player.getUUID(), id);
            return;
        }
        throw new LocalizedException.State("zianutilities.gacha.err.no_roll", "Tirada inexistente");
    }

    /** The operator verifies the AVECOINS transaction externally; this never calls the wallet. */
    public static synchronized void resolveDebit(ServerPlayer player, UUID id, UUID admin,
                                                 boolean charged, String evidence) throws IOException {
        String note = evidence(evidence);
        ListTag ops = operations(player);
        for (int i = 0; i < ops.size(); i++) {
            CompoundTag op = ops.getCompound(i);
            if (!op.getUUID("id").equals(id)) continue;
            if (!GachaRecovery.isDebitReview(op.getString("phase"), op.getString("reason")))
                throw new LocalizedException.State("zianutilities.gacha.err.phase_no_resolve", "La fase no permite resolver el cobro; revisa el historial");
            op.putString("phase", charged ? "READY" : "REJECTED");
            op.putString("resolution", charged ? "operator_confirmed_charged" : "operator_confirmed_not_charged");
            op.putString("evidence", note);
            saveOperations(player, ops);
            LOG.info("[ZIAN-AUDIT] action=gacha_resolve_debit admin={} playerUuid={} operation={} charged={} evidence={}",
                admin, player.getUUID(), id, charged, note);
            return;
        }
        throw new LocalizedException.State("zianutilities.gacha.err.no_roll", "Tirada inexistente");
    }

    /** Close only after the operator has actually delivered the missing prize or equivalent compensation. */
    public static synchronized void confirmCompensated(ServerPlayer player, UUID id, UUID admin,
                                                       String evidence) throws IOException {
        String note = evidence(evidence);
        ListTag ops = operations(player);
        for (int i = 0; i < ops.size(); i++) {
            CompoundTag op = ops.getCompound(i);
            if (!op.getUUID("id").equals(id)) continue;
            if (!GachaRecovery.isDeliveryReview(op.getString("phase"), op.getString("reason")))
                throw new LocalizedException.State("zianutilities.gacha.err.phase_no_compensation", "La fase no permite confirmar una compensación");
            op.putString("phase", "DELIVERED");
            op.putString("resolution", "operator_confirmed_compensated");
            op.putString("evidence", note);
            saveOperations(player, ops);
            LOG.info("[ZIAN-AUDIT] action=gacha_confirm_compensated admin={} playerUuid={} operation={} evidence={}",
                admin, player.getUUID(), id, note);
            return;
        }
        throw new LocalizedException.State("zianutilities.gacha.err.no_roll", "Tirada inexistente");
    }

    private static String evidence(String text) {
        String note = text.trim().replaceAll("\\p{Cntrl}", " ");
        if (note.isEmpty() || note.length() > 160)
            throw new LocalizedException.State("zianutilities.gacha.err.need_reference", "Escribe una referencia de verificación de hasta 160 caracteres");
        return note;
    }

    public static synchronized ItemStack roll(ServerPlayer player, int poolId) throws IOException {
        if (!paymentsEnabled()) throw new LocalizedException.State("zianutilities.gacha.err.payments_off", "Los pagos de gacha están desactivados");
        if (!AvecoinsContractProbe.inspect().compatible()) throw new LocalizedException.State("zianutilities.gacha.err.avecoins_unavailable", "AVECOINS compatible no disponible");
        Pool pool = pools(player).stream().filter(p -> p.id() == poolId && p.enabled()).findFirst()
            .orElseThrow(() -> new LocalizedException.State("zianutilities.gacha.err.pool_unavailable", "Gacha no disponible"));
        ListTag ops = operations(player);
        int waiting = 0;
        boolean rejectedPrepared = false;
        for (int i = 0; i < ops.size(); i++) {
            CompoundTag previous = ops.getCompound(i);
            String phase = previous.getString("phase");
            // PREPARED is persisted before DEBIT_PENDING and before the economy call.
            // It therefore cannot have charged a ticket and may safely be retired.
            if (phase.equals("PREPARED")) {
                previous.putString("phase", "REJECTED");
                previous.putString("reason", "interrupted_before_debit");
                rejectedPrepared = true;
                continue;
            }
            if (GachaRecovery.isBlocking(phase))
                throw new LocalizedException.State("zianutilities.gacha.err.roll_pending_review", "Tirada %s pendiente de revisión (%s)", previous.getUUID("id"), phase);
            if (phase.equals("READY")) {
                if (System.currentTimeMillis() < previous.getLong("claimAfter"))
                    throw new LocalizedException.State("zianutilities.gacha.err.wait_previous", "Espera a que termine la tirada anterior");
                waiting++;
            }
        }
        if (rejectedPrepared) saveOperations(player, ops);
        if (waiting >= MAX_PENDING) throw new LocalizedException.State("zianutilities.gacha.err.claim_pending", "Reclama tus premios pendientes");
        if (pool.prizes().isEmpty()) throw new LocalizedException.State("zianutilities.gacha.err.no_prizes", "Gacha sin premios");
        Prize winner = pool.prizes().get(GachaDraw.choose(
            pool.prizes().stream().map(Prize::weight).toList(), RANDOM));
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
            throw rejected.getReason().equals("insufficient_funds")
                ? new LocalizedException.State("zianutilities.gacha.err.not_enough_tickets", "No tienes tickets suficientes")
                : new LocalizedException.State("zianutilities.gacha.err.ticket_failed", "No se pudo usar ese ticket");
        }
        if (debit instanceof EconomyMutationResult.Uncertain uncertain) {
            op.putString("phase", "DEBIT_UNCERTAIN");
            op.putString("reason", uncertain.getReason());
            saveOperations(player, ops);
            throw new LocalizedException.State("zianutilities.gacha.err.roll_uncertain", "Cobro incierto; un admin debe revisar la tirada %s", id);
        }
        op.putString("phase", "READY");
        op.putLong("claimAfter", System.currentTimeMillis() + GachaTiming.DELIVERY_MILLIS);
        saveOperations(player, ops);
        LOG.info("[ZIAN-AUDIT] action=gacha_roll playerUuid={} operation={} pool={} ticket={} cost={} result=READY",
            player.getUUID(), id, pool.id(), pool.ticket(), pool.cost());
        GachaDeliveryScheduler.schedule(player, id);
        return winner.item().copy();
    }

    static synchronized void deliverScheduled(ServerPlayer player, UUID id) throws IOException {
        ListTag ops = operations(player);
        for (int i = 0; i < ops.size(); i++) {
            CompoundTag op = ops.getCompound(i);
            if (!op.getUUID("id").equals(id) || !op.getString("phase").equals("READY")) continue;
            ItemStack prize = stack(player, op.getCompound("prize"));
            if (!fits(player, prize)) {
                player.sendSystemMessage(com.zianblk.zianutilities.neoforge.Messages.format(Tr.c("zianutilities.gacha.msg.won_no_space", "Ganaste %s. Libera espacio y reclámalo en Gachas.", prize.getHoverName())));
                return;
            }
            claim(player, id);
            player.sendSystemMessage(com.zianblk.zianutilities.neoforge.Messages.format(Tr.c("zianutilities.gacha.msg.won", "Ganaste %s.", prize.getHoverName().getString())));
            return;
        }
    }

    public static synchronized void claim(ServerPlayer player, UUID id) throws IOException {
        ListTag ops = operations(player);
        CompoundTag op = null;
        for (int i = 0; i < ops.size(); i++) {
            if (ops.getCompound(i).getUUID("id").equals(id)) op = ops.getCompound(i);
        }
        if (op == null || !op.getString("phase").equals("READY"))
            throw new LocalizedException.State("zianutilities.gacha.err.prize_unavailable", "Premio no disponible");
        if (System.currentTimeMillis() < op.getLong("claimAfter"))
            throw new LocalizedException.State("zianutilities.gacha.err.wait_roll", "Espera a que termine la tirada");
        ItemStack prize = stack(player, op.getCompound("prize"));
        if (prize.isEmpty()) throw new LocalizedException.State("zianutilities.gacha.err.prize_invalid", "Premio inválido");
        if (!fits(player, prize)) throw new LocalizedException.State("zianutilities.gacha.err.free_space", "Libera espacio en tu inventario");
        op.putString("phase", "DELIVERING");
        saveOperations(player, ops);
        ItemStack copy = prize.copy();
        player.getInventory().add(copy);
        if (!copy.isEmpty()) {
            op.putString("phase", "DELIVERY_UNCERTAIN");
            saveOperations(player, ops);
            throw new LocalizedException.State("zianutilities.gacha.err.prize_uncertain", "Entrega incierta; un admin debe revisar el premio %s", id);
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
