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
        ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("items",List.of()).forGetter(TradeShipment::items),
        Codec.INT.optionalFieldOf("capacity",TradeSettings.MAX_EXPORTS).forGetter(s -> s.capacity),
        ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("rewards",List.of()).forGetter(TradeShipment::rewardItems)
    ).apply(i,TradeShipment::new));
    public UUID destination;
    public String stage="idle";
    public int capacity=TradeSettings.MAX_EXPORTS;
    public final SimpleContainer rewards=new SimpleContainer(TradeSettings.MAX_EXPORTS);
    public TradeShipment() { super(TradeSettings.MAX_EXPORTS*2); }
    @Override public boolean canPlaceItem(int slot,ItemStack stack) { return slot<capacity; }
    private TradeShipment(Optional<UUID> destination,String stage,List<ItemStack> items,int capacity,List<ItemStack> rewards) {
        this(); this.destination=destination.orElse(null);
        this.capacity=Math.clamp(capacity,TradeSettings.MAX_EXPORTS,TradeSettings.MAX_EXPORTS*2);
        this.stage=List.of("idle","checkpoint","outbound","deliver","return","home").contains(stage) ? stage : "return";
        for(int n=0;n<Math.min(getContainerSize(),items.size());n++) setItem(n,items.get(n).copy());
        for(int n=0;n<Math.min(this.rewards.getContainerSize(),rewards.size());n++) this.rewards.setItem(n,rewards.get(n).copy());
    }
    public boolean travelling() { return !stage.equals("idle") || !isEmpty() || !rewards.isEmpty(); }
    public List<ItemStack> items() { return java.util.stream.IntStream.range(0,getContainerSize()).mapToObj(n -> getItem(n).copy()).toList(); }
    public List<ItemStack> rewardItems() { return java.util.stream.IntStream.range(0,rewards.getContainerSize()).mapToObj(n -> rewards.getItem(n).copy()).toList(); }
    public void finish() { if(!isEmpty() || !rewards.isEmpty()) throw new IllegalStateException("Shipment still holds goods"); destination=null; stage="idle"; }
}
