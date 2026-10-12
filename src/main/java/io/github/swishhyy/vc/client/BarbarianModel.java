package io.github.swishhyy.vc.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

/** Native walking, melee and bow poses with the robe hidden beneath actual equipment. */
public final class BarbarianModel extends HumanoidModel<BarbarianRenderer.State> {
    public BarbarianModel(ModelPart root) { super(root); }
    @Override public void setupAnim(BarbarianRenderer.State state) {
        super.setupAnim(state);
        hat.visible=!state.armoredHead;
        body.getChild("jacket_upper").visible=!state.armoredChest;
        body.getChild("jacket_lower").visible=!state.armoredChest && !state.armoredLegs;
    }
}
