package io.github.swishhyy.wwmc.settlement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Saved town policy, separate from the actual goods saved on the citizen carrying them. */
public final class TradeSettings {
    public static final int MAX_EXPORTS=6;
    public record Export(String item,int reserve,int load) {
        public static final Codec<Export> CODEC=RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("item").forGetter(Export::item),
            Codec.INT.fieldOf("reserve").forGetter(Export::reserve),
            Codec.INT.fieldOf("load").forGetter(Export::load)).apply(i,Export::new));
        public Export { reserve=Math.clamp(reserve,0,4096); load=Math.clamp(load,0,64); }
        public Item resolve() {
            Identifier key=Identifier.tryParse(item);
            Item item=key==null ? null : BuiltInRegistries.ITEM.getValue(key);
            return item==null ? Items.AIR : item;
        }
        public Export withReserve(int count) { return new Export(item,count,load); }
        public Export withLoad(int count) { return new Export(item,reserve,count); }
    }
    public static final Codec<TradeSettings> CODEC=RecordCodecBuilder.create(i -> i.group(
        Codec.BOOL.optionalFieldOf("npc",false).forGetter(t -> t.npc),
        Codec.STRING.optionalFieldOf("specialty","").forGetter(t -> t.specialty),
        Codec.STRING.xmap(UUID::fromString,UUID::toString).optionalFieldOf("partner").forGetter(t -> Optional.ofNullable(t.partner)),
        Export.CODEC.listOf().optionalFieldOf("exports",List.of()).forGetter(t -> t.exports),
        Codec.BOOL.optionalFieldOf("paused",false).forGetter(t -> t.paused),
        Codec.STRING.xmap(UUID::fromString,UUID::toString).optionalFieldOf("runner").forGetter(t -> Optional.ofNullable(t.runner)),
        BlockPos.CODEC.optionalFieldOf("runner_pos").forGetter(t -> Optional.ofNullable(t.runnerPos)),
        Codec.LONG.optionalFieldOf("delivered",0L).forGetter(t -> t.delivered),
        Codec.INT.optionalFieldOf("build_index",-1).forGetter(t -> t.buildIndex),
        Codec.unboundedMap(Codec.STRING.xmap(UUID::fromString,UUID::toString),Codec.INT).optionalFieldOf("relations",Map.of()).forGetter(t -> t.relations)
    ).apply(i,TradeSettings::new));
    public boolean npc,paused;
    public String specialty;
    public UUID partner,runner;
    public BlockPos runnerPos;
    public long delivered;
    /** NPC construction checkpoint: -1 means the town is complete. */
    public int buildIndex;
    public final List<Export> exports;
    /** Starts neutral; deliveries build goodwill and attacks make this town refuse that owner's routes. */
    public final Map<UUID,Integer> relations;
    public String status="Choose a destination town";
    public TradeSettings() { this(false,"",Optional.empty(),List.of(),false,Optional.empty(),Optional.empty(),0,-1,Map.of()); }
    private TradeSettings(boolean npc,String specialty,Optional<UUID> partner,List<Export> exports,boolean paused,
            Optional<UUID> runner,Optional<BlockPos> runnerPos,long delivered,int buildIndex,Map<UUID,Integer> relations) {
        this.npc=npc; this.specialty=specialty; this.partner=partner.orElse(null); this.paused=paused;
        this.exports=new ArrayList<>(exports.stream().limit(MAX_EXPORTS).toList());
        this.runner=runner.orElse(null); this.runnerPos=runnerPos.map(BlockPos::immutable).orElse(null);
        this.delivered=Math.max(0,delivered); this.buildIndex=Math.max(-1,buildIndex);
        this.relations=new HashMap<>(relations);
    }
}
