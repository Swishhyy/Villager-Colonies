package io.github.swishhyy.wwmc;

import com.mojang.serialization.JsonOps;
import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.core.Upgrades;
import io.github.swishhyy.wwmc.core.WavePlan;
import io.github.swishhyy.wwmc.menu.PanelView;
import io.github.swishhyy.wwmc.menu.Panels;
import io.github.swishhyy.wwmc.settlement.*;
import io.netty.buffer.Unpooled;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.FuelValues;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Emerald upgrades, fixed crews, threat from population upgrades, and villager enchanting. */
public final class UpgradeChecks {
    private static int checks;
    private static void check(boolean ok,String message) { checks++; if(!ok) throw new AssertionError(message); }
    private static SimpleContainer box(ItemStack... stacks) {
        SimpleContainer box=new SimpleContainer(27);
        for(int i=0;i<stacks.length;i++) box.setItem(i,stacks[i]);
        return box;
    }

    @Test void prices() {
        check(Upgrades.stationCost(8,0)==8 && Upgrades.stationCost(8,1)==16 && Upgrades.stationCost(8,2)==32,"Each station level costs twice the last");
        check(Upgrades.populationCost(8,0)==8 && Upgrades.populationCost(8,1)==16 && Upgrades.populationCost(8,4)==40,"Each population upgrade costs more than the last");
        check(Upgrades.stationCost(0,2)==0 && Upgrades.populationCost(0,3)==0,"A server can make upgrades free");
        check(Upgrades.populationLimit(0,10,5,64)==10 && Upgrades.populationLimit(2,10,5,64)==20 && Upgrades.populationLimit(30,10,5,64)==64,"Upgrades raise the limit up to the ceiling");
        check(Upgrades.maxPopulationLevel(10,5,64)==11 && Upgrades.maxPopulationLevel(10,5,5)==0,"Upgrades stop at the ceiling");
        check(Upgrades.levelFor(10,10,5)==0 && Upgrades.levelFor(11,10,5)==1 && Upgrades.levelFor(26,10,5)==4,"Towns from before upgrades keep room for everyone they have");
        check(Objects.equals(Upgrades.pay(10,0,8),new Upgrades.Payment(8,0,0)),"Loose emeralds pay first");
        check(Objects.equals(Upgrades.pay(3,1,8),new Upgrades.Payment(3,1,4)),"An emerald block is broken into change");
        check(Objects.equals(Upgrades.pay(0,2,16),new Upgrades.Payment(0,2,2)),"Two blocks cover sixteen with two back");
        check(Upgrades.pay(5,0,8)==null && Upgrades.pay(0,0,1)==null,"A short purse buys nothing");
        check(Objects.equals(Upgrades.pay(0,0,0),new Upgrades.Payment(0,0,0)),"Free upgrades take nothing");
    }

