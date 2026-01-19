package com.touchinput;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            // Use the EXISTING config from TouchInputHandler, not a new one!
            TouchInputHandler handler = WaylandTouchInput.getTouchHandler();
            TouchConfig config = handler != null ? handler.getConfig() : TouchConfig.load();
            return new TouchConfigScreen(parent, config);
        };
    }
}
