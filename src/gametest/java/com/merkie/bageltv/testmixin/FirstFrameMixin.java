package com.merkie.bageltv.testmixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.texture.TextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Holds texture animation until the first frame has set the shader globals. When the test client's resources finish
 * loading before its first frame, Minecraft 26.2 animates an atlas without them and crashes with "Missing uniform
 * Globals".
 */
@Mixin(TextureManager.class)
abstract class FirstFrameMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void waitForFirstFrame(CallbackInfo ci) {
        if (RenderSystem.getGlobalSettingsUniform() == null) ci.cancel();
    }
}
