package io.github.swishhyy.wwmc;
import com.mojang.logging.LogUtils;
import io.github.swishhyy.wwmc.block.SettlementBannerBlock;
import io.github.swishhyy.wwmc.block.StationBlock;
import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.entity.CitizenEntity;
import io.github.swishhyy.wwmc.item.SurveyorItem;
import io.github.swishhyy.wwmc.settlement.SettlementService;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.*;
import org.slf4j.Logger;

@Mod(WWMC.MODID)
public final class WWMC {
    public static final String MODID="wwmc";
    public static final Logger LOGGER=LogUtils.getLogger();
    public static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(MODID);
    public static final DeferredRegister.Entities ENTITIES=DeferredRegister.createEntities(MODID);
    public static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,MODID);
    public static final DeferredBlock<SettlementBannerBlock> BANNER=BLOCKS.registerBlock("settlement_banner",SettlementBannerBlock::new,p -> p.mapColor(MapColor.COLOR_BLUE).strength(2));
    public static final DeferredItem<BlockItem> BANNER_ITEM=ITEMS.registerSimpleBlockItem(BANNER);
    public static final Map<StructureRole,DeferredBlock<StationBlock>> STATIONS=new EnumMap<>(StructureRole.class);
    public static final Map<StructureRole,DeferredItem<BlockItem>> STATION_ITEMS=new EnumMap<>(StructureRole.class);
    static {
        for(StructureRole role:StructureRole.values()) {
            var block=BLOCKS.registerBlock(role.id()+"_station",p -> new StationBlock(role,p),p -> p.mapColor(MapColor.WOOD).strength(2));
            STATIONS.put(role,block); STATION_ITEMS.put(role,ITEMS.registerSimpleBlockItem(block));
        }
    }
    public static final DeferredItem<SurveyorItem> SURVEYOR=ITEMS.registerItem("surveyor",SurveyorItem::new,p -> p.stacksTo(1));
    public static final DeferredHolder<EntityType<?>,EntityType<CitizenEntity>> CITIZEN=ENTITIES.registerEntityType("citizen",CitizenEntity::new,MobCategory.CREATURE,b -> b.sized(0.6F,1.95F).clientTrackingRange(10));
    public static final DeferredHolder<CreativeModeTab,CreativeModeTab> TAB=TABS.register("settlement",() -> CreativeModeTab.builder()
        .title(Component.translatable("itemGroup.wwmc")).withTabsBefore(CreativeModeTabs.COMBAT)
        .icon(() -> BANNER_ITEM.get().getDefaultInstance()).displayItems((p,out) -> {
            out.accept(BANNER_ITEM.get()); out.accept(SURVEYOR.get());
            for(StructureRole role:StructureRole.values()) out.accept(STATION_ITEMS.get(role).get());
        }).build());
    public WWMC(IEventBus bus, ModContainer container) {
        BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus); TABS.register(bus);
        bus.addListener(this::attributes);
        NeoForge.EVENT_BUS.register(new SettlementService());
        container.registerConfig(ModConfig.Type.SERVER,Config.SPEC);
    }
    private void attributes(EntityAttributeCreationEvent event) { event.put(CITIZEN.get(),Villager.createAttributes().build()); }
}
