package com.donutmarket.mod.mixin;

import com.donutmarket.mod.PriceGraph;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
    @Inject(method = "getTooltipImage", at = @At("RETURN"), cancellable = true, require = 0)
    private void addPriceGraph(CallbackInfoReturnable<Optional<TooltipComponent>> cir) {
        if (cir.getReturnValue().isEmpty()) {
            TooltipComponent graph = PriceGraph.of((ItemStack) (Object) this);
            if (graph != null) {
                cir.setReturnValue(Optional.of(graph));
            }
        }
    }
}