    @Test void stationsAndCrews() {
        BlockPos pos=new BlockPos(0,64,0);
        Station farm=new Station(pos,StructureRole.FARM);
        check(farm.radius()==3 && farm.contains(pos.offset(3,-3,3)) && !farm.contains(pos.offset(4,0,0)),"A plain station reaches three blocks");
        Station wide=farm.withRange(2);
        check(wide.radius()==5 && wide.contains(pos.offset(5,5,-5)) && !wide.contains(pos.offset(6,0,0)) && wide.size().equals("11x11x11"),"Range levels widen the cube");
        check(wide.area().minX()==-5 && wide.area().maxY()==69,"The scanned area follows the range");
        check(new Station(pos,StructureRole.ENCHANTER).radius()==Upgrades.ENCHANTER_RADIUS && Upgrades.ENCHANTER_RADIUS>3,"The enchanter looks farther for its table");
        check(new Station(pos,StructureRole.QUARRY,Direction.EAST,3,2).range()==0,"Quarries have no range upgrades");
        check(new Station(pos,StructureRole.FARM,Direction.NORTH,1,3).crew()==0 && new Station(pos,StructureRole.CRAFTSMAN,Direction.NORTH,0,2).crew()==0
                && new Station(pos,StructureRole.ENCHANTER,Direction.NORTH,0,1).crew()==0,"Farms, craftsmen and enchanters never gain crew");
        check(Upgrades.soloCrew(StructureRole.FARM) && Upgrades.soloCrew(StructureRole.CRAFTSMAN) && Upgrades.soloCrew(StructureRole.ENCHANTER)
                && !Upgrades.hires(StructureRole.FARM) && !Upgrades.hires(StructureRole.LUMBER) && Upgrades.hires(StructureRole.QUARRY)
                && !Upgrades.hires(StructureRole.HOUSING),"Only quarries can hire more crew");
        check(new Station(pos,StructureRole.LUMBER,Direction.NORTH,9,-2).range()==Upgrades.MAX_STATION_LEVEL && new Station(pos,StructureRole.LUMBER,Direction.NORTH,9,-2).crew()==0,"Levels stay within bounds");
        Station upgraded=new Station(pos,StructureRole.QUARRY,Direction.WEST,0,2);
        Station saved=Station.CODEC.parse(JsonOps.INSTANCE,Station.CODEC.encodeStart(JsonOps.INSTANCE,upgraded).getOrThrow()).getOrThrow();
        check(saved.equals(upgraded),"Upgrades survive a restart");
        var legacy=Station.CODEC.encodeStart(JsonOps.INSTANCE,new Station(pos,StructureRole.GUARD)).getOrThrow().getAsJsonObject();
        legacy.remove("range"); legacy.remove("crew");
        check(Station.CODEC.parse(JsonOps.INSTANCE,legacy).getOrThrow().equals(new Station(pos,StructureRole.GUARD)),"Stations from older saves have no upgrades");
        Settlement town=new Settlement(UUID.randomUUID(),UUID.randomUUID(),"Town",BlockPos.ZERO,240,List.of(),new ArrayList<>(List.of(farm,new Station(pos.east(9),StructureRole.WAREHOUSE))),"balanced");
        check(town.replace(farm.withRange(1)) && town.station(pos).range()==1 && town.stations.size()==2,"An upgrade replaces the station's record in place");
        check(!town.replace(new Station(pos.west(40),StructureRole.FARM)),"Only a registered station can be upgraded");
        check(town.nearestStation(pos.offset(4,0,0),s -> s.role()==StructureRole.FARM)!=null && town.nearestStation(pos.offset(5,0,0),s -> s.role()==StructureRole.FARM)==null,
                "Ownership follows the upgraded range");
        town.populationLevel=3;
        Settlement reloaded=Settlement.CODEC.parse(JsonOps.INSTANCE,Settlement.CODEC.encodeStart(JsonOps.INSTANCE,town).getOrThrow()).getOrThrow();
        check(reloaded.populationLevel==3 && reloaded.station(pos).range()==1,"Population and station upgrades survive a restart");
        var old=Settlement.CODEC.encodeStart(JsonOps.INSTANCE,town).getOrThrow().getAsJsonObject();
        old.remove("population_level");
        check(Settlement.CODEC.parse(JsonOps.INSTANCE,old).getOrThrow().populationLevel==Settlement.UNSET,"An older town's population level is worked out from its citizens");
        System.out.println("Passed "+checks+" station upgrade checks.");
    }

    @Test @ExtendWith(EphemeralTestServerProvider.class)
    void oneWorkerPerJobBlockAndLegacyCrewMigration(MinecraftServer server) {
        BlockPos pos=new BlockPos(0,64,0);
        Settlement town=new Settlement(UUID.randomUUID(),UUID.randomUUID(),"Crew migration",pos,240,List.of(),List.of(),"balanced");
        town.campaign.projects.add("hospital");
        for(StructureRole role:StructureRole.values()) {
            var legacy=Station.CODEC.encodeStart(JsonOps.INSTANCE,new Station(pos,role,Direction.WEST,2,0)).getOrThrow().getAsJsonObject();
            legacy.addProperty("crew",3);
            Station station=Station.CODEC.parse(JsonOps.INSTANCE,legacy).getOrThrow();
            check(station.range()==(Upgrades.widens(role) ? 2 : 0),"Migration keeps valid range levels for "+role);
            if(role==StructureRole.QUARRY) {
                check(station.crew()==3 && SettlementService.workerLimit(town,station)==Config.QUARRY_WORKERS.get()+3,"Quarry crew levels and configured capacity survive migration");
            } else {
                check(station.crew()==0 && !Upgrades.hires(role),"Old "+role+" crew upgrades cannot add workers");
                check(SettlementService.workerLimit(town,station)==(role.providesWork() ? 1 : 0),role+" has one job place, or none for a building without a job");
            }
            Station saved=Station.CODEC.parse(JsonOps.INSTANCE,Station.CODEC.encodeStart(JsonOps.INSTANCE,station).getOrThrow()).getOrThrow();
            check(saved.equals(station),"Normalized "+role+" upgrades survive another restart");
        }
        town.campaign.projects.clear();
        check(SettlementService.workerLimit(town,new Station(pos,StructureRole.HOSPITAL))==0,"A hospital still needs its funded project");
        town.trading.npc=true;
        check(SettlementService.workerLimit(town,new Station(pos,StructureRole.QUARRY,Direction.NORTH,0,3))==1,"NPC towns still spread their small population across jobs");
        check(SettlementService.workerLimit(town,new Station(pos,StructureRole.WAREHOUSE))==0,"An NPC warehouse never receives a worker");
    }

