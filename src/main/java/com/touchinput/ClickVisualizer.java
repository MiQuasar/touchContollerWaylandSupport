package com.touchinput;

import net.minecraft.client. gui.DrawContext;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ClickVisualizer {
    private static final ConcurrentLinkedQueue<ClickPoint> clickPoints = new ConcurrentLinkedQueue<>();
    private static final int DOT_RADIUS = 15;
    private static final int DOT_LIFETIME_MS = 2000; // Show dot for 2 seconds
    private static final int DOT_COLOR = 0xFFFF0000; // Red with full alpha
    
    // DEBUG FLAG - Change to false to disable red circles
    public static boolean DEBUG_ENABLED = false;
    
    public static void recordClick(int x, int y) {
        if (! DEBUG_ENABLED) return;
        clickPoints.offer(new ClickPoint(x, y, System.currentTimeMillis()));
    }
    
    public static void drawClicks(DrawContext context) {
        if (!DEBUG_ENABLED) return;
        
        long currentTime = System.currentTimeMillis();
        
        // Draw all active click points
        clickPoints.removeIf(point -> {
            long age = currentTime - point.createdAt;
            if (age > DOT_LIFETIME_MS) {
                return true; // Remove expired points
            }
            
            // Calculate opacity based on age
            float opacity = 1.0f - (age / (float) DOT_LIFETIME_MS);
            int color = (int) (opacity * 255) << 24 | 0xFF0000; // Red with fading alpha
            
            // Draw filled circle
            drawFilledCircle(context, point. x, point.y, DOT_RADIUS, color);
            
            return false; // Keep point
        });
    }
    
    private static void drawFilledCircle(DrawContext context, int centerX, int centerY, int radius, int color) {
        // Draw a filled circle using multiple calls
        for (int y = -radius; y <= radius; y++) {
            for (int x = -radius; x <= radius; x++) {
                if (x * x + y * y <= radius * radius) {
                    context.fill(centerX + x, centerY + y, centerX + x + 1, centerY + y + 1, color);
                }
            }
        }
    }
    
    private static class ClickPoint {
        final int x;
        final int y;
        final long createdAt;
        
        ClickPoint(int x, int y, long createdAt) {
            this.x = x;
            this.y = y;
            this.createdAt = createdAt;
        }
    }
}
