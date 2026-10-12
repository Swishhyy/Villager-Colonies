package io.github.swishhyy.vc.test;

import io.github.swishhyy.vc.VC;
import io.github.swishhyy.vc.core.WavePlan;
import io.github.swishhyy.vc.entity.BarbarianEntity;
import io.github.swishhyy.vc.entity.CitizenEntity;
import io.github.swishhyy.vc.settlement.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.GameTest;

public final class BarbarianWorldTests {
    private static BarbarianEntity raider(ServerLevel level,BlockPos pos,WavePlan.Attacker role) {
        var raider=new BarbarianEntity(VC.BARBARIAN.get(),level); raider.equip(role); raider.setPersistenceRequired();
        raider.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5); level.addFreshEntity(raider); return raider;
    }
    private static CitizenEntity target(ServerLevel level,BlockPos pos) {
        var citizen=new CitizenEntity(VC.CITIZEN.get(),level); citizen.setNoAi(true);
        citizen.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5); level.addFreshEntity(citizen); return citizen;
    }
    @GameTest(timeoutTicks=100) @EmptyTemplate
    @TestHolder(description="Real barbarian roles retain weapons, armor, damage, health, no-drop chances and town tags across native saves; the VC command root merges every command family.")
    static void nativeRaidSavesAndCommands(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-35000));
            var chunks=CitizenNavigationTests.pinTicking(level,start,2); CitizenNavigationTests.meadow(level,start,-8,24,-8,8);
            for(var role:WavePlan.Attacker.values()) {
                var original=raider(level,start.east(role.ordinal()*5),role); original.setNoAi(true);
                original.setHealth(13); original.getMainHandItem().setDamageValue(7);
                original.addTag(WaveService.WAVE_TAG); original.addTag(WaveService.WAVE_TAG+":"+UUID.randomUUID());
                var output=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,level.registryAccess()); original.saveWithoutId(output);
                var restored=new BarbarianEntity(VC.BARBARIAN.get(),level);
                restored.load(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),output.buildResult()));
                helper.assertTrue(restored.role()==role && restored.getHealth()==13,"Role or health reset while loading "+role);
                helper.assertTrue(restored.getMainHandItem().getItem()==original.getMainHandItem().getItem() && restored.getMainHandItem().getDamageValue()==7,"Weapon changed while loading "+role);
                helper.assertTrue(restored.entityTags().equals(original.entityTags()),"Raid membership was lost");
                for(var slot:List.of(EquipmentSlot.MAINHAND,EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET))
                    helper.assertTrue(restored.getDropChances().byEquipment(slot)==0,"Raid equipment can be farmed: "+slot);
                helper.assertTrue(TrapService.target(restored),"Guards or traps cannot recognize barbarians");
                if(role==WavePlan.Attacker.ARMORED) helper.assertTrue(restored.getItemBySlot(EquipmentSlot.CHEST).is(Items.CHAINMAIL_CHESTPLATE) && restored.getMaxHealth()==24,"Veteran armor or health was lost");
                original.discard(); restored.discard();
            }
            var command=level.getServer().getCommands().getDispatcher().getRoot().getChild("vc");
            helper.assertTrue(command!=null,"VC commands were not registered");
            for(String family:List.of("status","wave","map","town","neighbours")) helper.assertTrue(command.getChild(family)!=null,"Command family was lost: "+family);
            helper.assertTrue(command.getName().equals(VC.MODID),"Command root and active mod ID disagree");
            CitizenNavigationTests.releaseTicking(level,start,chunks); helper.succeed();
        });
    }
    @GameTest(timeoutTicks=600) @EmptyTemplate
    @TestHolder(description="Barbarian foot soldiers walk into native melee range and bowmen aim and hit citizens with real projectiles using their ordinary combat AI.")
    static void realMeleeAndArchery(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-35200));
            var chunks=CitizenNavigationTests.pinTicking(level,start,2); CitizenNavigationTests.meadow(level,start,-8,28,-16,16);
            var meleeTarget=target(level,start.east(10).north(8)); var rangedTarget=target(level,start.east(12).south(8));
            var melee=raider(level,start.east(2).north(8),WavePlan.Attacker.FOOT_SOLDIER);
            var bow=raider(level,start.south(8),WavePlan.Attacker.ARCHER);
            melee.setTarget(meleeTarget); bow.setTarget(rangedTarget);
            helper.succeedWhen(() -> {
                helper.assertTrue(meleeTarget.getHealth()<meleeTarget.getMaxHealth(),"Foot soldier did not reach and hit its target; alive="+melee.isAlive()+", ticks="+melee.tickCount+", distance="+melee.distanceTo(meleeTarget));
                helper.assertTrue(rangedTarget.getHealth()<rangedTarget.getMaxHealth(),"Archer did not aim and hit its target; alive="+bow.isAlive()+", ticks="+bow.tickCount+", distance="+bow.distanceTo(rangedTarget));
                helper.assertTrue(bow.distanceToSqr(rangedTarget)>4*4,"Archer only dealt melee damage");
                melee.discard(); bow.discard(); meleeTarget.discard(); rangedTarget.discard(); CitizenNavigationTests.releaseTicking(level,start,chunks);
            });
        });
    }
    private static List<BarbarianEntity> party(ServerLevel level,BlockPos start,Settlement town) {
        return level.getEntitiesOfClass(BarbarianEntity.class,new AABB(start).inflate(160),r -> r.isAlive() && r.entityTags().contains(WaveService.WAVE_TAG+":"+town.id));
    }
    @GameTest(timeoutTicks=150) @EmptyTemplate
    @TestHolder(description="Actual raids spawn only barbarians outside the defense perimeter, progress from foot soldiers to archers and armor, save irregular timing, and reject overlapping manual raids.")
    static void actualStagedRaids(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-35500));
            var chunks=CitizenNavigationTests.pinTicking(level,start,6); CitizenNavigationTests.meadow(level,start,-80,80,-80,80);
            var town=new Settlement(UUID.randomUUID(),UUID.randomUUID(),"Barbarian fixture",start,96,List.of(),List.of(),"balanced");
            for(int i=0;i<8;i++) town.citizens.add(UUID.randomUUID());
            SettlementData.get(level).settlements.add(town); level.setBlockAndUpdate(start,VC.BANNER.get().defaultBlockState());
            helper.runAtTickTime(10,() -> {
                int spawned=WaveService.callNow(level,town); var first=party(level,start,town);
                helper.assertTrue(spawned>0 && first.size()==spawned && town.waves==1,"First barbarian raid did not spawn");
                var perimeter=WaveService.perimeter(level,town);
                for(var r:first) helper.assertTrue(r.role()==WavePlan.Attacker.FOOT_SOLDIER && r.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING) && !perimeter.contains(r.blockPosition().getX(),r.blockPosition().getZ()),"First raid skipped melee or bypassed defenses");
                long next=town.nextWave;
                helper.assertTrue(next-level.getGameTime()>=72000 && next-level.getGameTime()<=168000,"Raid used the old frequent interval");
                helper.assertTrue(WaveService.callNow(level,town)==0 && town.waves==1 && town.nextWave==next,"Manual raids overlap or advance the schedule while blocked");
                first.forEach(BarbarianEntity::discard); town.waves=2;
                helper.assertTrue(WaveService.callNow(level,town)>0,"Third raid did not spawn");
                var middle=party(level,start,town);
                helper.assertTrue(middle.stream().anyMatch(BarbarianEntity::archer) && middle.stream().noneMatch(r -> r.role()==WavePlan.Attacker.ARMORED),"Archers did not precede armor");
                middle.forEach(BarbarianEntity::discard); town.waves=5;
                for(int i=8;i<16;i++) town.citizens.add(UUID.randomUUID());
                helper.assertTrue(WaveService.callNow(level,town)>0,"Veteran raid did not spawn");
                var late=party(level,start,town);
                helper.assertTrue(late.stream().anyMatch(r -> r.role()==WavePlan.Attacker.ARMORED && !r.getItemBySlot(EquipmentSlot.CHEST).isEmpty()),"Developed raids lack armored veterans");
                var saved=Settlement.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,Settlement.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,town).getOrThrow()).getOrThrow();
                helper.assertTrue(saved.waves==6 && saved.nextWave==town.nextWave,"Raid history or its chosen time was rerolled by saving");
                late.forEach(BarbarianEntity::discard); SettlementData.get(level).settlements.remove(town); CitizenNavigationTests.releaseTicking(level,start,chunks); helper.succeed();
            });
        });
    }
}
