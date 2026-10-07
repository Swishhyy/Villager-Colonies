package io.github.swishhyy.wwmc.settlement;

import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.UUID;
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
}
