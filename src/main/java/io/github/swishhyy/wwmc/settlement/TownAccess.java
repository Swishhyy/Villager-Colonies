package io.github.swishhyy.wwmc.settlement;

import java.util.*;

/** Ownership remains immutable; accepted membership and reciprocal town consent grant specific capabilities. */
public final class TownAccess {
    public static final List<String> ROLES=List.of("builder","steward");
    private TownAccess() {}
    public static boolean owner(Settlement town,UUID player) { return town!=null && town.owner.equals(player); }
    public static boolean manages(Settlement town,UUID player) { return owner(town,player) || town!=null && "steward".equals(town.campaign.members.get(player)); }
    public static boolean builds(Settlement town,UUID player) { return manages(town,player) || town!=null && "builder".equals(town.campaign.members.get(player)); }
    public static boolean invited(Settlement town,UUID player) { return town!=null && town.campaign.invitations.containsKey(player); }
    public static boolean allied(Settlement a,Settlement b) {
        return a!=null && b!=null && (a.owner.equals(b.owner) || a.campaign.allies.contains(b.id) && b.campaign.allies.contains(a.id));
    }
    public static String invite(Settlement town,UUID owner,UUID player,String role) {
        if(!owner(town,owner)) return "Only the owner may invite members or change their permissions.";
        if(player.equals(owner) || !ROLES.contains(role)) return "Choose a friend and either builder or steward.";
        if(town.campaign.members.size()>=32 && !town.campaign.members.containsKey(player) || town.campaign.invitations.size()>=32) return "This town's membership list is full.";
        town.campaign.invitations.put(player,role); return "Invitation sent as "+role+". Your friend must accept it.";
    }
    public static boolean accept(Settlement town,UUID player) {
        String role=town.campaign.invitations.get(player);
        if(role==null || !ROLES.contains(role) || town.campaign.members.size()>=32 && !town.campaign.members.containsKey(player)) return false;
        town.campaign.invitations.remove(player);
        town.campaign.members.put(player,role); return true;
    }
    public static String alliance(Settlement a,Settlement b,UUID player) {
        if(!owner(a,player)) return "Only the owner may approve town alliances.";
        if(a==b || b.trading.npc) return "Choose another player town.";
        if(allied(a,b)) return "These towns are already allied.";
        if(a.campaign.allies.size()>=32 || b.campaign.allies.size()>=32) return "The alliance list is full.";
        if(a.campaign.allianceOffers.remove(b.id)) { a.campaign.allies.add(b.id); b.campaign.allies.add(a.id); b.campaign.allianceOffers.remove(a.id); return "Alliance accepted with "+b.name+"."; }
        b.campaign.allianceOffers.add(a.id); return "Alliance proposed to "+b.name+". Its owner must accept.";
    }
    public static void leaveAlliance(Settlement a,Settlement b) {
        a.campaign.allies.remove(b.id); b.campaign.allies.remove(a.id);
        a.campaign.allianceOffers.remove(b.id); b.campaign.allianceOffers.remove(a.id);
    }
}
