package com.touchinput;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WaylandTouchInput implements ClientModInitializer {
    public static final String MOD_ID = "wayland-touch-input";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    
    private static TouchInputHandler touchHandler;
    private static KeyBinding configKeyBinding;

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing Wayland Touch Input mod");
        
        // Initialize touch input handler
        touchHandler = new TouchInputHandler();
        
        // Set initial debug state
        ClickVisualizer.DEBUG_ENABLED = touchHandler.getConfig().debugCirclesEnabled;
        
        // Start touch input thread
        Thread touchThread = new Thread(() -> {
            try {
                touchHandler.start();
            } catch (Exception e) {
                LOGGER.error("Failed to start touch input handler", e);
            }
        }, "TouchInputThread");
        touchThread.setDaemon(true);
        touchThread.start();
        
        // Register keybinding for config screen (Right Control + T)
configKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
    "key.wayland-touch-input.config",
    InputUtil.Type.KEYSYM,
    GLFW.GLFW_KEY_F6,  // Changed to F6
    "category.wayland-touch-input"
));
        // Register tick event to process touch events and check keybinding
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client != null && touchHandler != null) {
                touchHandler.processTouchEvents(client);
                
                // Check if config key was pressed
                while (configKeyBinding.wasPressed()) {
                    openConfigScreen(client);
                }
            }
        });
        
        // Register screen open event
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (touchHandler != null) {
                touchHandler.onScreenOpen(screen);
            }
        });
        
        LOGGER.info("Wayland Touch Input mod initialized successfully");
        LOGGER.info("Press Right Ctrl + T to open config screen");
        
        LOGGER.info("Config loaded: debugCircles={}, touchDevice={}, screenRes={}x{}", 
    touchHandler.getConfig().debugCirclesEnabled,
    touchHandler.getConfig().touchDevicePath,
    touchHandler.getConfig().screenWidth,
    touchHandler.getConfig().screenHeight);
    }
    
    public static TouchInputHandler getTouchHandler() {
        return touchHandler;
    }
    
    public static void openConfigScreen(MinecraftClient client) {
        TouchConfig config = touchHandler != null ? touchHandler.getConfig() : TouchConfig.load();
        client.setScreen(new TouchConfigScreen(client.currentScreen, config));
    }
}
