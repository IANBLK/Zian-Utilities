package com.zianblk.zianutilities.neoforge.gacha;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class GachaJournalReadTest {
    @TempDir Path directory;

    private CompoundTag read(Path path) throws Exception {
        var method = GachaRuntime.class.getDeclaredMethod("read", Path.class);
        method.setAccessible(true);
        return (CompoundTag) method.invoke(null, path);
    }

    @Test void normalCompressedJournalSurvivesRead() throws Exception {
        Path path = directory.resolve("journal.nbt");
        CompoundTag journal = new CompoundTag();
        journal.putString("phase", "DEBIT_PENDING");
        NbtIo.writeCompressed(journal, path);
        assertEquals("DEBIT_PENDING", read(path).getString("phase"));
    }

    @Test void compressedExpansionBeyondQuotaIsRejected() throws Exception {
        Path path = directory.resolve("oversized.nbt");
        CompoundTag journal = new CompoundTag();
        journal.putByteArray("data", new byte[9 * 1024 * 1024]);
        NbtIo.writeCompressed(journal, path);
        InvocationTargetException error = assertThrows(InvocationTargetException.class, () -> read(path));
        assertEquals("NbtAccounterException", error.getCause().getClass().getSimpleName());
    }

    @Test void sqlitePoolsAndUncertainPrizeJournalSurviveWithoutLegacyFiles() throws Exception {
        Path world=directory.resolve("world");Path root=world.resolve("data/zianutilities/gacha_v1");java.nio.file.Files.createDirectories(root);
        Path pools=root.resolve("pools.nbt");CompoundTag tag=new CompoundTag();tag.putString("pool","existing-five-prizes");tag.putString("phase","DEBIT_PENDING");NbtIo.writeCompressed(tag,pools);
        com.zianblk.zianutilities.core.storage.StorageMaintenance.migrateOffline(world);
        java.nio.file.Files.move(world.resolve("data/zianutilities"),world.resolve("legacy"));
        try {
            assertEquals(tag,read(pools));
            var method=GachaRuntime.class.getDeclaredMethod("write",Path.class,CompoundTag.class);method.setAccessible(true);
            Path journal=root.resolve("players/"+java.util.UUID.randomUUID()+".nbt");method.invoke(null,journal,tag);
            com.zianblk.zianutilities.core.storage.StorageFiles.closeAll();
            assertEquals(tag,read(journal));assertFalse(java.nio.file.Files.exists(journal));
        } finally {com.zianblk.zianutilities.core.storage.StorageFiles.closeAll();}
    }
}
