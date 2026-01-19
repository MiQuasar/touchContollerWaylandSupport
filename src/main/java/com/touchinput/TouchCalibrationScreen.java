package com.touchinput;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft. text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TouchCalibrationScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger("TouchCalibration");
    
    private final Screen parent;
    private final TouchConfig config;
    
    private int step = 0;
    private int[] topLeftTouch = new int[2];
    private int[] topLeftScreen = new int[2];
    private int[] bottomRightTouch = new int[2];
    private int[] bottomRightScreen = new int[2];
    
    private long lastClickTime = 0;
    private static final long CALIBRATION_DELAY = 500; // ms between clicks
    
    private boolean analyzed = false;
    
    public TouchCalibrationScreen(Screen parent, TouchConfig config) {
        super(Text.literal("Touch Calibration"));
        this.parent = parent;
        this.config = config;
    }
    
    @Override
    protected void init() {
        // Skip button
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Skip"), button -> {
            if (client != null) {
                client. setScreen(parent);
            }
        }).dimensions(this.width / 2 - 50, this.height - 30, 100, 20).build());
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // DON'T call renderBackground - super.render() already does it
        super.render(context, mouseX, mouseY, delta);
        
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        
        // Draw instructions
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, 20, 0xFFFFFF);
        
        switch (step) {
            case 0:
                // Draw target in top-left
                drawTarget(context, 50, 50);
                context.drawCenteredTextWithShadow(this.textRenderer, 
                    Text.literal("Touch the RED circle in the TOP-LEFT corner"), 
                    centerX, centerY, 0xFFFFFF);
                context.drawCenteredTextWithShadow(this.textRenderer, 
                    Text.literal("(Step 1 of 2)"), 
                    centerX, centerY + 30, 0xAAAAAA);
                topLeftScreen[0] = 50;
                topLeftScreen[1] = 50;
                break;
                
            case 1:
                // Draw target in bottom-right
                drawTarget(context, this.width - 50, this. height - 50);
                context.drawCenteredTextWithShadow(this.textRenderer, 
                    Text.literal("Touch the RED circle in the BOTTOM-RIGHT corner"), 
                    centerX, centerY, 0xFFFFFF);
                context.drawCenteredTextWithShadow(this.textRenderer, 
                    Text.literal("(Step 2 of 2)"), 
                    centerX, centerY + 30, 0xAAAAAA);
                bottomRightScreen[0] = this.width - 50;
                bottomRightScreen[1] = this.height - 50;
                break;
                
            case 2:
                // Analyzing
                context.drawCenteredTextWithShadow(this.textRenderer, 
                    Text.literal("Analyzing touch mapping..."), 
                    centerX, centerY, 0xFFFFFF);
                
                // Auto-advance after analyzing
                if (! analyzed) {
                    analyzeMapping();
                    analyzed = true;
                }
                break;
                
            case 3:
                // Done
                context.drawCenteredTextWithShadow(this.textRenderer, 
                    Text.literal("Calibration Complete!"), 
                    centerX, centerY - 60, 0x00FF00);
                context.drawCenteredTextWithShadow(this.textRenderer, 
                    Text.literal("Detected mapping:  " + config.mappingMode.getDisplayName()), 
                    centerX, centerY - 30, 0xFFFFFF);
                context.drawCenteredTextWithShadow(this.textRenderer, 
                    Text.literal("Swap XY: " + config.swapXY), 
                    centerX, centerY, 0xFFFFFF);
                context.drawCenteredTextWithShadow(this.textRenderer, 
                    Text. literal("Invert X: " + config.invertX + ", Invert Y: " + config. invertY), 
                    centerX, centerY + 30, 0xFFFFFF);
                context.drawCenteredTextWithShadow(this.textRenderer, 
                    Text.literal("Click anywhere to close"), 
                    centerX, centerY + 80, 0xAAAAAA);
                break;
        }
    }
    
    private void drawTarget(DrawContext context, int x, int y) {
        int radius = 30;
        
        // Draw outer circle
        for (int r = radius; r > 0; r -= 2) {
            int color = r % 4 == 0 ? 0xFFFF0000 : 0xFFFFFFFF;
            drawCircleOutline(context, x, y, r, color);
        }
        
        // Draw crosshair
        context.fill(x - radius, y - 1, x + radius, y + 1, 0xFFFF0000);
        context.fill(x - 1, y - radius, x + 1, y + radius, 0xFFFF0000);
    }
    
    private void drawCircleOutline(DrawContext context, int centerX, int centerY, int radius, int color) {
        for (int angle = 0; angle < 360; angle += 2) {
            double rad = Math.toRadians(angle);
            int x = centerX + (int) (radius * Math.cos(rad));
            int y = centerY + (int) (radius * Math.sin(rad));
            context.fill(x, y, x + 1, y + 1, color);
        }
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Debounce clicks
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastClickTime < CALIBRATION_DELAY) {
            return true;
        }
        lastClickTime = currentTime;
        
        TouchInputHandler handler = WaylandTouchInput.getTouchHandler();
        if (handler == null) {
            LOGGER.warn("TouchInputHandler not available for calibration");
            return super.mouseClicked(mouseX, mouseY, button);
        }
        
        if (step == 0) {
            // Record top-left
            topLeftScreen[0] = (int) mouseX;
            topLeftScreen[1] = (int) mouseY;
            
            // Get current touch coordinates from handler
            int[] touchPos = handler.getLastTouchPosition();
            topLeftTouch[0] = touchPos[0];
            topLeftTouch[1] = touchPos[1];
            
            LOGGER.info("Calibration Step 1: Screen({}, {}) Touch({}, {})", 
                topLeftScreen[0], topLeftScreen[1], topLeftTouch[0], topLeftTouch[1]);
            
            step = 1;
            return true;
            
        } else if (step == 1) {
            // Record bottom-right
            bottomRightScreen[0] = (int) mouseX;
            bottomRightScreen[1] = (int) mouseY;
            
            // Get current touch coordinates from handler
            int[] touchPos = handler.getLastTouchPosition();
            bottomRightTouch[0] = touchPos[0];
            bottomRightTouch[1] = touchPos[1];
            
            LOGGER.info("Calibration Step 2: Screen({}, {}) Touch({}, {})", 
                bottomRightScreen[0], bottomRightScreen[1], bottomRightTouch[0], bottomRightTouch[1]);
            
            step = 2;
            analyzed = false;  // Reset for next render
            return true;
            
        } else if (step == 3) {
            // Done, save and go back
            config.save();
            if (client != null) {
                client.setScreen(parent);
            }
            return true;
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    private void analyzeMapping() {
        // Calculate deltas
        int screenDeltaX = bottomRightScreen[0] - topLeftScreen[0];
        int screenDeltaY = bottomRightScreen[1] - topLeftScreen[1];
        int touchDeltaX = bottomRightTouch[0] - topLeftTouch[0];
        int touchDeltaY = bottomRightTouch[1] - topLeftTouch[1];
        
        LOGGER.info("Calibration Analysis:");
        LOGGER.info("  Screen positions: TL({}, {}) BR({}, {})", 
            topLeftScreen[0], topLeftScreen[1], bottomRightScreen[0], bottomRightScreen[1]);
        LOGGER.info("  Touch positions: TL({}, {}) BR({}, {})", 
            topLeftTouch[0], topLeftTouch[1], bottomRightTouch[0], bottomRightTouch[1]);
        LOGGER.info("  Screen Delta: ({}, {})", screenDeltaX, screenDeltaY);
        LOGGER.info("  Touch Delta: ({}, {})", touchDeltaX, touchDeltaY);
        
        // Get absolute deltas to determine if axes are swapped
        int absTouchDeltaX = Math. abs(touchDeltaX);
        int absTouchDeltaY = Math.abs(touchDeltaY);
        int absScreenDeltaX = Math. abs(screenDeltaX);
        int absScreenDeltaY = Math.abs(screenDeltaY);
        
        // Determine if axes need to be swapped
        // If touch X moves more than Y but screen X moves less than Y, axes are swapped
        boolean axesSwapped = (absTouchDeltaX > absTouchDeltaY) != (absScreenDeltaX > absScreenDeltaY);
        
        config.swapXY = axesSwapped;
        
        // Determine inversions based on sign matching
        // If touch and screen move in opposite directions on same axis, invert
        if (axesSwapped) {
            // X maps to Y, Y maps to X
            config.invertX = (touchDeltaY > 0) == (screenDeltaX < 0);  // touch Y vs screen X
            config.invertY = (touchDeltaX > 0) == (screenDeltaY < 0);  // touch X vs screen Y
        } else {
            // Direct mapping
            config.invertX = (touchDeltaX > 0) == (screenDeltaX < 0);
            config.invertY = (touchDeltaY > 0) == (screenDeltaY < 0);
        }
        
        // Determine best mapping mode
        if (! axesSwapped) {
            if (! config.invertX && !config. invertY) {
                config.mappingMode = TouchConfig.MappingMode.NORMAL;
            } else if (config.invertX && config.invertY) {
                config.mappingMode = TouchConfig. MappingMode.ROTATE_180;
            } else {
                config.mappingMode = TouchConfig.MappingMode. CUSTOM;
            }
        } else {
            config.mappingMode = TouchConfig.MappingMode.CUSTOM;
        }
        
        LOGGER.info("Calibration Result:");
        LOGGER.info("  Mapping Mode: {}", config.mappingMode. getDisplayName());
        LOGGER.info("  Swap XY: {}", config.swapXY);
        LOGGER.info("  Invert X: {}", config.invertX);
        LOGGER.info("  Invert Y: {}", config.invertY);
        
        config.save();
        step = 3;
    }
    
    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }
}
