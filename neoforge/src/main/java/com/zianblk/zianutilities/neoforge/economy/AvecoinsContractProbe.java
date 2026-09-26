package com.zianblk.zianutilities.neoforge.economy;

import net.neoforged.fml.ModList;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Read-only compatibility check; it never reads or changes a player wallet. */
public final class AvecoinsContractProbe {
    private AvecoinsContractProbe() {
    }

    public static ProbeResult inspect() {
        try {
            var mod = ModList.get().getModContainerById("avecoins");
            if (mod.isEmpty()) {
                return new ProbeResult(false, "AVECOINS no está instalado", List.of());
            }
            String version = mod.get().getModInfo().getVersion().toString();
            if (!"2.3".equals(version)) {
                return new ProbeResult(false, "AVECOINS " + version + "; se requiere 2.3", List.of());
            }

            Class<?> crafting = Class.forName("net.sundggs.avecoins.config.CraftingConfig");
            Class<?> store = Class.forName("net.sundggs.avecoins.shop.WalletStore");
            Class<?> data = Class.forName("net.sundggs.avecoins.shop.WalletData");
            return inspectLayout(crafting, store, data);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            return new ProbeResult(
                false,
                "contrato AVECOINS incompatible: " + error.getClass().getSimpleName(),
                List.of()
            );
        }
    }

    static ProbeResult inspectLayout(Class<?> crafting, Class<?> store, Class<?> data)
        throws ReflectiveOperationException {
            Field managedField = crafting.getField("MANAGED_RESULTS");
            Object managedValue = managedField.get(null);
            if (!(managedValue instanceof Set<?> managed)
                || managed.stream().anyMatch(value -> !(value instanceof String))) {
                return new ProbeResult(false, "MANAGED_RESULTS incompatible", List.of());
            }
            List<String> currencies = managed.stream().map(String.class::cast).sorted().toList();

            store.getMethod("get");
            store.getMethod("save", data);
            data.getMethod("copy");
            data.getMethod("balance", UUID.class, String.class);
            data.getMethod("balances", UUID.class);
            data.getMethod("credit", UUID.class, String.class, long.class);
            data.getMethod("debit", UUID.class, String.class, long.class);
            int slots = data.getField("SLOT_COUNT").getInt(null);
            int stackSize = data.getField("STACK_SIZE").getInt(null);
            if (slots != 27 || stackSize != 64) {
                return new ProbeResult(false, "estructura de cartera incompatible", List.of());
            }
            return new ProbeResult(true, "AVECOINS 2.3 compatible", currencies);
    }

    public record ProbeResult(boolean compatible, String detail, List<String> currencies) {
        public ProbeResult {
            currencies = List.copyOf(currencies);
        }
    }
}

