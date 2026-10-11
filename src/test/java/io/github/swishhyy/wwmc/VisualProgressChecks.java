package io.github.swishhyy.wwmc;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.swishhyy.wwmc.settlement.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.DyeColor;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;

public final class VisualProgressChecks {
    @Test void oldSavesAndTownProgressRemainCompatible() {
        Settlement town=new Settlement(UUID.randomUUID(),UUID.randomUUID(),"Bluewater",new BlockPos(0,120,0),16,List.of(),List.of(),"balanced");
        DyeColor original=TownBorders.color(town);
        var old=Settlement.CODEC.encodeStart(JsonOps.INSTANCE,town).getOrThrow().getAsJsonObject();
        old.remove("progress");
        assertEquals(original,TownBorders.color(Settlement.CODEC.parse(JsonOps.INSTANCE,old).getOrThrow()));
        town.progress.color=DyeColor.CYAN.getId(); town.progress.milestones.add("enchanting"); town.progress.research.add("steel_tools");
        var saved=Settlement.CODEC.encodeStart(JsonOps.INSTANCE,town).getOrThrow();
        Settlement restored=Settlement.CODEC.parse(JsonOps.INSTANCE,saved).getOrThrow();
        assertEquals(DyeColor.CYAN,TownBorders.color(restored));
        assertEquals(Set.of("enchanting"),restored.progress.milestones);
        assertEquals(Set.of("steel_tools"),restored.progress.research);
    }
    @Test @ExtendWith(EphemeralTestServerProvider.class)
    void tutorialAdvancementsLoadWithMinecraftCodec(MinecraftServer server) throws Exception {
        var ops=server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        List<String> ids=List.of("root","read_guide","found_town","housing","warehouse","recruit","first_job","courier","harvest","meals","butchering","timber","mining","smelting","crafting","repair","enchanting","recovery","defense","friends","shipment","project","schematic","research");
        for(String id:ids) try(var in=getClass().getResourceAsStream("/data/wwmc/advancement/tutorial/"+id+".json")) {
            assertNotNull(in,id);
            Advancement advancement=Advancement.CODEC.parse(ops,JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8))).getOrThrow();
            assertTrue(advancement.criteria().containsKey("done"));
        }
    }
}
