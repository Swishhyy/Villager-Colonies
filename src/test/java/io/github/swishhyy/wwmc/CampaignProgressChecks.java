package io.github.swishhyy.wwmc;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.settlement.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/** Research, schematics, map pings, guard plans and expedition objectives survive a restart, and older saves load. */
public final class CampaignProgressChecks {
    private static int checks;
    private static void check(boolean ok,String message) { checks++; if(!ok) throw new AssertionError(message); }

    @Test void progressSaves() {
        Settlement town=new Settlement(UUID.randomUUID(),UUID.randomUUID(),"Progress",BlockPos.ZERO,240,List.of(),List.of(new Station(new BlockPos(4,64,4),StructureRole.FARM)),"balanced");
        town.progress.research.add("steel_tools"); town.progress.schematics.add("armor");
        UUID author=UUID.randomUUID();
        for(int n=0;n<TownProgress.MAX_PINGS+3;n++)
            town.progress.ping(new TownProgress.Ping(UUID.randomUUID(),author,"Ada",new BlockPos(n,70,-n),n%2==0 ? "bridge" : "nonsense","river crossing "+n,n));
        check(town.progress.pings.size()==TownProgress.MAX_PINGS && town.progress.pings.getFirst().placed()==3,"Only the newest pings are kept");
        check(town.progress.pings.getFirst().kind().equals("meet") && town.progress.pings.get(1).label().equals("Build a bridge here: river crossing 4"),
                "Unknown ping kinds become meeting points; labels read naturally");
        var json=Settlement.CODEC.encodeStart(JsonOps.INSTANCE,town).getOrThrow();
        Settlement reloaded=Settlement.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow();
        check(reloaded.progress.research.equals(Set.of("steel_tools")) && reloaded.progress.schematics.equals(Set.of("armor")),"Research and schematics survive a restart");
        check(reloaded.progress.pings.size()==TownProgress.MAX_PINGS && reloaded.progress.pings.getLast().pos().equals(new BlockPos(18,70,-18)),"Pings survive a restart");
        JsonObject legacy=json.getAsJsonObject(); legacy.remove("progress");
        Settlement old=Settlement.CODEC.parse(JsonOps.INSTANCE,legacy).getOrThrow();
        check(old.progress.research.isEmpty() && old.progress.pings.isEmpty() && old.stations.size()==1,"A town from before research loads with none");
        System.out.println("Passed "+checks+" progress save checks.");
    }

    @Test void guardPlans() {
        BlockPos station=new BlockPos(0,64,0),day=new BlockPos(5,64,0),night=new BlockPos(0,64,5);
        GuardPosts plain=new GuardPosts(station,day,night);
        check(plain.role().equals(GuardPosts.SWORD) && plain.patrol().isEmpty() && plain.chosen(),"Posts from before roles are swordsmen without a route");
        check(!new GuardPosts(station,station,station).chosen(),"A guard standing at its station has no chosen posts");
        List<BlockPos> route=new ArrayList<>();
        for(int n=0;n<12;n++) route.add(new BlockPos(n,64,n));
        GuardPosts planned=plain.withRole(GuardPosts.ARCHER).withPatrol(route);
        check(planned.patrol().size()==GuardPosts.MAX_PATROL && planned.withRole("knight").role().equals(GuardPosts.SWORD),"Routes keep eight points; unknown roles are swordsmen");
        var json=GuardPosts.CODEC.encodeStart(JsonOps.INSTANCE,planned).getOrThrow();
        GuardPosts reloaded=GuardPosts.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow();
        check(reloaded.equals(planned),"Roles and routes survive a restart");
        JsonObject legacy=json.getAsJsonObject(); legacy.remove("role"); legacy.remove("patrol");
        check(GuardPosts.CODEC.parse(JsonOps.INSTANCE,legacy).getOrThrow().equals(plain),"Saved posts from before roles still load");
        check(!GuardPosts.describe(GuardPosts.SHIELD).isEmpty() && GuardPosts.title(GuardPosts.ARCHER).equals("Archer"),"Roles explain themselves");
        System.out.println("Passed "+checks+" guard plan checks.");
    }

    @Test void expeditionsAndResearch() {
        var site=new ExpeditionData.Site(UUID.randomUUID(),new BlockPos(900,70,-300),"fort","1:-1",ExpeditionData.Site.LEADER,Regions.HIGHLANDS.id());
        site.leader=UUID.randomUUID(); site.captives.add(UUID.randomUUID()); site.rewarded=true;
        var json=ExpeditionData.Site.CODEC.encodeStart(JsonOps.INSTANCE,site).getOrThrow();
        var reloaded=ExpeditionData.Site.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow();
        check(reloaded.objective.equals(ExpeditionData.Site.LEADER) && reloaded.resource.equals("highlands") && reloaded.leader.equals(site.leader)
                && reloaded.captives.equals(site.captives) && reloaded.rewarded,"Objectives, captives and captains survive a restart");
        JsonObject legacy=json.getAsJsonObject();
        for(String key:List.of("objective","resource","captives","leader","rewarded")) legacy.remove(key);
        var old=ExpeditionData.Site.CODEC.parse(JsonOps.INSTANCE,legacy).getOrThrow();
        check(old.objective.isEmpty() && old.captives.isEmpty() && old.leader==null && old.task().equals(ExpeditionData.Site.LEADER),
                "Forts found before objectives still hold a captain and a schematic");
        var camp=new ExpeditionData.Site(UUID.randomUUID(),BlockPos.ZERO,"camp","0:0");
        check(camp.task().isEmpty() && camp.goal().startsWith("Drive out"),"Other sites found before objectives keep their plain goal");
        check(Regions.ALL.stream().map(Regions.Region::id).distinct().count()==Regions.ALL.size() && Regions.byId("coast")==Regions.COAST && Regions.byId("moon")==null,
                "Regions have distinct ids");
        for(Research.Tech tech:Research.ALL) {
            check(!tech.costs().isEmpty() && Research.byId(tech.id())==tech,"Every technology costs real goods: "+tech.id());
            check(tech.schematic().isEmpty() || Research.SCHEMATICS.contains(tech.schematic()),"Schematic research names a schematic captains carry: "+tech.id());
        }
        check(Research.SCHEMATICS.stream().allMatch(id -> Research.ALL.stream().anyMatch(t -> t.schematic().equals(id))),"Every schematic unlocks something");
        System.out.println("Passed "+checks+" expedition and research checks.");
    }
}
