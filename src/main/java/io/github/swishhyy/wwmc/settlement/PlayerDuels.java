package io.github.swishhyy.wwmc.settlement;

import io.github.swishhyy.wwmc.WWMC;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;

/** A matched stake, five-second countdown, nonlethal finish, and bounded arena. Normal equipment wear still applies. */
public final class PlayerDuels {
    private static final Map<ServerLevel,Map<UUID,Long>> GRACE=new WeakHashMap<>();
    private PlayerDuels() {}
    public static ServerPlayer online(ServerLevel level,UUID id) {
        // Level lookup includes real server test players; the player list also finds participants who changed dimension.
        for(ServerPlayer player:level.players()) if(player.getUUID().equals(id)) return player;
        return level.getServer().getPlayerList().getPlayer(id);
    }
    private static boolean eligible(ServerLevel level,ServerPlayer player) {
        return player!=null && player.level()==level && player.isAlive() && !player.isSpectator() && !player.getAbilities().instabuild && player.getHealth()>2;
    }
    private static boolean inside(ServerLevel level,MultiplayerData.Duel duel,Player player) {
        return player.level()==level && player.distanceToSqr(Vec3.atCenterOf(duel.arena))<=24*24;
    }
    public static boolean protectedAfter(ServerLevel level,UUID player) {
        return GRACE.getOrDefault(level,Map.of()).getOrDefault(player,0L)>level.getGameTime();
    }
    public static String challenge(ServerLevel level,ServerPlayer challenger,ServerPlayer opponent,int stake) {
        if(!level.getGameRules().get(GameRules.PVP)) return "This server has player combat disabled.";
        if(!eligible(level,challenger) || !eligible(level,opponent) || challenger==opponent || !challenger.canHarmPlayer(opponent) || !opponent.canHarmPlayer(challenger)) return "Choose another nearby survival player who can fight you and has more than one heart.";
        if(challenger.distanceToSqr(opponent)>16*16 || protectedAfter(level,challenger.getUUID()) || protectedAfter(level,opponent.getUUID())) return "Stand within 16 blocks of your opponent and wait for any duel protection to end.";
        var data=MultiplayerData.get(level);
        if(data.duels.size()>=32 || data.duel(challenger.getUUID())!=null || data.duel(opponent.getUUID())!=null) return "One of you already has a duel or invitation waiting.";
        Settlement arena=SettlementData.get(level).at(challenger.blockPosition());
        if(arena!=null && !TownAccess.manages(arena,challenger.getUUID()) && !TownAccess.manages(arena,opponent.getUUID())) return "Fight outside a claim or with someone who manages this settlement.";
        if(!PlayerContracts.withdraw(challenger,stake)) return "Carry the full stake (0–64 emeralds); it is reserved now.";
        var duel=new MultiplayerData.Duel(UUID.randomUUID(),challenger.getUUID(),opponent.getUUID(),stake,challenger.blockPosition(),false,0,level.getGameTime()+1200);
        data.duels.add(duel); data.setDirty();
        SettlementService.notify(opponent,challenger.getName().getString()+" proposed a duel for "+stake+" emeralds each. Accept at a banner or /wwmc duel accept.");
        WWMC.LOGGER.info("[WWMC] [duels] {} challenged {}, duel {}, stake {}",duel.challenger,duel.opponent,duel.id,stake);
        return "Duel offered for one minute. Your opponent must accept and match the stake; the arena extends 24 blocks from here.";
    }
    public static String accept(ServerLevel level,ServerPlayer player) {
        var data=MultiplayerData.get(level); var duel=data.duel(player.getUUID());
        if(duel==null || duel.accepted || !duel.opponent.equals(player.getUUID())) return "No duel invitation is waiting for you.";
        ServerPlayer challenger=online(level,duel.challenger);
        if(level.getGameTime()>=duel.deadline || !eligible(level,challenger) || !eligible(level,player) || !inside(level,duel,challenger) || !inside(level,duel,player)) {
            finish(level,duel,null,"Invitation expired or a participant left the arena"); return "The duel invitation expired; the stake was refunded.";
        }
        if(!level.getGameRules().get(GameRules.PVP) || !challenger.canHarmPlayer(player) || !player.canHarmPlayer(challenger)) return "Server or team rules currently prevent this duel.";
        if(!PlayerContracts.withdraw(player,duel.stake)) return "Carry "+duel.stake+" emeralds to match the stake.";
        duel.accepted=true; duel.starts=level.getGameTime()+100; duel.deadline=duel.starts+6000; data.setDirty();
        SettlementService.notify(challenger,"Duel accepted. Starts in five seconds; leaving, dying or disconnecting forfeits. Finish at one heart.");
        WWMC.LOGGER.info("[WWMC] [duels] {} accepted, starts at {}, arena {}",duel.id,duel.starts,duel.arena);
        return "Duel starts in five seconds. Finish at one heart; leaving the arena, dying or disconnecting forfeits. Winner receives both stakes.";
    }
    public static String cancel(ServerLevel level,ServerPlayer player) {
        var duel=MultiplayerData.get(level).duel(player.getUUID());
        if(duel==null) return "You have no duel to decline or leave.";
        finish(level,duel,duel.accepted ? duel.other(player.getUUID()) : null,duel.accepted ? "Participant surrendered" : "Invitation declined / cancelled");
        return duel.accepted ? "Duel forfeited. The winner's payment is ready at a banner." : "Invitation closed. Reserved stakes refunded.";
    }
    public static boolean allows(ServerLevel level,Player attacker,LivingEntity defender) {
        if(!(defender instanceof Player player)) return false;
        var duel=MultiplayerData.get(level).duel(attacker.getUUID());
        return duel!=null && duel.accepted && duel.other(attacker.getUUID()).equals(player.getUUID())
                && attacker instanceof ServerPlayer a && player instanceof ServerPlayer b && eligible(level,a) && eligible(level,b)
                && level.getGameRules().get(GameRules.PVP) && a.canHarmPlayer(b) && b.canHarmPlayer(a)
                && level.getGameTime()>=duel.starts && level.getGameTime()<duel.deadline && inside(level,duel,attacker) && inside(level,duel,player);
    }
    /** During a duel only its consenting pair can exchange player damage; countdown and finishing grace protect both. */
    public static boolean blocks(ServerLevel level,Player attacker,LivingEntity defender) {
        if(protectedAfter(level,attacker.getUUID()) || protectedAfter(level,defender.getUUID())) return true;
        var a=MultiplayerData.get(level).duel(attacker.getUUID()); var b=MultiplayerData.get(level).duel(defender.getUUID());
        return (a!=null && a.accepted || b!=null && b.accepted) && !allows(level,attacker,defender);
    }
    public static void finish(ServerLevel level,MultiplayerData.Duel duel,UUID winner,String reason) {
        var data=MultiplayerData.get(level);
        if(!data.duels.remove(duel)) return; // A damage/death/tick callback cannot pay the same stake twice.
        if(winner!=null && duel.accepted && duel.includes(winner)) data.pay(winner,duel.stake*2L);
        else { data.pay(duel.challenger,duel.stake); if(duel.accepted) data.pay(duel.opponent,duel.stake); }
        data.setDirty();
        if(duel.accepted) for(UUID id:List.of(duel.challenger,duel.opponent)) GRACE.computeIfAbsent(level,l -> new HashMap<>()).put(id,level.getGameTime()+100);
        for(UUID id:List.of(duel.challenger,duel.opponent)) {
            var player=online(level,id); if(player!=null) SettlementService.notify(player,"Duel ended: "+reason+(winner==null ? ". Stakes refunded." : winner.equals(id) ? ". You won; collect your payment at a banner." : ". Your opponent won."));
        }
        WWMC.LOGGER.info("[WWMC] [duels] {} ended: {}; winner {}",duel.id,reason,winner);
    }
    public static void tick(ServerLevel level) {
        var data=MultiplayerData.get(level);
        for(var duel:new ArrayList<>(data.duels)) {
            ServerPlayer a=online(level,duel.challenger),b=online(level,duel.opponent);
            if(!duel.accepted) {
                if(level.getGameTime()>=duel.deadline || a==null || b==null) finish(level,duel,null,"Invitation expired");
                continue;
            }
            boolean aValid=eligible(level,a) && inside(level,duel,a),bValid=eligible(level,b) && inside(level,duel,b);
            if(!aValid || !bValid) finish(level,duel,aValid==bValid ? null : aValid ? duel.challenger : duel.opponent,"Participant left or could not continue");
            else if(level.getGameTime()>=duel.deadline || !level.getGameRules().get(GameRules.PVP)) finish(level,duel,null,"Time limit or server combat disabled");
            else if(level.getGameTime()<duel.starts) {
                int seconds=(int)Math.ceil((duel.starts-level.getGameTime())/20.0);
                SettlementService.notify(a,"Duel starts in "+seconds+"…"); SettlementService.notify(b,"Duel starts in "+seconds+"…");
            }
        }
        GRACE.getOrDefault(level,new HashMap<>()).values().removeIf(until -> until<=level.getGameTime());
    }
}
