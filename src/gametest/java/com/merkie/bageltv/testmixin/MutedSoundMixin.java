package com.merkie.bageltv.testmixin;

import net.minecraft.client.Options;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mutes the test client. Loading returns early in the fresh run folder, so this runs on every return. Fabric records
 * the loaded options as each test's defaults, so it holds for every test.
 */
@Mixin(Options.class)
abstract class MutedSoundMixin {
    @Inject(method = "load", at = @At("RETURN"))
    private void mute(CallbackInfo ci) {
        ((Options) (Object) this).getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
    }
}
