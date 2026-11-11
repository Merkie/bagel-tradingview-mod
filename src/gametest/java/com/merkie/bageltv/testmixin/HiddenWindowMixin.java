package com.merkie.bageltv.testmixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Keeps the test client's window hidden on macOS, so a test run never takes focus from the window you're typing in.
 * Minecraft creates the window hidden and shows it once it has started; tests render offscreen without it.
 * Linux runs under Xvfb and keeps its window.
 */
@Mixin(Minecraft.class)
abstract class HiddenWindowMixin {
    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwShowWindow(J)V"))
    private void stayHidden(long window, Operation<Void> original) {
        if (Util.getPlatform() != Util.OS.OSX) original.call(window);
    }
}
