package com.zianblk.zianutilities.neoforge.breeding;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class BreedingBalanceTest {
 @TempDir Path temp;
 @Test void newDefaultsExposeEightReviewedCurrenciesAndTwelveHours(){var c=new BreedingConfig();c.validate();assertEquals(720,c.baseMinutes);assertEquals(120,c.minimumMinutes);assertEquals(8,c.currencyReductionsMinutes.size());assertEquals(2L,c.currencyReductionsMinutes.get("avecoins:coppercoin"));assertEquals(220L,c.currencyReductionsMinutes.get("avecoins:netheriteticket"));}
 @Test void oldDefaultsMigrateOnlyOnceWithBackup()throws Exception{var file=temp.resolve("breeding.json");String original="{\"baseMinutes\":1440,\"currencyReductionsMinutes\":{\"avecoins:coppercoin\":2,\"avecoins:diamondcoin\":20,\"avecoins:netheriteticket\":220}}";Files.writeString(file,original);var c=BreedingConfig.load(file);assertEquals(720,c.baseMinutes);assertEquals(8,c.currencyReductionsMinutes.size());assertEquals(original,Files.readString(file.resolveSibling("breeding.json.before-12h.json")));assertEquals(720,BreedingConfig.load(file).baseMinutes);}
 @Test void intentionalCustomPolicyIsNotSilentlyReplaced()throws Exception{var file=temp.resolve("custom.json");String original="{\"baseMinutes\":360,\"currencyReductionsMinutes\":{\"avecoins:coppercoin\":7}}";Files.writeString(file,original);var c=BreedingConfig.load(file);assertEquals(360,c.baseMinutes);assertEquals(1,c.currencyReductionsMinutes.size());assertEquals(original,Files.readString(file));}
}
