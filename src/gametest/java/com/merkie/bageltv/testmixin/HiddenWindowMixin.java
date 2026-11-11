package com.merkie.bageltv.testmixin;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.GpuBackend;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Creates the test client's window hidden on macOS, so a test run never takes focus from the window you're typing in.
 * Tests render offscreen without it. Linux runs under Xvfb and keeps its window.
 */
@Mixin(Window.class)
abstract class HiddenWindowMixin {
    @Inject(method = "createGlfwWindow", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J"))
    private static void hide(int width, int height, String title, long monitor, GpuBackend backend,
                             CallbackInfoReturnable<Long> cir) {
        if (Util.getPlatform() == Util.OS.OSX) GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
    }
}
