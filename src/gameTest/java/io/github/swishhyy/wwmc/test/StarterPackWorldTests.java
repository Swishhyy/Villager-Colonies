package io.github.swishhyy.wwmc.test;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import io.github.swishhyy.wwmc.VC;
import io.github.swishhyy.wwmc.settlement.StarterPack;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.GameTest;

public final class StarterPackWorldTests {
    @GameTest(timeoutTicks=40) @EmptyTemplate
    @TestHolder(description="First login supplies a banner and guide once per player per world; saved receipts prevent reconnect and respawn duplicates, and full inventories keep both items as drops.")
    static void starterPackSurvivesRejoinsAndFullInventory(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); var profile=new GameProfile(UUID.randomUUID(),"NewColonist");
            var player=new FakePlayer(level,profile);
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(player));
            helper.assertTrue(player.getInventory().countItem(VC.BANNER_ITEM.get())==1
                    && player.getInventory().countItem(VC.GUIDE.get())==1,"First login did not give exactly one banner and guide");
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(player));
            helper.assertTrue(player.getInventory().countItem(VC.BANNER_ITEM.get())==1
                    && player.getInventory().countItem(VC.GUIDE.get())==1,"Reconnect duplicated the starter pack");
            var respawned=new FakePlayer(level,profile);
            helper.assertTrue(!StarterPack.give(respawned) && respawned.getInventory().isEmpty(),"A replacement player entity received a second pack");
            var receipts=StarterPack.Receipts.get(level);
            var reloaded=StarterPack.Receipts.CODEC.parse(JsonOps.INSTANCE,
                    StarterPack.Receipts.CODEC.encodeStart(JsonOps.INSTANCE,receipts).getOrThrow()).getOrThrow();
            helper.assertTrue(!reloaded.claim(profile.id()),"Saving and loading forgot the player's receipt");
            helper.assertTrue(new StarterPack.Receipts().claim(profile.id()),"Another world cannot give the same player a pack");
            var full=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"PackedColonist"));
            BlockPos pos=helper.absolutePos(new BlockPos(0,2,0)); full.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
            for(int slot=0;slot<full.getInventory().getContainerSize();slot++) full.getInventory().setItem(slot,new ItemStack(Items.STONE,64));
            helper.assertTrue(StarterPack.give(full),"First pack with a full inventory was rejected");
            var drops=level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(3));
            try {
                helper.assertTrue(drops.stream().filter(e -> e.getItem().is(VC.BANNER_ITEM.get())).mapToInt(e -> e.getItem().getCount()).sum()==1
                        && drops.stream().filter(e -> e.getItem().is(VC.GUIDE.get())).mapToInt(e -> e.getItem().getCount()).sum()==1,
                        "A full inventory lost the starter items");
                helper.assertTrue(!StarterPack.give(full),"Full-inventory delivery duplicated on retry");
            } finally { drops.forEach(ItemEntity::discard); }
            helper.succeed();
        });
    }
}
