package com.merkie.bageltv.testmixin;

import com.mojang.blaze3d.platform.DisplayData;
import com.mojang.blaze3d.platform.ScreenManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.platform.WindowEventHandler;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Creates the test client's window hidden on macOS, so a test run never takes focus from the window you're typing in.
 * Tests render offscreen without it. Linux runs under Xvfb and keeps its window.
 */
@Mixin(Window.class)
abstract class HiddenWindowMixin {
    @Inject(method = "<init>", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J"))
    private void hide(WindowEventHandler handler, ScreenManager screens, DisplayData display, String videoMode,
                      String title, CallbackInfo ci) {
        if (Util.getPlatform() == Util.OS.OSX) GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
    }
}
