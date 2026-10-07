package io.github.swishhyy.wwmc.settlement;

import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;

/** Deterministic regions avoid a repeated random spawn check producing towns around the same player. */
public final class NpcTownPlan {
    public record Candidate(UUID id,BlockPos center,String name,String specialty,String region) {}
    private static final String[] NAMES={"Oakridge","Redbrook","Stoneford","Willowfield","Pinehaven","Ashvale","Copperhill","Meadowcross","Foxglade","Birchmere","Westmere","Greenhollow"};
    private static final String[] SPECIALTIES={"farming","timber","mining"};
    private NpcTownPlan() {}
    public static Candidate candidate(long seed,int regionX,int regionZ,int spacing) {
        String key=seed+":"+regionX+":"+regionZ+":"+spacing;
        UUID id=UUID.nameUUIDFromBytes(("wwmc:npc:"+key).getBytes(StandardCharsets.UTF_8));
        Random random=new Random(id.getMostSignificantBits()^id.getLeastSignificantBits());
        int jitter=Math.max(16,spacing/8);
        int x=regionX*spacing+spacing/2+random.nextInt(jitter*2+1)-jitter;
        int z=regionZ*spacing+spacing/2+random.nextInt(jitter*2+1)-jitter;
        // Centering the compact town in a chunk keeps its whole footprint inside a 3x3 trader window.
        x=Math.floorDiv(x,16)*16+8; z=Math.floorDiv(z,16)*16+8;
        return new Candidate(id,new BlockPos(x,0,z),NAMES[random.nextInt(NAMES.length)]+" "+(regionX==0 && regionZ==0 ? "" : Math.abs((long)regionX)+"-"+Math.abs((long)regionZ)).strip(),
                SPECIALTIES[random.nextInt(SPECIALTIES.length)],key);
    }
    /** Nearby alternate plots allow a town beside a hill or player build, without searching or generating distant terrain. */
    public static List<BlockPos> sites(Candidate candidate) {
        List<BlockPos> sites=new ArrayList<>(List.of(candidate.center()));
        for(int x:new int[]{-96,0,96}) for(int z:new int[]{-96,0,96}) if(x!=0 || z!=0) sites.add(candidate.center().offset(x,0,z));
        return sites;
    }
}
