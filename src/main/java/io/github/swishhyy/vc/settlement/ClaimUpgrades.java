package io.github.swishhyy.vc.settlement;

import io.github.swishhyy.vc.Config;
import io.github.swishhyy.vc.VC;
import io.github.swishhyy.vc.core.Upgrades;
import java.util.Collection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** A banner purchase, checked against the live claim list before any payment. */
public final class ClaimUpgrades {
    private ClaimUpgrades() {}
    public static int nextRadius(Settlement town) { return Math.min(Settlement.MAX_RADIUS,town.radius+Config.BORDER_STEP.get()); }
    public static int cost(Settlement town) { return Upgrades.populationCost(Config.BORDER_COST.get(),town.borderLevel); }
    public static String issue(Settlement town,int radius,Collection<Settlement> towns) {
        if(radius<=town.radius || radius>Settlement.MAX_RADIUS) return "The border is already at its maximum size.";
        var neighbour=towns.stream().filter(t -> t!=town && t.overlaps(town.center,radius)).findFirst().orElse(null);
        return neighbour==null ? "" : "Expansion would reach "+neighbour.name+"'s claim. Another settlement already controls that land.";
    }
    public static String issue(ServerLevel level,Settlement town) {
        int radius=nextRadius(town); String issue=issue(town,radius,SettlementData.get(level).settlements);
        if(!issue.isEmpty()) return issue;
        return TownBorders.cornersAt(town.center,radius).stream().allMatch(level.getWorldBorder()::isWithinBounds)
                ? "" : "Expansion would cross the world border.";
    }
    public static boolean buy(ServerLevel level,ServerPlayer player,Settlement town) {
        if(town==null || town.trading.npc || player.level()!=level || !player.isAlive() || SettlementData.get(level).byId(town.id)!=town || !TownAccess.owner(town,player.getUUID()) || player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(town.center))>64
                || !level.hasChunkAt(town.center) || !level.getBlockState(town.center).is(VC.BANNER.get())) return false;
        String issue=issue(level,town);
        if(!issue.isEmpty()) { SettlementService.notify(player,issue); return false; }
        int cost=cost(town);
        if(!SettlementService.pay(player,cost)) { SettlementService.notify(player,"Border expansion costs "+cost+" emeralds; you carry "+SettlementService.emeralds(player)+"."); return false; }
        town.radius=nextRadius(town); town.borderLevel++;
        TownBorders.update(level,town); SettlementService.refreshResources(level,town);
        CampaignService.journal(level,town,"Our border now extends "+town.radius+" blocks from the banner.");
        SettlementData.get(level).setDirty(); SettlementMap.refresh(level);
        SettlementService.notify(player,"Border expanded to "+town.radius+" blocks from the banner ("+(town.radius*2+1)+" × "+(town.radius*2+1)+").");
        return true;
    }
}
