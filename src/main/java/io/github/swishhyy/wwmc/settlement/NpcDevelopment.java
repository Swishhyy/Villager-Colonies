package io.github.swishhyy.wwmc.settlement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;

/** Saved macro decisions are independent of the citizens' existing work/combat AI. */
public final class NpcDevelopment {
    public enum Phase { SUPPLY,RECOVER,GROW,BUILD,CONNECT,STABLE,PAUSED }
    public static final Codec<NpcDevelopment> CODEC=RecordCodecBuilder.create(i -> i.group(
        Codec.BOOL.optionalFieldOf("enabled",true).forGetter(d -> d.enabled),
        Codec.STRING.xmap(Phase::valueOf,Phase::name).optionalFieldOf("phase",Phase.SUPPLY).forGetter(d -> d.phase),
        Codec.INT.optionalFieldOf("completed",0).forGetter(d -> d.completed),
        Codec.LONG.optionalFieldOf("next_project",0L).forGetter(d -> d.nextProject),
        Codec.LONG.optionalFieldOf("next_immigrant",0L).forGetter(d -> d.nextImmigrant),
        NpcConstruction.CODEC.optionalFieldOf("construction").forGetter(d -> Optional.ofNullable(d.construction))
    ).apply(i,NpcDevelopment::new));
    public boolean enabled;
    public Phase phase=Phase.SUPPLY;
    public int completed;
    public long nextProject,nextImmigrant;
    public NpcConstruction construction;
    public NpcDevelopment() {}
    private NpcDevelopment(boolean enabled,Phase phase,int completed,long nextProject,long nextImmigrant,Optional<NpcConstruction> work) {
        this.enabled=enabled; this.phase=phase; this.completed=Math.clamp(completed,0,8);
        this.nextProject=nextProject; this.nextImmigrant=nextImmigrant; this.construction=work.orElse(null);
    }
    public String description() {
        String action=switch(phase) {
            case SUPPLY -> "building food reserves";
            case RECOVER -> "recovering from danger";
            case GROW -> "planning a building";
            case BUILD -> "building";
            case CONNECT -> "trading with neighbours";
            case STABLE -> "settled";
            case PAUSED -> "construction paused";
        };
        return action+" · "+completed+" additions";
    }
}
