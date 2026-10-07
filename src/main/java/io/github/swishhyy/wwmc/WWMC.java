package io.github.swishhyy.wwmc;
import com.mojang.logging.LogUtils;
import io.github.swishhyy.wwmc.block.SettlementBannerBlock;
import io.github.swishhyy.wwmc.block.StationBlock;
import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.entity.CitizenEntity;
import io.github.swishhyy.wwmc.item.SurveyorItem;
import io.github.swishhyy.wwmc.item.GuideBook;
import io.github.swishhyy.wwmc.menu.WwmcMenus;
import io.github.swishhyy.wwmc.menu.WwmcNetwork;
import io.github.swishhyy.wwmc.settlement.SettlementService;
import io.github.swishhyy.wwmc.settlement.WorkProtection;
import io.github.swishhyy.wwmc.settlement.GuardService;
import io.github.swishhyy.wwmc.settlement.DefenseService;
import io.github.swishhyy.wwmc.settlement.WaveService;
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
import net.minecraft.world.item.WrittenBookItem;
import net.minecraft.core.component.DataComponents;
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
    // Both models are detailed rather than full cubes, so they must not hide their neighbours' faces.
    public static final DeferredBlock<SettlementBannerBlock> BANNER=BLOCKS.registerBlock("settlement_banner",SettlementBannerBlock::new,p -> p.mapColor(MapColor.COLOR_BLUE).strength(2).noOcclusion());
    public static final DeferredItem<BlockItem> BANNER_ITEM=ITEMS.registerSimpleBlockItem(BANNER);
    public static final Map<StructureRole,DeferredBlock<StationBlock>> STATIONS=new EnumMap<>(StructureRole.class);
    public static final Map<StructureRole,DeferredItem<BlockItem>> STATION_ITEMS=new EnumMap<>(StructureRole.class);
    static {
        for(StructureRole role:StructureRole.values()) {
            var block=BLOCKS.registerBlock(role.id()+"_station",p -> new StationBlock(role,p),p -> p.mapColor(MapColor.WOOD).strength(2).noOcclusion());
            STATIONS.put(role,block); STATION_ITEMS.put(role,ITEMS.registerSimpleBlockItem(block));
        }
    }
    public static final DeferredItem<SurveyorItem> SURVEYOR=ITEMS.registerItem("surveyor",SurveyorItem::new,p -> p.stacksTo(1));
    public static final DeferredItem<WrittenBookItem> GUIDE=ITEMS.registerItem("settlement_guide",WrittenBookItem::new,
            p -> p.stacksTo(1).component(DataComponents.WRITTEN_BOOK_CONTENT,GuideBook.content()));
    public static final DeferredHolder<EntityType<?>,EntityType<CitizenEntity>> CITIZEN=ENTITIES.registerEntityType("citizen",CitizenEntity::new,MobCategory.CREATURE,b -> b.sized(0.6F,1.95F).clientTrackingRange(10));
    public static final DeferredHolder<CreativeModeTab,CreativeModeTab> TAB=TABS.register("settlement",() -> CreativeModeTab.builder()
        .title(Component.translatable("itemGroup.wwmc")).withTabsBefore(CreativeModeTabs.COMBAT)
        .icon(() -> BANNER_ITEM.get().getDefaultInstance()).displayItems((p,out) -> {
            out.accept(BANNER_ITEM.get()); out.accept(SURVEYOR.get()); out.accept(GUIDE.get());
            for(StructureRole role:StructureRole.values()) out.accept(STATION_ITEMS.get(role).get());
        }).build());
    public WWMC(IEventBus bus, ModContainer container) {
        BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus); TABS.register(bus);
        WwmcMenus.MENUS.register(bus);
        bus.addListener(this::attributes);
        bus.addListener(WwmcNetwork::register);
        NeoForge.EVENT_BUS.register(new SettlementService());
        NeoForge.EVENT_BUS.register(new WorkProtection());
        NeoForge.EVENT_BUS.register(new GuardService());
        NeoForge.EVENT_BUS.register(new DefenseService());
        NeoForge.EVENT_BUS.register(new WaveService());
        container.registerConfig(ModConfig.Type.SERVER,Config.SPEC);
    }
    private void attributes(EntityAttributeCreationEvent event) {
        event.put(CITIZEN.get(),Villager.createAttributes().add(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE,2.0).build());
    }
}
