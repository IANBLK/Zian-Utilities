package com.zianblk.zianutilities.neoforge.raids;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Modifier;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;

/** Compatibility control against the real Raid Dens jar (0.14.3): every member the raids module calls must exist. */
class RaidDensApiContractTest {
    private static final String P = "com.necro.raid.dens.common.";

    private static final java.net.URLClassLoader LOADER = loader();

    /** Raid Dens is read from its jar in a side loader so FML never tries to load it as a mod in the test JVM. */
    private static java.net.URLClassLoader loader() {
        try {
            return new java.net.URLClassLoader(new java.net.URL[] {java.nio.file.Path.of(System.getProperty("zian.raiddens.jar")).toUri().toURL()}, RaidDensApiContractTest.class.getClassLoader());
        } catch (java.net.MalformedURLException bad) {
            throw new IllegalStateException(bad);
        }
    }

    private static Class<?> cls(String name) throws Exception { return Class.forName(P + name, false, LOADER); }

    private static void method(String type, String name, Class<?>... params) throws Exception {
        var m = cls(type).getMethod(name, params);
        assertNotNull(m);
    }

    @Test void entryPointAndRaidCloseApi() throws Exception {
        var packet = cls("network.packets.RaidChallengePacket");
        var handle = packet.getDeclaredMethod("handleServer", ServerPlayer.class);
        org.junit.jupiter.api.Assertions.assertEquals(void.class, handle.getReturnType());
        method("network.packets.RaidChallengePacket", "targetedEntityId");
        method("raids.RaidInstance", "queueStopInstance", boolean.class);
        method("raids.RaidInstance", "stopInstance", boolean.class);
        method("raids.RaidInstance", "getMaxHealth");
        method("raids.RaidInstance", "getBattles");
        method("raids.RaidInstance", "removeFromBossEvent", ServerPlayer.class);
        method("raids.RaidSession", "getDamageRecord", ServerPlayer.class);
        method("raids.RaidSession", "addPlayer", ServerPlayer.class);
        method("raids.RaidSession", "isPlayerIn", ServerPlayer.class);
        method("raids.RaidSession", "hasFailed", ServerPlayer.class);
        method("raids.RaidSession", "getActive");
        method("raids.RaidSession", "getRaid");
        method("raids.RaidSession", "boss");
        method("raids.RaidSession$DamageRecord", "addDamage", float.class);
        method("raids.RaidSession$DamageRecord", "ratio");
        method("data.raid.RaidBoss", "getRequiredDamage");
        method("data.raid.RaidBoss", "getTier");
        method("data.raid.RaidBoss", "getBossProperties");
        method("util.IRaidAccessor", "crd_isRaidBoss");
        method("util.IRaidAccessor", "crd_getRaidId");
        method("util.IRaidAccessor", "crd_getRaidState");
        method("raids.helpers.RaidJoinHelper", "isParticipating", net.minecraft.world.entity.player.Player.class, boolean.class);
        method("util.RaidUtils", "isRaidDimension", net.minecraft.world.level.Level.class);
        for (String field : new String[] {"ACTIVE_RAIDS"})
            org.junit.jupiter.api.Assertions.assertTrue(Modifier.isStatic(cls("raids.helpers.RaidHelper").getField(field).getModifiers()));
    }

    @Test void eventsAndRegistry() throws Exception {
        var events = cls("events.RaidEvents");
        for (String field : new String[] {"SET_RAID", "RAID_JOIN", "REWARD_POKEMON"}) assertNotNull(events.getField(field));
        method("events.SetRaidEvent", "setRaid", cls("data.raid.RaidBoss"));
        method("events.RewardPokemonEvent", "getPokemon");
        method("events.RaidJoinEvent", "getRaid");
        assertNotNull(cls("registry.CustomRaidRegistries").getField("RAID_REGISTRY"));
        method("registry.custom.RaidRegistry", "getCache");
    }
}
