package io.github.swishhyy.wwmc.client;

import io.github.swishhyy.wwmc.settlement.WorkFeedback;
import io.github.swishhyy.wwmc.core.StructureRole;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

/** Small additive poses, leaving walking, sleeping, combat and held-item animations to Minecraft. */
public final class CitizenModel extends HumanoidModel<CitizenRenderer.State> {
    private final boolean outfit;
    public CitizenModel(ModelPart root) { this(root,false); }
    CitizenModel(ModelPart root,boolean outfit) { super(root); this.outfit=outfit; }
    @Override public void setupAnim(CitizenRenderer.State state) {
        super.setupAnim(state);
        // Models are shared by Minecraft's deferred renderer: choose visibility when this entity is drawn.
        if(outfit) {
            boolean cap=state.job==StructureRole.MINE || state.job==StructureRole.QUARRY || state.job==StructureRole.COOK || state.job==StructureRole.HOSPITAL || state.job==StructureRole.ENCHANTER;
            hat.visible=cap && state.headEquipment.isEmpty();
            head.getChild("brim").visible=(cap || state.job==StructureRole.FARM || state.job==StructureRole.FISHERMAN) && state.headEquipment.isEmpty();
        }
        if(state.resting || state.work==WorkFeedback.NONE || state.isUsingItem) return;
        float motion=(float)Math.sin(state.ageInTicks*0.18F)*0.12F;
        if(state.work==WorkFeedback.ENCHANTING) {
            rightArm.xRot=-1.15F+motion; leftArm.xRot=-1.15F-motion;
            rightArm.yRot=-0.3F; leftArm.yRot=0.3F; head.xRot+=0.12F;
        } else if(state.work==WorkFeedback.FISHING) {
            rightArm.xRot=-0.7F+motion*0.3F; leftArm.xRot=-0.25F;
        } else if(state.work==WorkFeedback.TREATING || state.work==WorkFeedback.CRAFTING || state.work==WorkFeedback.PROCESSING) {
            rightArm.xRot=-0.7F+motion; leftArm.xRot=-0.45F-motion;
        }
    }
}
