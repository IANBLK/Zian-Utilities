package com.zianblk.zianutilities.neoforge.breeding;
import com.zianblk.zianutilities.core.breeding.BreedingJob;
import com.zianblk.zianutilities.core.storage.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SQLiteBreedingTest {
  @TempDir Path world;
  @AfterEach void close()throws Exception{StorageFiles.closeAll();}
  private BreedingJob job(){
    var child=new BreedingJob.Child(1,UUID.randomUUID(),"cobblemon:bulbasaur","normal","bulbasaur gender=female",Collections.nCopies(6,31),"cobblemon:hardy","overgrow","FEMALE","cobblemon:poke_ball",false,false,1);
    return new BreedingJob(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),child,0,86400000,7200000,0,0,BreedingJob.Status.ACTIVE,"",List.of());
  }
  @Test void geneticsUncertainPaymentAndParentLeasesSurviveSQLiteAndCompaction()throws Exception{
    Path path=world.resolve("data/zianutilities/breeding_v1/journal.wal");
    var first=job();var purchase=new BreedingJob.Purchase(UUID.randomUUID(),"diamondcoin",1,60000,60000,BreedingJob.Payment.PENDING);
    var uncertain=first.begin(purchase,1).payment(BreedingJob.Payment.UNCERTAIN,2);var completed=job().phase(BreedingJob.Status.DELIVERED,"confirmed",3);
    try(var store=new BreedingStore(path)){store.put(uncertain);store.put(completed);store.checkpoint();}
    StorageMaintenance.migrateOffline(world);
    Files.move(world.resolve("data/zianutilities"),world.resolve("originals"));
    for(int pass=0;pass<2;pass++){
      try(var store=new BreedingStore(path)){
        assertEquals(uncertain,store.jobs.get(first.id()));assertEquals(completed,store.jobs.get(completed.id()));
        assertTrue(store.purchaseExists(purchase.id()));assertTrue(store.parentBusy(first.first()));
        assertEquals(first.child().uuid(),store.jobs.get(first.id()).child().uuid());
        assertEquals(first.child().ivs(),store.jobs.get(first.id()).child().ivs());
        assertEquals(1,store.pending().size());
      }
      if(pass==0)BreedingStorageMaintenance.compact(path);
      StorageFiles.closeAll();
    }
    assertFalse(Files.exists(path));
  }
}
