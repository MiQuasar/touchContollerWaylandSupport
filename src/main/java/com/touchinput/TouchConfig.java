package com. touchinput;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net. minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Path;

public class TouchConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("TouchConfig");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("wayland-touch-input.json");
    
    // Debug settings
    public boolean debugCirclesEnabled = false;
    
    // Touch device settings
    public boolean autoDetectTouchResolution = true;
    public int touchMaxX = 1599;
    public int touchMaxY = 2559;
    public String touchDevicePath = "/dev/input/event6";
    
    // Screen settings (used as fallback, but live window size is preferred)
    public boolean autoDetectScreenResolution = true;
    public int screenWidth = 2560;
    public int screenHeight = 1600;
    
    // Mapping settings
    public MappingMode mappingMode = MappingMode.AUTO;
    public boolean swapXY = true;      // Swap axes (for rotated screens)
    public boolean invertX = false;    // Invert X axis
    public boolean invertY = true;     // Invert Y axis
    
    // Scaling settings
    public boolean autoDetectSystemScale = true;
    public double manualSystemScale = 1.66;
    
    public enum MappingMode {
        NORMAL("Normal (no swap/invert)"),
        ROTATE_90("Rotate 90° (Landscape Left)"),
        ROTATE_180("Rotate 180° (Upside Down)"),
        ROTATE_270("Rotate 270° (Landscape Right)"),
        AUTO("Auto-detect from system"),
        CUSTOM("Custom (manual settings)");
        
        private final String displayName;
        
        MappingMode(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    public static TouchConfig load() {
        try {
            File configFile = CONFIG_PATH.toFile();
            if (configFile.exists()) {
                try (FileReader reader = new FileReader(configFile)) {
                    TouchConfig config = GSON.fromJson(reader, TouchConfig.class);
                    LOGGER.info("Loaded config from {}", CONFIG_PATH);
                    return config;
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load config, using defaults", e);
        }
        
        TouchConfig config = new TouchConfig();
        config.save(); // Save default config
        return config;
    }
    
    public void save() {
        try {
            File configFile = CONFIG_PATH.toFile();
            configFile.getParentFile().mkdirs();
            
            try (FileWriter writer = new FileWriter(configFile)) {
                GSON.toJson(this, writer);
                LOGGER.info("Saved config to {}", CONFIG_PATH);
            }
        } catch (Exception e) {
            LOGGER. error("Failed to save config", e);
        }
    }
    
    public int[] mapCoordinates(int touchX, int touchY, double guiScale) {
    int screenX, screenY;
    
    MappingMode effectiveMode = mappingMode;
    
    if (mappingMode == MappingMode. AUTO) {
        effectiveMode = detectMappingMode();
    }
    
    switch (effectiveMode) {
        case NORMAL:
            screenX = (int) ((touchX / (double) touchMaxX) * screenWidth / guiScale);
            screenY = (int) ((touchY / (double) touchMaxY) * screenHeight / guiScale);
            break;
            
        case ROTATE_90:
            screenX = (int) ((touchY / (double) touchMaxY) * screenWidth / guiScale);
            screenY = (int) (((touchMaxX - touchX) / (double) touchMaxX) * screenHeight / guiScale);
            break;
            
        case ROTATE_180:
            screenX = (int) (((touchMaxX - touchX) / (double) touchMaxX) * screenWidth / guiScale);
            screenY = (int) (((touchMaxY - touchY) / (double) touchMaxY) * screenHeight / guiScale);
            break;
            
        case ROTATE_270:
            screenX = (int) (((touchMaxY - touchY) / (double) touchMaxY) * screenWidth / guiScale);
            screenY = (int) ((touchX / (double) touchMaxX) * screenHeight / guiScale);
            break;
            
        case CUSTOM:
            double normX = touchX / (double) touchMaxX;
            double normY = touchY / (double) touchMaxY;
            
            if (invertX) normX = 1.0 - normX;
            if (invertY) normY = 1.0 - normY;
            
            if (swapXY) {
                screenX = (int) (normY * screenWidth / guiScale);
                screenY = (int) (normX * screenHeight / guiScale);
            } else {
                screenX = (int) (normX * screenWidth / guiScale);
                screenY = (int) (normY * screenHeight / guiScale);
            }
            break;
            
        case AUTO:
        default: 
            screenX = (int) ((touchY / (double) touchMaxY) * screenWidth / guiScale);
            screenY = (int) (((touchMaxX - touchX) / (double) touchMaxX) * screenHeight / guiScale);
            break;
    }
    
    screenX = Math.max(0, Math.min((int)(screenWidth / guiScale) - 1, screenX));
    screenY = Math.max(0, Math.min((int)(screenHeight / guiScale) - 1, screenY));
    
    return new int[]{screenX, screenY};
}
    
    private MappingMode detectMappingMode() {
        // Try to detect screen orientation from system
        // Check if screen is rotated (width < height means portrait)
        
        if (touchMaxX < touchMaxY && screenWidth > screenHeight) {
            // Touch sensor is portrait (vertical), screen is landscape (horizontal)
            // This means screen is rotated 90° from sensor
            return MappingMode. ROTATE_90;
        } else if (touchMaxX > touchMaxY && screenWidth < screenHeight) {
            // Touch sensor is landscape, screen is portrait
            return MappingMode.ROTATE_270;
        } else {
            // Same orientation
            return MappingMode.NORMAL;
        }
    }
    
    public double getEffectiveSystemScale(MinecraftClient client) {
        if (! autoDetectSystemScale) {
            return manualSystemScale;
        }
        
        try {
            // Auto-detect from window size
            int windowWidth = client.getWindow().getWidth();
            int windowHeight = client.getWindow().getHeight();
            
            double scaleX = (double) screenWidth / windowWidth;
            double scaleY = (double) screenHeight / windowHeight;
            
            return (scaleX + scaleY) / 2.0;
        } catch (Exception e) {
            LOGGER. warn("Auto-detect failed, using manual scale:  {}", manualSystemScale);
            return manualSystemScale;
        }
    }
}