    @Test void threat() {
        check(WavePlan.size(20,2,0.5,40,0,2)==WavePlan.size(20,2,0.5,40),"A town without upgrades faces the usual wave");
        check(WavePlan.size(20,2,0.5,40,2,2)==16,"Each population upgrade adds attackers");
        check(WavePlan.size(500,2,0.5,40,3,2)==46,"Upgrades also raise the largest wave");
        var first=WavePlan.compose(20,20,1);
        check(first.get(WavePlan.Attacker.PILLAGER)==2 && first.get(WavePlan.Attacker.VINDICATOR)==0,"The first upgrade brings pillagers");
        var third=WavePlan.compose(30,30,3);
        check(third.get(WavePlan.Attacker.VINDICATOR)>=1 && third.get(WavePlan.Attacker.PILLAGER)<=30/4,"The third upgrade brings vindicators");
        for(int size=1;size<=60;size++) for(int population=0;population<=40;population+=5) for(int threat=0;threat<=12;threat++) {
            var wave=WavePlan.compose(size,population,threat);
            check(wave.values().stream().mapToInt(Integer::intValue).sum()==size && wave.values().stream().allMatch(count -> count>=0),"Every wave keeps its size: "+size+"/"+population+"/"+threat);
        }
        System.out.println("Passed "+checks+" threat checks.");
    }

