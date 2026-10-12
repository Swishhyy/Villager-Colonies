package io.github.swishhyy.vc.settlement;

import com.mojang.serialization.Codec;
import io.github.swishhyy.vc.VC;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** A world keeps its own receipts, independently of player deaths, dimensions and reconnects. */
public final class StarterPack {
    @SubscribeEvent public void joined(PlayerEvent.PlayerLoggedInEvent event) {
        if(event.getEntity() instanceof ServerPlayer player) give(player);
    }

    public static boolean give(ServerPlayer player) {
        if(!Receipts.get((ServerLevel)player.level()).claim(player.getUUID())) return false;
        for(var item:List.of(VC.BANNER_ITEM.get(),VC.GUIDE.get())) {
            ItemStack stack=new ItemStack(item);
            player.getInventory().add(stack);
            if(!stack.isEmpty()) player.drop(stack,false);
        }
        player.inventoryMenu.broadcastChanges();
        return true;
    }

    public static final class Receipts extends SavedData {
        public static final Codec<Receipts> CODEC=Settlement.UUID_CODEC.listOf().xmap(Receipts::new,d -> List.copyOf(d.players));
        private static final SavedDataType<Receipts> TYPE=new SavedDataType<>(
                Identifier.fromNamespaceAndPath(VC.MODID,"starter_packs"),Receipts::new,CODEC);
        private final Set<UUID> players;
        public Receipts() { this(List.of()); }
        private Receipts(List<UUID> players) { this.players=new HashSet<>(players); }
        public static Receipts get(ServerLevel level) {
            return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
        }
        public boolean claim(UUID player) {
            if(!players.add(player)) return false;
            setDirty(); return true;
        }
    }
}
