package io.github.swishhyy.wwmc.mixin;

import io.github.swishhyy.wwmc.settlement.AgeProgression;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Intercept recipe pickup before the menu consumes ingredients or produces a shift-click batch. */
@Mixin(AbstractContainerMenu.class)
public abstract class ResearchResultMixin {
    @Inject(method="clicked",at=@At("HEAD"),cancellable=true)
    private void wwmc$researchResult(int slot,int button,ClickType kind,Player player,CallbackInfo ci) {
        if(AgeProgression.denyResult((AbstractContainerMenu)(Object)this,slot,player)) ci.cancel();
    }
}
