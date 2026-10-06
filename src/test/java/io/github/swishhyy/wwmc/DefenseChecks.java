package io.github.swishhyy.wwmc;

import com.mojang.serialization.JsonOps;
import io.github.swishhyy.wwmc.core.AlarmState;
import io.github.swishhyy.wwmc.core.WavePlan;
import io.github.swishhyy.wwmc.settlement.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;

public final class DefenseChecks {
    private static int checks;
    private static void check(boolean ok,String message) { checks++; if(!ok) throw new AssertionError(message); }
    @Test void alarmsWavesAndClaims() {
        AlarmState alarm=new AlarmState();
        check(alarm.observe(2,10,0,20)==AlarmState.Signal.NONE,"One or two hostiles never send a runner to the bell");
        check(alarm.observe(9,10,20,20)==AlarmState.Signal.NONE,"A group below the threshold is left to the guards");
        check(alarm.observe(20,10,40,20)==AlarmState.Signal.DISPATCH,"A large force sends a guard to the bell");
        alarm.dispatched(40);
        check(alarm.phase()==AlarmState.Phase.RAISING && !alarm.ringing(),"Civilians keep working until the bell actually rings");
        check(alarm.observe(20,10,60,20)==AlarmState.Signal.NONE,"The runner keeps running while the threat remains");
        check(alarm.ring() && alarm.ringing(),"Ringing the bell raises the alarm");
        check(!alarm.ring(),"A second ring does not restart the alarm");
        long now=80;
        for(int quiet=0;quiet<AlarmState.ALL_CLEAR_TICKS-20;quiet+=20,now+=20)
            check(alarm.observe(0,10,now,20)==AlarmState.Signal.NONE,"Citizens stay in cover during a short lull");
        check(alarm.observe(1,10,now,20)==AlarmState.Signal.NONE && alarm.ringing(),"A single sighting during the alarm restarts the quiet period");
        AlarmState.Signal last=AlarmState.Signal.NONE;
        for(int quiet=0;quiet<AlarmState.ALL_CLEAR_TICKS;quiet+=20) last=alarm.observe(0,10,now+=20,20);
        check(last==AlarmState.Signal.ALL_CLEAR && alarm.phase()==AlarmState.Phase.CALM,"Thirty quiet seconds sound the all-clear");
        AlarmState late=new AlarmState();
        late.observe(15,10,0,20); late.dispatched(0);
        check(late.observe(15,10,AlarmState.RUN_TICKS,20)==AlarmState.Signal.STAND_DOWN && late.phase()==AlarmState.Phase.CALM,"A runner who never arrives is replaced");
        check(late.observe(15,10,AlarmState.RUN_TICKS+20,20)==AlarmState.Signal.DISPATCH,"The next check sends another runner");

        check(WavePlan.size(3,2,0.5,40)==4 && WavePlan.size(20,2,0.5,40)==12 && WavePlan.size(32,2,0.5,40)==18,"Waves grow with population");
        check(WavePlan.size(500,2,0.5,40)==40 && WavePlan.size(0,0,0.0,40)==1,"Wave sizes stay within their bounds");
        var small=WavePlan.compose(4,3);
        check(small.get(WavePlan.Attacker.ZOMBIE)==4 && small.get(WavePlan.Attacker.SKELETON)==0 && small.get(WavePlan.Attacker.SPIDER)==0,"A first small wave is only zombies");
        var large=WavePlan.compose(20,36);
        check(large.values().stream().mapToInt(Integer::intValue).sum()==20 && large.get(WavePlan.Attacker.SKELETON)==5 && large.get(WavePlan.Attacker.SPIDER)==3,"Larger towns also face archers and spiders without changing the total");
        check(WavePlan.delay(2,bound -> 0)==36000 && WavePlan.delay(2,bound -> bound-1)==60000,"Waves arrive every two days give or take half a day");
        check(WavePlan.compass(0,-10).equals("north") && WavePlan.compass(10,0).equals("east") && WavePlan.compass(-7,7).equals("south-west"),"Wave warnings name the compass direction");

        Settlement old=new Settlement(UUID.randomUUID(),UUID.randomUUID(),"Old",new BlockPos(0,64,0),150,List.of(),List.of(),"balanced");
        Settlement neighbor=new Settlement(UUID.randomUUID(),UUID.randomUUID(),"Neighbor",new BlockPos(350,64,0),150,List.of(),List.of(),"balanced");
        check(!old.widenTo(Settlement.MIN_RADIUS,List.of(old,neighbor)) && old.radius==150,"A claim does not grow into a neighboring town");
        Settlement far=new Settlement(UUID.randomUUID(),UUID.randomUUID(),"Far",new BlockPos(900,64,0),Settlement.MIN_RADIUS,List.of(),List.of(),"balanced");
        check(old.widenTo(Settlement.MIN_RADIUS,List.of(old,far)) && old.radius==240 && old.contains(new BlockPos(240,70,-240)),"Older small claims widen to the 240-block minimum");
        check(!far.widenTo(Settlement.MIN_RADIUS,List.of(old,far)),"Claims at the minimum are unchanged");
        old.nextWave=123456L; old.waves=3;
        Settlement saved=Settlement.CODEC.parse(JsonOps.INSTANCE,Settlement.CODEC.encodeStart(JsonOps.INSTANCE,old).getOrThrow()).getOrThrow();
        check(saved.nextWave==123456L && saved.waves==3 && saved.radius==240,"Wave schedules and widened claims survive a restart");
        var legacy=Settlement.CODEC.encodeStart(JsonOps.INSTANCE,old).getOrThrow().getAsJsonObject();
        legacy.remove("next_wave"); legacy.remove("waves");
        Settlement older=Settlement.CODEC.parse(JsonOps.INSTANCE,legacy).getOrThrow();
        check(older.nextWave==0 && older.waves==0,"Older saves schedule their first wave from scratch");
        System.out.println("Passed "+checks+" alarm, wave and claim checks.");
    }
    @Test @ExtendWith(EphemeralTestServerProvider.class)
    void guardWeapons(MinecraftServer server) {
        check(GuardWeapons.kind(new ItemStack(Items.IRON_SWORD))==GuardWeapons.Kind.SWORD,"Swords are melee weapons");
        check(GuardWeapons.kind(new ItemStack(Items.BOW))==GuardWeapons.Kind.BOW && GuardWeapons.arrow(new ItemStack(Items.ARROW))
                && GuardWeapons.arrow(new ItemStack(Items.SPECTRAL_ARROW)),"Bows fire any real arrow");
        check(GuardWeapons.kind(new ItemStack(Items.IRON_PICKAXE))==GuardWeapons.Kind.NONE && !GuardWeapons.weapon(new ItemStack(Items.BREAD)),"Tools and food are not guard weapons");
        var spear=BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("iron_spear"));
        check(spear!=Items.AIR && GuardWeapons.kind(new ItemStack(spear))==GuardWeapons.Kind.SPEAR,"Spears are melee weapons");
        check(GuardWeapons.reach(new ItemStack(spear))==4 && GuardWeapons.reach(new ItemStack(Items.IRON_SWORD))==4
                && GuardWeapons.reach(ItemStack.EMPTY)==4,"Swords, spears and fists share four-block citizen melee reach");
        check(GuardWeapons.score(new ItemStack(Items.DIAMOND_SWORD))>GuardWeapons.score(new ItemStack(Items.IRON_SWORD))
                && GuardWeapons.score(new ItemStack(Items.IRON_SWORD))>GuardWeapons.score(new ItemStack(Items.WOODEN_SWORD)),"Guards rank melee weapons by attack damage");
        ItemStack worn=new ItemStack(Items.IRON_SWORD); worn.setDamageValue(200);
        check(GuardWeapons.score(new ItemStack(Items.IRON_SWORD))>GuardWeapons.score(worn),"An intact copy is preferred to a worn one");
        SimpleContainer armory=new SimpleContainer(4);
        armory.setItem(0,new ItemStack(Items.WOODEN_SWORD)); armory.setItem(1,new ItemStack(Items.BOW));
        armory.setItem(2,new ItemStack(Items.DIAMOND_SWORD)); armory.setItem(3,new ItemStack(Items.ARROW,20));
        ItemStack best=InventoryOps.takeBest(List.of(armory),GuardWeapons::melee,GuardWeapons::score);
        check(best.is(Items.DIAMOND_SWORD) && armory.getItem(2).isEmpty() && armory.getItem(0).is(Items.WOODEN_SWORD),"The strongest stocked weapon is taken and the rest stay in storage");
        check(InventoryOps.count(List.of(armory),GuardWeapons::arrow)==20,"Arrow stock is counted across stacks");
        System.out.println("Passed "+checks+" guard weapon checks.");
    }
}
