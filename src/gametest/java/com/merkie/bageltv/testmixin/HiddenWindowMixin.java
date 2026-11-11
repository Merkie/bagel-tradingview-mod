package com.merkie.bageltv.testmixin;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.util.Util;
import org.lwjgl.sdl.SDLVideo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Keeps the test client's window hidden on macOS, so a test run never takes focus from the window you're typing in.
 * Minecraft 26.3 creates its window through SDL with a fixed set of flags; this adds the hidden one. Tests render
 * offscreen without the window. Linux runs under Xvfb and keeps its window.
 */
@Mixin(Window.class)
abstract class HiddenWindowMixin {
    @ModifyConstant(method = "createWindow", constant = @Constant(longValue = 8224L))
    private long stayHidden(long flags) {
        return Util.getPlatform() == Util.OS.OSX ? flags | SDLVideo.SDL_WINDOW_HIDDEN : flags;
    }
}