    @Test @ExtendWith(EphemeralTestServerProvider.class)
    void enchanting(MinecraftServer server) {
        ItemStack book=new ItemStack(Items.BOOK),iron=new ItemStack(Items.IRON_SWORD),diamond=new ItemStack(Items.DIAMOND_SWORD),netherite=new ItemStack(Items.NETHERITE_SWORD);
        check(Enchanting.rarity(netherite)>Enchanting.rarity(diamond) && Enchanting.rarity(diamond)>Enchanting.rarity(iron) && Enchanting.rarity(iron)>Enchanting.rarity(book),
                "Rarer items take longer");
        check(Enchanting.ticks(book,5)==6000 && Enchanting.ticks(netherite,5)>=12000,"A book takes five minutes; netherite at least twice that");
        check(Enchanting.lapisCost(5)==1 && Enchanting.lapisCost(15)==2 && Enchanting.lapisCost(25)==3,"Lapis follows the table's rows");
        check(Enchanting.candidate(book) && Enchanting.candidate(diamond) && !Enchanting.candidate(new ItemStack(Items.ENCHANTED_BOOK)) && !Enchanting.candidate(new ItemStack(Items.STONE)),
                "Unenchanted gear and books are enchantable; stone is not");
        ItemStack broken=new ItemStack(Items.IRON_PICKAXE); broken.setDamageValue(broken.getMaxDamage()-1);
        check(!Enchanting.candidate(broken),"Nearly broken gear waits for the blacksmith");
        RandomSource random=RandomSource.create(42);
        for(int roll=0;roll<50;roll++) {
            int level=Enchanting.level(random,15,diamond,25);
            check(level>=1 && level<=25,"Enchanters never reach level 30");
        }
        check(Enchanting.level(random,15,new ItemStack(Items.STONE),25)==0,"Items without enchantability get no level");
        check(Enchanting.typicalLevel(15,25)==25 && Enchanting.typicalLevel(0,25)==8 && Enchanting.typicalLevel(6,25)==12,"The screen shows about twice the bookshelves");
        ItemStack sword=Enchanting.enchant(server.registryAccess(),random,diamond,25);
        check(sword.is(Items.DIAMOND_SWORD) && sword.isEnchanted() && !diamond.isEnchanted(),"A sword is enchanted as a copy");
        check(!Enchanting.candidate(sword),"An enchanted sword is not enchanted again");
        check(GuardWeapons.score(sword)>GuardWeapons.score(diamond),"Guards prefer an enchanted sword to a plain one");
        ItemStack plate=Enchanting.enchant(server.registryAccess(),random,new ItemStack(Items.IRON_CHESTPLATE),25);
        check(GuardEquipment.protection(plate)>GuardEquipment.protection(new ItemStack(Items.IRON_CHESTPLATE))
                && GuardEquipment.upgrade(plate,new ItemStack(Items.IRON_CHESTPLATE),net.minecraft.world.entity.EquipmentSlot.CHEST),"Guards swap a plain chestplate for an enchanted one");
        ItemStack tome=Enchanting.enchant(server.registryAccess(),random,new ItemStack(Items.BOOK,5),20);
        check(tome.is(Items.ENCHANTED_BOOK) && tome.getCount()==1,"A book becomes one enchanted book");
        SimpleContainer chest=box(new ItemStack(Items.BOOK,3),new ItemStack(Items.IRON_PICKAXE),new ItemStack(Items.DIAMOND_CHESTPLATE),new ItemStack(Items.LAPIS_LAZULI,10));
        check(Enchanting.count(List.of(chest),stack -> false)==5,"Each book counts as one waiting item");
        check(Enchanting.takeNext(List.of(chest),stack -> false).is(Items.DIAMOND_CHESTPLATE),"Armor and weapons go first");
        check(Enchanting.takeNext(List.of(chest),stack -> stack.is(Items.IRON_PICKAXE)).is(Items.BOOK) && chest.getItem(0).getCount()==2,"Books leave a stack one at a time, and skipped items stay");
        check(Enchanting.takeNext(List.of(chest),stack -> false).is(Items.IRON_PICKAXE),"Tools come before books");

        JobStorage.Supplies supplies=new JobStorage.Supplies(FuelValues.vanillaBurnTimes(server.registryAccess(),FeatureFlags.DEFAULT_FLAGS,200),server.getRecipeManager(),null);
        Settlement town=new Settlement(UUID.randomUUID(),UUID.randomUUID(),"Town",BlockPos.ZERO,240,List.of(),List.of(),"balanced");
        SimpleContainer barrel=box(new ItemStack(Items.LAPIS_LAZULI,6),new ItemStack(Items.IRON_SWORD),sword.copy(),tome.copy());
        check(JobStorage.goods(JobStorage.collectable(supplies,town,StructureRole.ENCHANTER,List.of(barrel)))==2,"Couriers take enchanted goods and leave lapis and waiting gear");
        SimpleContainer warehouse=box(new ItemStack(Items.LAPIS_LAZULI,40)),empty=new SimpleContainer(27);
        check(JobStorage.needsSupplies(supplies,StructureRole.ENCHANTER,List.of(empty),List.of(warehouse)),"An enchanter's empty barrel gets lapis");
        CitizenInventory bag=new CitizenInventory(player -> true);
        check(JobStorage.load(supplies,StructureRole.ENCHANTER,List.of(empty),List.of(warehouse),bag)==18 && bag.count(Items.LAPIS_LAZULI)==18,"Couriers carry two reserves of lapis");
        check(JobStorage.input(supplies,StructureRole.ENCHANTER,new ItemStack(Items.LAPIS_LAZULI)) && JobStorage.input(supplies,StructureRole.ENCHANTER,new ItemStack(Items.BOOK)),
                "Couriers deliver both lapis and unenchanted books to enchanters");

        PanelView view=new PanelView(Component.literal("Lumber Station"),Component.literal("range 9x9x9"),List.of(),
                List.of(new PanelView.Action(Panels.RANGE_UP,"Range: 16 emeralds",false,"Widen the range from 9x9x9 to 11x11x11")));
        RegistryFriendlyByteBuf buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),server.registryAccess(),ConnectionType.NEOFORGE);
        PanelView.STREAM_CODEC.encode(buf,view);
        PanelView.Action action=PanelView.STREAM_CODEC.decode(buf).actions().getFirst();
        check(action.id()==Panels.RANGE_UP && !action.enabled() && action.tooltip().getString().startsWith("Widen") && buf.readableBytes()==0,"Upgrade buttons reach the client with their tooltip");
        System.out.println("Passed "+checks+" enchanting checks.");
    }
}
