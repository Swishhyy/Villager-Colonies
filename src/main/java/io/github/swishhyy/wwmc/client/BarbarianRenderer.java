package io.github.swishhyy.wwmc.client;

import io.github.swishhyy.wwmc.entity.BarbarianEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;

/** Free humanoid arms, native illager skins and visible armor distinguish raiders from citizens. */
public final class BarbarianRenderer extends HumanoidMobRenderer<BarbarianEntity,BarbarianRenderer.State,BarbarianModel> {
    public static final class State extends HumanoidRenderState {
        public boolean archer,armoredHead,armoredChest,armoredLegs;
    }
    private static final Identifier MELEE=Identifier.withDefaultNamespace("textures/entity/illager/vindicator.png");
    private static final Identifier ARCHER=Identifier.withDefaultNamespace("textures/entity/illager/pillager.png");
    public BarbarianRenderer(EntityRendererProvider.Context context) {
        super(context,new BarbarianModel(context.bakeLayer(CitizenRenderer.LAYER)),0.5F);
        ArmorModelSet<HumanoidModel<State>> armor=ArmorModelSet.bake(ModelLayers.ZOMBIE_VILLAGER_ARMOR,context.getModelSet(),HumanoidModel::new);
        addLayer(new HumanoidArmorLayer<>(this,armor,context.getEquipmentRenderer()));
    }
    @Override public State createRenderState() { return new State(); }
    @Override public Identifier getTextureLocation(State state) { return state.archer ? ARCHER : MELEE; }
    @Override public void extractRenderState(BarbarianEntity raider,State state,float partialTick) {
        super.extractRenderState(raider,state,partialTick);
        state.archer=raider.archer();
        state.armoredHead=!raider.getItemBySlot(EquipmentSlot.HEAD).isEmpty();
        state.armoredChest=!raider.getItemBySlot(EquipmentSlot.CHEST).isEmpty();
        state.armoredLegs=!raider.getItemBySlot(EquipmentSlot.LEGS).isEmpty();
        boolean right=raider.getMainArm()==HumanoidArm.RIGHT;
        var pose=raider.archer() && raider.isUsingItem() ? HumanoidModel.ArmPose.BOW_AND_ARROW : HumanoidModel.ArmPose.ITEM;
        state.rightArmPose=right ? pose : HumanoidModel.ArmPose.EMPTY;
        state.leftArmPose=right ? HumanoidModel.ArmPose.EMPTY : pose;
    }
}
