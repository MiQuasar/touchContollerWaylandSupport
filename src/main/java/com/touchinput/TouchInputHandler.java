package com.touchinput;

import com.touchinput.mixin.MouseMixin;
import net.minecraft.client.MinecraftClient;
import net. minecraft.client.gui.screen.Screen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TouchInputHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("TouchInputHandler");
    
    // Configuration
    private final TouchConfig config;
    
    // State
    private volatile boolean pressedSent = false;
    
    // Event codes from linux/input-event-codes.h
    private static final int EV_SYN = 0x00;
    private static final int EV_KEY = 0x01;
    private static final int EV_ABS = 0x03;
    private static final int ABS_MT_POSITION_X = 0x35;
    private static final int ABS_MT_POSITION_Y = 0x36;
    private static final int ABS_MT_TRACKING_ID = 0x39;
    private static final int BTN_TOUCH = 0x14a;
    
    // Touch state
    private volatile boolean touchActive = false;
    private volatile int currentTouchX = 0;
    private volatile int currentTouchY = 0;
    
    // Last sent position for release
    private volatile int lastSentX = 0;
    private volatile int lastSentY = 0;
    
    // Drag tracking
    private volatile int lastDragX = 0;
    private volatile int lastDragY = 0;
    
    // Event queue for processing in main thread
    private final ConcurrentLinkedQueue<TouchEvent> eventQueue = new ConcurrentLinkedQueue<>();
    
    private RandomAccessFile device;
    private volatile boolean running = true;
    
    public TouchInputHandler() {
        this.config = TouchConfig.load();
        LOGGER.info("TouchInputHandler initialized with config");
    }
    
    public TouchConfig getConfig() {
        return config;
    }
    
    public void start() {
        try {
            device = new RandomAccessFile(config.touchDevicePath, "r");
            LOGGER.info("Successfully opened touch device: {}", config. touchDevicePath);
            
            // Event structure: timeval (16 bytes) + type (2) + code (2) + value (4) = 24 bytes
            byte[] eventData = new byte[24];
            ByteBuffer buffer = ByteBuffer. wrap(eventData).order(ByteOrder.LITTLE_ENDIAN);
            
            while (running) {
                try {
                    device.readFully(eventData);
                    buffer.position(0);
                    
                    // Skip timeval (16 bytes)
                    buffer.position(16);
                    
                    int type = buffer.getShort() & 0xFFFF;
                    int code = buffer.getShort() & 0xFFFF;
                    int value = buffer.getInt();
                    
                    handleInputEvent(type, code, value);
                    
                } catch (IOException e) {
                    if (running) {
                        LOGGER.error("Error reading touch events", e);
                        Thread.sleep(100);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER. error("Failed to initialize touch input", e);
        }
    }
    
    private void handleInputEvent(int type, int code, int value) {
        if (type == EV_ABS) {
            if (code == ABS_MT_POSITION_X) {
                currentTouchX = value;
            } else if (code == ABS_MT_POSITION_Y) {
                currentTouchY = value;
            } else if (code == ABS_MT_TRACKING_ID) {
                if (value != -1) {
                    onTouchStart(currentTouchX, currentTouchY);
                } else {
                    onTouchEnd();
                }
            }
        } else if (type == EV_KEY && code == BTN_TOUCH) {
            if (value == 1) {
                onTouchStart(currentTouchX, currentTouchY);
            } else if (value == 0) {
                onTouchEnd();
            }
        }
    }
    
    private void onTouchStart(int touchX, int touchY) {
        if (! touchActive) {
            touchActive = true;
            pressedSent = false;
            currentTouchX = touchX;
            currentTouchY = touchY;
            lastDragX = touchX;
            lastDragY = touchY;
            
            LOGGER.debug("Touch started at ({}, {})", touchX, touchY);
        }
    }
    
public void processTouchEvents(MinecraftClient client) {
    Screen currentScreen = client.currentScreen;
    
    if (currentScreen == null) {
        if (touchActive) {
            touchActive = false;
            pressedSent = false;
        }
        return;
    }
    
    if (touchActive) {
        // Get GUI scale
        double guiScale = client.options.getGuiScale().getValue();
        
        // Map touch to screen coordinates (for widget interaction)
        int[] currentScreenCoords = config.mapCoordinates(currentTouchX, currentTouchY, guiScale);
        
        // Calculate physical cursor position (for mouse cursor)
        double systemScale = config. getEffectiveSystemScale(client);
        double physicalX = currentScreenCoords[0] * (guiScale / systemScale);
        double physicalY = currentScreenCoords[1] * (guiScale / systemScale);
        
        // Update actual cursor position
        try {
            ((MouseMixin) client.mouse).invokeOnCursorPos(
                client.getWindow().getHandle(),
                physicalX,
                physicalY
            );
        } catch (Exception e) {
            LOGGER.debug("Failed to update cursor position", e);
        }
        
        // Send press on first frame
        if (!pressedSent) {
            try {
                // Move cursor to position first
                currentScreen.mouseMoved((double) currentScreenCoords[0], (double) currentScreenCoords[1]);
                
                // Then press
                simulateMouseClick(client, currentScreen, currentScreenCoords[0], currentScreenCoords[1], 0, true);
                
                pressedSent = true;
                lastSentX = currentScreenCoords[0];
                lastSentY = currentScreenCoords[1];
                lastDragX = currentTouchX;
                lastDragY = currentTouchY;
                
                LOGGER.info("PRESS at ({}, {})", currentScreenCoords[0], currentScreenCoords[1]);
            } catch (Exception e) {
                LOGGER. error("Failed to press", e);
            }
        } else {
            // Continuous drag
            if (currentTouchX != lastDragX || currentTouchY != lastDragY) {
                try {
                    double dragDeltaX = currentScreenCoords[0] - lastSentX;
                    double dragDeltaY = currentScreenCoords[1] - lastSentY;
                    
                    // Update cursor position before drag
                    currentScreen.mouseMoved((double) currentScreenCoords[0], (double) currentScreenCoords[1]);
                    
                    // Then send drag
                    boolean handled = currentScreen.mouseDragged(
                        (double) currentScreenCoords[0], 
                        (double) currentScreenCoords[1], 
                        0,
                        dragDeltaX, 
                        dragDeltaY
                    );
                    
                    LOGGER.debug("DRAG to ({}, {}) delta({}, {}) handled={}", 
                        currentScreenCoords[0], currentScreenCoords[1], 
                        dragDeltaX, dragDeltaY, handled);
                    
                    lastSentX = currentScreenCoords[0];
                    lastSentY = currentScreenCoords[1];
                    lastDragX = currentTouchX;
                    lastDragY = currentTouchY;
                } catch (Exception e) {
                    LOGGER.error("Failed to drag", e);
                }
            }
        }
    }
    
    // Process release events
    TouchEvent event;
    while ((event = eventQueue.poll()) != null) {
        processEvent(client, event);
    }
}
    
    private void onTouchEnd() {
        if (touchActive) {
            touchActive = false;
            pressedSent = false;
            lastDragX = 0;
            lastDragY = 0;
            
            eventQueue.offer(new TouchEvent(TouchEventType.LEFT_RELEASE, lastSentX, lastSentY));
            
            LOGGER.debug("Touch ended at screen ({}, {})", lastSentX, lastSentY);
        }
    }
    
    private void processEvent(MinecraftClient client, TouchEvent event) {
        Screen currentScreen = client.currentScreen;
        
        if (currentScreen == null) {
            return;
        }
        
        switch (event.type) {
            case LEFT_PRESS:
                simulateMouseClick(client, currentScreen, event.x, event.y, 0, true);
                LOGGER.info("LEFT_PRESS at ({}, {})", event.x, event.y);
                break;
                
            case LEFT_RELEASE:
                simulateMouseClick(client, currentScreen, event.x, event. y, 0, false);
                LOGGER.info("LEFT_RELEASE at ({}, {})", event.x, event.y);
                break;
        }
    }
    
    private void simulateMouseClick(MinecraftClient client, Screen screen, int x, int y, int button, boolean pressed) {
        try {
            if (pressed) {
                // Record click for visualization if enabled
                if (config.debugCirclesEnabled) {
                    ClickVisualizer.recordClick(x, y);
                }
                
                LOGGER.info("CLICK at ({}, {}) on screen:  {}", x, y, screen. getClass().getSimpleName());
                
                boolean widgetClicked = false;
                
                // Try to find and click widgets
                for (var widget : screen.children()) {
                    if (widget instanceof net.minecraft.client.gui.widget. ClickableWidget) {
                        net.minecraft.client.gui.widget.ClickableWidget clickable = 
                            (net.minecraft. client.gui.widget.ClickableWidget) widget;
                        
                        if (clickable. isMouseOver((double) x, (double) y)) {
                            clickable.onClick((double) x, (double) y);
                            LOGGER.info("SUCCESS:  Clicked widget '{}' at ({}, {})", 
                                clickable.getMessage().getString(), x, y);
                            
                            try {
                                clickable.mouseClicked(x, y, button);
                                LOGGER.info("Also called mouseClicked on widget");
                            } catch (Exception e) {
                                // Some widgets don't have mouseClicked, that's ok
                            }
                            
                            widgetClicked = true;
                            break;
                        }
                    }
                }
                
                if (!widgetClicked) {
                    LOGGER.info("No widget found at ({}, {}), using screen. mouseClicked()", x, y);
                    boolean consumed = screen.mouseClicked(x, y, button);
                    LOGGER.info("screen.mouseClicked() returned: {}", consumed);
                }
            } else {
                screen.mouseReleased(x, y, button);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to simulate mouse click", e);
        }
    }
    
    public void onScreenOpen(Screen screen) {
        LOGGER.debug("Screen opened: {}", screen. getClass().getSimpleName());
    }
    
    public int[] getLastTouchPosition() {
        return new int[]{currentTouchX, currentTouchY};
    }
    
    public void stop() {
        running = false;
        try {
            if (device != null) {
                device. close();
            }
        } catch (IOException e) {
            LOGGER. error("Error closing touch device", e);
        }
    }
    
    // Helper class for touch events
    private static class TouchEvent {
        final TouchEventType type;
        final int x;
        final int y;
        
        TouchEvent(TouchEventType type, int x, int y) {
            this.type = type;
            this.x = x;
            this.y = y;
        }
    }
    
    private enum TouchEventType {
        LEFT_PRESS,    // Mouse button down
        LEFT_RELEASE   // Mouse button up
    }
    
    public void reload() {
        LOGGER.info("Reloading TouchInputHandler with new config.. .");
        
        // Stop the current device reading
        stop();
        
        // Wait a bit for the thread to stop
        try {
            Thread. sleep(100);
        } catch (InterruptedException e) {
            Thread. currentThread().interrupt();
        }
        
        // Reload config (will pick up saved changes from file)
        TouchConfig newConfig = TouchConfig.load();
        
        // Copy all values to our config instance
        this.config. debugCirclesEnabled = newConfig.debugCirclesEnabled;
        this. config.touchDevicePath = newConfig.touchDevicePath;
        this.config.autoDetectTouchResolution = newConfig.autoDetectTouchResolution;
        this. config.touchMaxX = newConfig.touchMaxX;
        this.config.touchMaxY = newConfig.touchMaxY;
        this.config. autoDetectScreenResolution = newConfig.autoDetectScreenResolution;
        this. config.screenWidth = newConfig.screenWidth;
        this.config.screenHeight = newConfig.screenHeight;
        this.config.mappingMode = newConfig.mappingMode;
        this.config.swapXY = newConfig.swapXY;
        this.config. invertX = newConfig.invertX;
        this.config.invertY = newConfig.invertY;
        this.config.autoDetectSystemScale = newConfig.autoDetectSystemScale;
        this.config. manualSystemScale = newConfig.manualSystemScale;
        
        // Update debug visualizer
        ClickVisualizer.DEBUG_ENABLED = this.config.debugCirclesEnabled;
        
        // Restart the device reading thread
        running = true;
        Thread touchThread = new Thread(() -> {
            try {
                start();
            } catch (Exception e) {
                LOGGER.error("Failed to restart touch input handler", e);
            }
        }, "TouchInputThread-Reloaded");
        touchThread.setDaemon(true);
        touchThread.start();
        
        LOGGER.info("TouchInputHandler reloaded successfully");
    }
}
