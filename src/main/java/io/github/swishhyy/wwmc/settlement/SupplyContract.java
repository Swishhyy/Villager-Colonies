package io.github.swishhyy.wwmc.settlement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.item.ItemStack;

/** NPC orders escrow actual reward goods when offered. Delivery credit requires a physical handoff. */
public final class SupplyContract {
    public static final Codec<SupplyContract> CODEC=RecordCodecBuilder.create(i -> i.group(
            Settlement.UUID_CODEC.fieldOf("id").forGetter(c -> c.id),Codec.STRING.fieldOf("item").forGetter(c -> c.item),
            Codec.INT.fieldOf("amount").forGetter(c -> c.amount),Codec.INT.optionalFieldOf("delivered",0).forGetter(c -> c.delivered),
            ItemStack.OPTIONAL_CODEC.fieldOf("reward").forGetter(c -> c.reward),Settlement.UUID_CODEC.optionalFieldOf("customer").forGetter(c -> Optional.ofNullable(c.customer)),
            Codec.LONG.fieldOf("expires").forGetter(c -> c.expires)
    ).apply(i,SupplyContract::new));
    public final UUID id;
    public final String item;
    public final int amount;
    public int delivered;
    public ItemStack reward;
    public UUID customer;
    public final long expires;
    public SupplyContract(UUID id,String item,int amount,int delivered,ItemStack reward,Optional<UUID> customer,long expires) {
        this.id=id; this.item=item; this.amount=Math.clamp(amount,1,256); this.delivered=Math.clamp(delivered,0,this.amount);
        this.reward=reward.copy(); this.customer=customer.orElse(null); this.expires=expires;
    }
    public int remaining() { return amount-delivered; }
    public boolean complete() { return remaining()==0; }
}
