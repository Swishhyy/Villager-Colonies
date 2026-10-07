package io.github.swishhyy.wwmc.settlement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/** Cargo is isolated from rations and ordinary job supplies, and saved on its carrier exactly once. */
public final class TradeShipment extends SimpleContainer {
    public static final Codec<TradeShipment> CODEC=RecordCodecBuilder.create(i -> i.group(
        Settlement.UUID_CODEC.optionalFieldOf("destination").forGetter(s -> Optional.ofNullable(s.destination)),
        Codec.STRING.optionalFieldOf("stage","idle").forGetter(s -> s.stage),
        ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("items",List.of()).forGetter(TradeShipment::items)
    ).apply(i,TradeShipment::new));
    public UUID destination;
    public String stage="idle";
    public TradeShipment() { super(TradeSettings.MAX_EXPORTS); }
    private TradeShipment(Optional<UUID> destination,String stage,List<ItemStack> items) {
        this(); this.destination=destination.orElse(null);
        this.stage=List.of("idle","checkpoint","outbound","deliver","return","home").contains(stage) ? stage : "return";
        for(int n=0;n<Math.min(getContainerSize(),items.size());n++) setItem(n,items.get(n).copy());
    }
    public boolean travelling() { return !stage.equals("idle") || !isEmpty(); }
    public List<ItemStack> items() { return java.util.stream.IntStream.range(0,getContainerSize()).mapToObj(n -> getItem(n).copy()).toList(); }
    public void finish() { if(!isEmpty()) throw new IllegalStateException("Shipment still holds goods"); destination=null; stage="idle"; }
}
