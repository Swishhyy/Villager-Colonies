package io.github.swishhyy.wwmc.test;

import com.mojang.authlib.GameProfile;
import io.github.swishhyy.wwmc.menu.MapData;
import io.github.swishhyy.wwmc.settlement.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.GameTest;

public final class SettlementMapWorldTests {
    private static Settlement town(UUID owner,int x) {
        return new Settlement(UUID.randomUUID(),owner,"Map town "+x,new BlockPos(x,89,1_250_000),32,List.of(),List.of(),"balanced");
    }
    @GameTest(timeoutTicks=20)
    @EmptyTemplate
    @TestHolder(description="Optional maps receive saved completed town locations without loading terrain, while shared pings and trade routes remain limited to managers and reciprocal allies. Removal checks ownership even without a built-in map menu.")
    static void privateMarkersAndUnloadedTowns(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); var data=SettlementData.get(level); var saved=new ArrayList<>(data.settlements);
            var visitor=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"MapManager"));
            var outsider=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"MapOutsider"));
            Settlement own=town(visitor.getUUID(),1_250_000),ally=town(UUID.randomUUID(),1_251_000),stranger=town(outsider.getUUID(),1_252_000);
            Settlement complete=town(UUID.randomUUID(),1_253_000),pending=town(UUID.randomUUID(),1_254_000);
            complete.trading.npc=true; complete.trading.buildIndex=-1; pending.trading.npc=true; pending.trading.buildIndex=128;
            own.campaign.allies.add(ally.id); ally.campaign.allies.add(own.id); own.trading.partner=ally.id;
            ally.trading.partner=own.id; stranger.trading.partner=complete.id;
            for(var town:List.of(own,ally,stranger)) town.progress.ping(new TownProgress.Ping(UUID.randomUUID(),town.owner,"Manager",town.center,"meet","Private meeting",0));
            try {
                data.settlements.clear(); data.settlements.addAll(List.of(own,ally,stranger,complete,pending));
                var map=SettlementMap.build(level,visitor);
                helper.assertTrue(map.dimension().equals(level.dimension().identifier().toString()),"Wrong dimension in map snapshot");
                helper.assertTrue(map.towns().size()==4 && map.towns().stream().noneMatch(t -> t.id().equals(pending.id.toString())),"Map included an unfinished town");
                helper.assertTrue(map.towns().stream().anyMatch(t -> t.id().equals(complete.id.toString()) && t.y()==89 && t.relation()==MapData.NEUTRAL),"Unloaded NPC marker lost its identity or elevation");
                helper.assertTrue(map.pings().size()==2 && map.routes().size()==1,"Shared map exposed a stranger's private markers or routes");
                helper.assertTrue(!level.hasChunkAt(complete.center),"Map loaded a town's terrain");
                UUID ownPing=own.progress.pings.getFirst().id();
                SettlementMap.remove(outsider,ownPing.toString());
                helper.assertTrue(own.progress.pings.size()==1,"Unrelated player removed a private ping");
                SettlementMap.remove(visitor,ownPing.toString());
                helper.assertTrue(own.progress.pings.isEmpty(),"Town manager could not remove a shared ping");
                own.campaign.allies.remove(ally.id);
                helper.assertTrue(SettlementMap.build(level,visitor).pings().isEmpty(),"A one-sided alliance still exposed pings");
            } finally { data.settlements.clear(); data.settlements.addAll(saved); data.setDirty(); }
            helper.succeed();
        });
    }
}
