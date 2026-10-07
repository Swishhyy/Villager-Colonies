package io.github.swishhyy.wwmc;

import com.mojang.serialization.JsonOps;
import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.core.Upgrades;
import io.github.swishhyy.wwmc.menu.PanelView;
import io.github.swishhyy.wwmc.settlement.*;
import io.netty.buffer.Unpooled;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
public final class TradeChecks {
    private static Settlement town(UUID owner,int x) {
        BlockPos pos=new BlockPos(x,70,0);
        return new Settlement(UUID.randomUUID(),owner,"Town "+x,pos,240,List.of(),List.of(new Station(pos.north(),StructureRole.TRADER)),"balanced");
    }
    @Test void exportsRespectReservesAndPause() {
        var warehouse=new SimpleContainer(4); warehouse.setItem(0,new ItemStack(Items.OAK_LOG,64)); warehouse.setItem(1,new ItemStack(Items.OAK_LOG,64));
        warehouse.setItem(2,new ItemStack(Items.CARROT,64));
        TradeShipment cargo=new TradeShipment();
        var orders=List.of(new TradeSettings.Export("minecraft:oak_log",80,32),new TradeSettings.Export("minecraft:carrot",0,0));
        assertEquals(32,TradeGoods.load(List.of(warehouse),orders,cargo));
        assertEquals(96,InventoryOps.count(List.of(warehouse),s -> s.is(Items.OAK_LOG)));
        assertEquals(16,TradeGoods.load(List.of(warehouse),orders,new TradeShipment()));
        assertEquals(80,InventoryOps.count(List.of(warehouse),s -> s.is(Items.OAK_LOG)));
        assertEquals(64,warehouse.getItem(2).getCount());
    }
    @Test void fullWarehousesKeepCargoAndComponents() {
        var source=new SimpleContainer(1); ItemStack named=new ItemStack(Items.IRON_INGOT,10);
        named.set(DataComponents.CUSTOM_NAME,Component.literal("Town's iron")); source.setItem(0,named);
        var cargo=new TradeShipment();
        assertEquals(10,TradeGoods.load(List.of(source),List.of(new TradeSettings.Export("minecraft:iron_ingot",0,64)),cargo));
        assertTrue(source.isEmpty());
        var full=new SimpleContainer(1); full.setItem(0,new ItemStack(Items.COBBLESTONE,64));
        assertEquals(0,TradeGoods.unload(cargo,List.of(full)));
        assertEquals(10,cargo.getItem(0).getCount());
        var target=new SimpleContainer(1); target.setItem(0,named.copyWithCount(60));
        assertEquals(4,TradeGoods.unload(cargo,List.of(target)));
        assertEquals(6,cargo.getItem(0).getCount());
        assertEquals("Town's iron",cargo.getItem(0).getHoverName().getString());
        target.clearContent(); assertEquals(6,TradeGoods.unload(cargo,List.of(target))); assertTrue(cargo.isEmpty());
    }
    @Test void tradeCargoAndTownPolicySurviveReload(MinecraftServer server) {
        var ops=server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var cargo=new TradeShipment(); cargo.destination=UUID.randomUUID(); cargo.stage="deliver";
        ItemStack special=new ItemStack(Items.DIAMOND_PICKAXE); special.setDamageValue(23); special.set(DataComponents.CUSTOM_NAME,Component.literal("Reserved tool"));
        cargo.setItem(0,special);
        var restored=TradeShipment.CODEC.parse(ops,TradeShipment.CODEC.encodeStart(ops,cargo).getOrThrow()).getOrThrow();
        assertEquals(cargo.destination,restored.destination); assertEquals("deliver",restored.stage); assertTrue(restored.travelling());
        assertTrue(ItemStack.matches(special,restored.getItem(0))); assertThrows(IllegalStateException.class,restored::finish);
        Settlement town=town(UUID.randomUUID(),0); town.trading.partner=cargo.destination; town.trading.runner=UUID.randomUUID(); town.trading.runnerPos=new BlockPos(1500,72,48);
        town.trading.exports.add(new TradeSettings.Export("minecraft:iron_ingot",64,32)); town.trading.npc=true; town.trading.specialty="mining"; town.trading.buildIndex=128;
        var json=Settlement.CODEC.encodeStart(ops,town).getOrThrow();
        Settlement copy=Settlement.CODEC.parse(ops,json).getOrThrow();
        assertEquals(town.trading.partner,copy.trading.partner); assertEquals(town.trading.runnerPos,copy.trading.runnerPos);
        assertEquals(town.trading.exports,copy.trading.exports); assertEquals(128,copy.trading.buildIndex); assertTrue(copy.trading.npc);
        json.getAsJsonObject().remove("trading");
        Settlement old=Settlement.CODEC.parse(ops,json).getOrThrow();
        assertFalse(old.trading.npc); assertNull(old.trading.partner); assertTrue(old.trading.exports.isEmpty()); assertEquals(town.id,old.id);
    }
    @Test void otherOwnersMustAcceptAndNpcAttacksEndTrade() {
        Settlement a=town(UUID.randomUUID(),0),b=town(UUID.randomUUID(),1000),c=town(a.owner,2000);
        List<Settlement> towns=List.of(a,b,c);
        TradeRoutes.link(a,b,towns,8192); assertFalse(TradeRoutes.agreed(a,b)); assertNull(b.trading.partner);
        TradeRoutes.link(b,a,towns,8192); assertTrue(TradeRoutes.agreed(a,b));
        TradeRoutes.link(c,b,towns,8192); assertNull(c.trading.partner,"Cannot steal another player's route");
        TradeRoutes.disconnect(a,towns); assertNull(a.trading.partner); assertNull(b.trading.partner);
        TradeRoutes.link(a,c,towns,8192); assertTrue(TradeRoutes.agreed(a,c));
        TradeRoutes.disconnect(a,towns); b.trading.npc=true;
        TradeRoutes.link(a,b,towns,8192); assertTrue(TradeRoutes.agreed(a,b));
        TradeRoutes.attacked(b,a.owner,towns); assertFalse(TradeRoutes.agreed(a,b)); assertNull(a.trading.partner);
        TradeRoutes.link(a,b,towns,8192); assertNull(a.trading.partner,"Hostile NPC towns refuse a new route");
    }
    @Test void checkpointAndChunkWindowsAreBounded() {
        Settlement a=town(UUID.randomUUID(),0); Station first=TradeRoutes.checkpoint(a);
        assertTrue(TradeRoutes.uniqueCheckpoint(a,first.position())); assertFalse(TradeRoutes.uniqueCheckpoint(a,first.position().east()));
        assertTrue(Upgrades.soloCrew(StructureRole.TRADER)); assertFalse(Upgrades.hires(StructureRole.TRADER)); assertFalse(Upgrades.widens(StructureRole.TRADER));
        assertEquals(9,TradeChunks.window(new BlockPos(-1,70,-1)).size());
        assertEquals(9,TradeChunks.window(new BlockPos(10000,70,10000)).size());
        assertEquals(0,new HashSet<>(TradeChunks.window(BlockPos.ZERO)).stream().filter(TradeChunks.window(new BlockPos(10000,70,10000))::contains).count());
    }
    @Test void npcRegionsAreDeterministicAndBlueprintHasWorkingBeds() {
        var first=NpcTownPlan.candidate(42,0,0,1408); assertEquals(first,NpcTownPlan.candidate(42,0,0,1408));
        var neighbor=NpcTownPlan.candidate(42,1,0,1408); double distance=Math.sqrt(first.center().distSqr(neighbor.center()));
        assertTrue(distance>=1000 && distance<=2000); assertNotEquals(first.id(),neighbor.id());
        Settlement town=town(first.id(),first.center().getX());
        var plan=NpcSettlements.blueprint(town); var states=new HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
        for(var p:plan) { assertNull(states.put(p.pos(),p.state()),"Each block has one final construction state"); assertTrue(Math.abs(p.pos().getX()-town.center.getX())<=NpcSettlements.EXTENT); assertTrue(Math.abs(p.pos().getZ()-town.center.getZ())<=NpcSettlements.EXTENT); }
        Station home=NpcSettlements.stations(town).stream().filter(s -> s.role()==StructureRole.HOUSING).findFirst().orElseThrow(); int beds=0;
        for(var e:states.entrySet()) if(e.getValue().getBlock() instanceof BedBlock && e.getValue().getValue(BedBlock.PART)==BedPart.HEAD) {
            BlockPos foot=e.getKey().relative(e.getValue().getValue(BedBlock.FACING).getOpposite());
            assertTrue(home.contains(e.getKey()) && home.contains(foot)); assertTrue(StationDetection.completeBed(e.getValue(),states.get(foot))); beds++;
        }
        assertEquals(12,beds); assertEquals(1,NpcSettlements.stations(town).stream().filter(s -> s.role()==StructureRole.TRADER).count());
        assertEquals(8,NpcSettlements.stations(town).stream().filter(s -> s.role().providesWork()).count());
    }
    @Test void routeRowsKeepStableIdentityAcrossNetwork(MinecraftServer server) {
        String key=UUID.randomUUID().toString();
        PanelView.Row row=new PanelView.Row(new ItemStack(Items.COMPASS),Component.literal("Neighbor"),Component.literal("Neutral"),0,-1,0,key);
        var view=new PanelView(Component.literal("Trade"),Component.empty(),List.of(new PanelView.Tab("Routes",List.of(row))),List.of());
        var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),server.registryAccess(),ConnectionType.NEOFORGE);
        PanelView.STREAM_CODEC.encode(buf,view); var decoded=PanelView.STREAM_CODEC.decode(buf); buf.release();
        assertEquals(key,decoded.tabs().getFirst().rows().getFirst().key());
    }
}
