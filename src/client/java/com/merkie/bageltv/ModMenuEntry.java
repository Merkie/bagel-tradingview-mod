package com.merkie.bageltv;

import com.merkie.bageltv.ui.HotkeyScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Gives the mod a settings button in Mod Menu. Only Mod Menu loads this class, so the mod runs without it. */
public final class ModMenuEntry implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return HotkeyScreen::new;
    }
}
