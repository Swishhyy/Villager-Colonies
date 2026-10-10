package io.github.swishhyy.wwmc.client;

import io.github.swishhyy.wwmc.settlement.WorkFeedback;
import io.github.swishhyy.wwmc.core.StructureRole;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

/** Small additive poses, leaving walking, sleeping, combat and held-item animations to Minecraft. */
public final class CitizenModel extends HumanoidModel<CitizenRenderer.State> {
    private final boolean outfit,profession;
    public CitizenModel(ModelPart root) { this(root,false); }
    CitizenModel(ModelPart root,boolean outfit) { this(root,outfit,false); }
    CitizenModel(ModelPart root,boolean outfit,boolean profession) { super(root); this.outfit=outfit; this.profession=profession; }
    @Override public void setupAnim(CitizenRenderer.State state) {
        super.setupAnim(state);
        head.visible=!profession || !state.armoredHead;
        body.visible=!profession || !state.armoredChest;
        rightArm.visible=leftArm.visible=!profession || !state.armoredChest;
        rightLeg.visible=leftLeg.visible=!profession || !state.armoredLegs && !state.armoredFeet;
        if(!outfit) {
            hat.visible=!state.armoredHead;
            body.getChild("jacket_upper").visible=!state.armoredChest;
            body.getChild("jacket_lower").visible=!state.armoredChest && !state.armoredLegs;
        }
        // Models are shared by Minecraft's deferred renderer: choose visibility when this entity is drawn.
        if(outfit) {
            boolean cap=state.job==StructureRole.MINE || state.job==StructureRole.QUARRY || state.job==StructureRole.COOK || state.job==StructureRole.HOSPITAL || state.job==StructureRole.ENCHANTER || state.job==StructureRole.RESEARCHER;
            hat.visible=cap && state.headEquipment.isEmpty();
            head.getChild("brim").visible=(cap || state.job==StructureRole.FARM || state.job==StructureRole.FISHERMAN) && state.headEquipment.isEmpty();
            body.visible=!state.armoredChest && !state.armoredLegs;
            rightArm.visible=leftArm.visible=!state.armoredChest;
        }
        if(state.resting || state.work==WorkFeedback.NONE || state.isUsingItem) return;
        float motion=(float)Math.sin(state.ageInTicks*0.18F)*0.12F;
        ModelPart toolArm=state.rightHanded ? rightArm : leftArm;
        if(state.work==WorkFeedback.MINING || state.work==WorkFeedback.CHOPPING || state.work==WorkFeedback.BUTCHERING || state.work==WorkFeedback.SMITHING) {
            float cycle=(state.workElapsed%20)/20F;
            float strike=cycle<0.6F ? cycle/0.6F : (1-cycle)/0.4F;
            toolArm.xRot=-0.45F-1.9F*strike; toolArm.yRot=state.rightHanded ? -0.12F : 0.12F;
            toolArm.zRot=state.rightHanded ? 0.12F : -0.12F;
        } else
        if(state.work==WorkFeedback.ENCHANTING) {
            rightArm.xRot=-1.15F+motion; leftArm.xRot=-1.15F-motion;
            rightArm.yRot=-0.3F; leftArm.yRot=0.3F; head.xRot+=0.12F;
        } else if(state.work==WorkFeedback.FISHING_CAST) {
            float cast=Math.min(1,state.workElapsed/25F);
            toolArm.xRot=cast<0.45F ? -0.5F-2.0F*(cast/0.45F) : -2.5F+1.7F*((cast-0.45F)/0.55F);
            leftArm.xRot=state.rightHanded ? -0.25F : leftArm.xRot;
        } else if(state.work==WorkFeedback.FISHING_REEL) {
            float pull=Math.min(1,state.workElapsed/30F);
            toolArm.xRot=-0.85F-1.2F*(float)Math.sin(Math.PI*pull);
            toolArm.yRot=state.rightHanded ? -0.2F : 0.2F;
        } else if(state.work==WorkFeedback.FISHING) {
            toolArm.xRot=-0.8F+motion*0.15F;
        } else if(state.work==WorkFeedback.RESEARCHING) {
            toolArm.xRot=-0.9F+motion*0.6F; toolArm.yRot=-0.25F; head.xRot+=0.2F;
        } else if(state.work==WorkFeedback.TREATING || state.work==WorkFeedback.CRAFTING || state.work==WorkFeedback.PROCESSING) {
            rightArm.xRot=-0.7F+motion; leftArm.xRot=-0.45F-motion;
        }
    }
}
