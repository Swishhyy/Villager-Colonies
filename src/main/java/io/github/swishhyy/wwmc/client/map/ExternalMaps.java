package io.github.swishhyy.wwmc.client.map;

import io.github.swishhyy.wwmc.VC;
import io.github.swishhyy.wwmc.menu.MapData;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Has no external API types. Optional adapters are loaded only after their mod is present. */
public final class ExternalMaps {
    public interface Provider {
        String id();
        void sync(MapData map);
        boolean open();
        void clear();
    }
    private static final Map<String,Provider> PROVIDERS=new LinkedHashMap<>();
    private static MapData latest;
    private static boolean discovered;
    private int ticks;
    public static void register(Provider provider) { PROVIDERS.put(provider.id(),provider); }
    private static void discover() {
        if(discovered) return;
        discovered=true;
        load("xaeroworldmap","XaeroWorldMapCompat");
        load("xaerominimap","XaeroMinimapCompat");
    }
    private static void load(String mod,String adapter) {
        if(!ModList.get().isLoaded(mod)) return;
        try {
            register((Provider)Class.forName("io.github.swishhyy.wwmc.client.map."+adapter).getConstructor().newInstance());
        } catch(ReflectiveOperationException | LinkageError e) { VC.LOGGER.warn("[VC] Could not initialize optional {} integration",mod,e); }
    }
    private static void sync() {
        var mc=Minecraft.getInstance();
        if(latest==null || mc.level==null || !mc.level.dimension().identifier().toString().equals(latest.dimension())) return;
        discover();
        for(var provider:PROVIDERS.values()) try { provider.sync(latest); }
        catch(RuntimeException | LinkageError e) { VC.LOGGER.debug("[VC] Optional {} map is not ready",provider.id(),e); }
    }
    public static void receive(MapData map,boolean open) {
        latest=map; sync();
        if(!open) return;
        var mc=Minecraft.getInstance();
        if(mc.player==null) return;
        mc.player.closeContainer();
        for(String id:new String[]{"journeymap","xaeroworldmap","xaerominimap"}) {
            Provider provider=PROVIDERS.get(id);
            if(provider==null) continue;
            try { if(provider.open()) return; }
            catch(RuntimeException | LinkageError e) { VC.LOGGER.warn("[VC] Could not open optional {} map",id,e); }
        }
        String message=ModList.get().isLoaded("journeymap") || ModList.get().isLoaded("xaeroworldmap") || ModList.get().isLoaded("xaerominimap")
                ? "Your map is still starting. Try Settlement map again in a moment."
                : "Install JourneyMap or Xaero's World Map to view settlement markers. Neighbours and /locate settlement also show towns.";
        mc.player.sendSystemMessage(Component.literal(message));
    }
    @SubscribeEvent public void tick(ClientTickEvent.Post event) { if(++ticks%20==0) sync(); }
    @SubscribeEvent public void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        latest=null;
        for(var provider:PROVIDERS.values()) try { provider.clear(); }
        catch(RuntimeException | LinkageError e) { VC.LOGGER.debug("[VC] Optional {} map already closed",provider.id(),e); }
    }
}
