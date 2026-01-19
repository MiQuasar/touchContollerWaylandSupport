package com.touchinput;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

public class TouchConfigScreen extends Screen {
    private final Screen parent;
    private final TouchConfig config;
    
    private TextFieldWidget touchMaxXField;
    private TextFieldWidget touchMaxYField;
    private TextFieldWidget screenWidthField;
    private TextFieldWidget screenHeightField;
    private TextFieldWidget systemScaleField;
    private TextFieldWidget devicePathField;
    
    // Tab system
    private enum Tab {
        DISPLAY("Display", 0),
        TOUCH("Touch", 1),
        MAPPING("Mapping", 2),
        SYSTEM("System", 3);
        
        final String name;
        final int index;
        
        Tab(String name, int index) {
            this.name = name;
            this.index = index;
        }
    }
    
    private Tab currentTab = Tab.DISPLAY;
    
    public TouchConfigScreen(Screen parent, TouchConfig config) {
        super(Text.literal("Touch Input Settings"));
        this.parent = parent;
        this.config = config;
    }
    
    @Override
    protected void init() {
        this.clearChildren();
        
        int tabBarX = this.width - 170;
        int tabBarY = 50;
        int tabWidth = 160;
        int tabHeight = 28;
        
        // Create tab buttons with visual distinction for selected tab
        for (Tab tab : Tab.values()) {
            int yPos = tabBarY + (tab.index * (tabHeight + 6));
            final Tab currentTabFinal = tab;
            
            ButtonWidget tabButton = ButtonWidget.builder(
                Text.literal(tab == currentTab ? "► " + tab.name : "  " + tab.name),
                button -> {
                    currentTab = currentTabFinal;
                    init(); // Reinitialize to update content
                }
            ).dimensions(tabBarX, yPos, tabWidth, tabHeight).build();
            
            this.addDrawableChild(tabButton);
        }
        
        // Content area
        int contentX = 20;
        int contentY = 60;
        int contentWidth = this.width - 200;
        
        // Render content based on selected tab
        switch (currentTab) {
            case DISPLAY:
                initDisplayTab(contentX, contentY, contentWidth);
                break;
            case TOUCH:
                initTouchTab(contentX, contentY, contentWidth/2);
                break;
            case MAPPING:
                initMappingTab(contentX, contentY, contentWidth/2);
                break;
            case SYSTEM:
                initSystemTab(contentX, contentY, contentWidth/2);
                break;
        }
        
        // Bottom buttons (always visible)
        int bottomY = this.height - 30;
        int buttonWidth = 150;
        int centerX = (contentWidth / 2) + contentX;
        
        this.addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> {
            config.save();
            
            // Reload the touch handler with new config
            TouchInputHandler handler = WaylandTouchInput.getTouchHandler();
            if (handler != null) {
                handler.reload();
            }
            
            if (client != null) {
                client.setScreen(parent);
            }
        }).dimensions(centerX - buttonWidth - 5, bottomY, buttonWidth, 20).build());
        
        this.addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL, button -> {
            if (client != null) {
                client.setScreen(parent);
            }
        }).dimensions(centerX + 5, bottomY, buttonWidth, 20).build());
    }
    
    private void initDisplayTab(int x, int y, int width) {
        int fieldWidth = 100;
        
        y += 10; // Add some top padding
        
        // Screen Resolution Section
        this.addDrawableChild(CyclingButtonWidget.onOffBuilder(Text.literal("Auto"), Text.literal("Manual"))
            .initially(config.autoDetectScreenResolution)
            .build(x, y, fieldWidth, 20, Text.literal("Screen Resolution"),
                (button, value) -> {
                    config.autoDetectScreenResolution = value;
                    updateFieldsEnabled();
                }));
        
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Detect Now"), button -> {
            config.autoDetectScreenResolution = true;
            detectScreenResolution();
            screenWidthField.setText(String.valueOf(config.screenWidth));
            screenHeightField.setText(String.valueOf(config.screenHeight));
        }).dimensions(x + fieldWidth + 10, y, fieldWidth, 20).build());
        
        y += 35;
        
        // Screen Width
        screenWidthField = new TextFieldWidget(this.textRenderer, x, y, fieldWidth, 20, Text.literal("Width"));
        screenWidthField.setText(String.valueOf(config.screenWidth));
        screenWidthField.setChangedListener(text -> {
            try { config.screenWidth = Integer.parseInt(text); } catch (NumberFormatException ignored) {}
        });
        this.addDrawableChild(screenWidthField);
        
        // Screen Height
        screenHeightField = new TextFieldWidget(this.textRenderer, x + fieldWidth + 10, y, fieldWidth, 20, Text.literal("Height"));
        screenHeightField.setText(String.valueOf(config.screenHeight));
        screenHeightField.setChangedListener(text -> {
            try { config.screenHeight = Integer.parseInt(text); } catch (NumberFormatException ignored) {}
        });
        this.addDrawableChild(screenHeightField);
        
        updateFieldsEnabled();
    }
    
    private void initTouchTab(int x, int y, int width) {
        int fieldWidth = 100;
        
        y += 10; // Add some top padding
        
        // Debug Section
        this.addDrawableChild(CyclingButtonWidget.onOffBuilder(Text.literal("ON"), Text.literal("OFF"))
            .initially(config.debugCirclesEnabled)
            .build(x, y, fieldWidth*2, 20, Text.literal("Debug Circles"),
                (button, value) -> {
                    config.debugCirclesEnabled = value;
                }));
        
        y += 35;
        
        // Device Path
        devicePathField = new TextFieldWidget(this.textRenderer, x, y, fieldWidth*2, 20, Text.literal("Device Path"));
        devicePathField.setText(config.touchDevicePath);
        devicePathField.setChangedListener(text -> config.touchDevicePath = text);
        this.addDrawableChild(devicePathField);
        
        y += 35;
        
        // Touch Resolution Section
        this.addDrawableChild(CyclingButtonWidget.onOffBuilder(Text.literal("Auto"), Text.literal("Manual"))
            .initially(config.autoDetectTouchResolution)
            .build(x, y, fieldWidth, 20, Text.literal("Touch Resolution"),
                (button, value) -> {
                    config.autoDetectTouchResolution = value;
                    updateFieldsEnabled();
                }));
        
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Detect Now"), button -> {
            config.autoDetectTouchResolution = true;
            detectTouchResolution();
            touchMaxXField.setText(String.valueOf(config.touchMaxX));
            touchMaxYField.setText(String.valueOf(config.touchMaxY));
        }).dimensions(x + 110, y, fieldWidth, 20).build());
        
        y += 35;
        
        // Touch Max X
        touchMaxXField = new TextFieldWidget(this.textRenderer, x, y, fieldWidth, 20, Text.literal("Max X"));
        touchMaxXField.setText(String.valueOf(config.touchMaxX));
        touchMaxXField.setChangedListener(text -> {
            try { config.touchMaxX = Integer.parseInt(text); } catch (NumberFormatException ignored) {}
        });
        this.addDrawableChild(touchMaxXField);
        
        // Touch Max Y
        touchMaxYField = new TextFieldWidget(this.textRenderer, x + 110, y, fieldWidth, 20, Text.literal("Max Y"));
        touchMaxYField.setText(String.valueOf(config.touchMaxY));
        touchMaxYField.setChangedListener(text -> {
            try { config.touchMaxY = Integer.parseInt(text); } catch (NumberFormatException ignored) {}
        });
        this.addDrawableChild(touchMaxYField);
        
        updateFieldsEnabled();
    }
    
    private void initMappingTab(int x, int y, int width) {
        int fieldWidth = 100;
        
        y += 10; // Add some top padding
        
        // Mapping Mode Dropdown
        this.addDrawableChild(CyclingButtonWidget.<TouchConfig.MappingMode>builder(mode -> Text.literal(mode.getDisplayName()))
            .values(TouchConfig.MappingMode.AUTO, TouchConfig.MappingMode.NORMAL, 
                   TouchConfig.MappingMode.ROTATE_90, TouchConfig.MappingMode.ROTATE_180, 
                   TouchConfig.MappingMode.ROTATE_270, TouchConfig.MappingMode.CUSTOM)
            .initially(config.mappingMode)
            .build(x, y, fieldWidth*2, 20, Text.literal("Coordinate Mapping"),
                (button, value) -> {
                    config.mappingMode = value;
                    init(); // Reinitialize to show/hide custom controls
                }));
        
        y += 35;
        
        // Custom mapping controls (only show if CUSTOM mode)
        if (config.mappingMode == TouchConfig.MappingMode.CUSTOM) {
            this.addDrawableChild(CyclingButtonWidget.onOffBuilder(Text.literal("YES"), Text.literal("NO"))
                .initially(config.swapXY)
                .build(x, y, fieldWidth*2, 20, Text.literal("Swap X ↔ Y"),
                    (button, value) -> config.swapXY = value));
            y += 30;
            
            this.addDrawableChild(CyclingButtonWidget.onOffBuilder(Text.literal("YES"), Text.literal("NO"))
                .initially(config.invertX)
                .build(x, y, fieldWidth, 20, Text.literal("Invert X"),
                    (button, value) -> config.invertX = value));
            
            this.addDrawableChild(CyclingButtonWidget.onOffBuilder(Text.literal("YES"), Text.literal("NO"))
                .initially(config.invertY)
                .build(x + 110, y, fieldWidth, 20, Text.literal("Invert Y"),
                    (button, value) -> config.invertY = value));
            y += 35;
        }
        
        // Calibration button
        this.addDrawableChild(ButtonWidget.builder(Text.literal("🎯 Auto-Calibrate Mapping"),
            button -> {
                if (client != null) {
                    client.setScreen(new TouchCalibrationScreen(this, config));
                }
            }).dimensions(x, y, fieldWidth*2, 20).build());
    }
    
    private void initSystemTab(int x, int y, int width) {
        int fieldWidth = 100;
        
        y += 10; // Add some top padding
        
        // System Scale Section
        this.addDrawableChild(CyclingButtonWidget.onOffBuilder(Text.literal("Auto"), Text.literal("Manual"))
            .initially(config.autoDetectSystemScale)
            .build(x, y, fieldWidth, 20, Text.literal("System Scale"),
                (button, value) -> {
                    config.autoDetectSystemScale = value;
                    updateFieldsEnabled();
                }));
        
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Detect Now"), button -> {
            if (client != null) {
                config.autoDetectSystemScale = true;
                config.manualSystemScale = config.getEffectiveSystemScale(client);
                systemScaleField.setText(String.format("%.2f", config.manualSystemScale));
            }
        }).dimensions(x + fieldWidth + 10, y, fieldWidth, 20).build());
        
        y += 35;
        
        // Manual scale field
        systemScaleField = new TextFieldWidget(this.textRenderer, x, y, fieldWidth, 20, Text.literal("Scale"));
        systemScaleField.setText(String.format("%.2f", config.manualSystemScale));
        systemScaleField.setChangedListener(text -> {
            try { config.manualSystemScale = Double.parseDouble(text); } catch (NumberFormatException ignored) {}
        });
        this.addDrawableChild(systemScaleField);
        
        updateFieldsEnabled();
    }
    
    private void updateFieldsEnabled() {
        if (touchMaxXField != null) touchMaxXField.active = !config.autoDetectTouchResolution;
        if (touchMaxYField != null) touchMaxYField.active = !config.autoDetectTouchResolution;
        if (screenWidthField != null) screenWidthField.active = !config.autoDetectScreenResolution;
        if (screenHeightField != null) screenHeightField.active = !config.autoDetectScreenResolution;
        if (systemScaleField != null) systemScaleField.active = !config.autoDetectSystemScale;
    }
    
    private void detectTouchResolution() {
        config.touchMaxX = 1599;
        config.touchMaxY = 2559;
    }
    
    private void detectScreenResolution() {
        if (client != null) {
            config.screenWidth = client.getWindow().getFramebufferWidth();
            config.screenHeight = client.getWindow().getFramebufferHeight();
        }
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        
        // Draw title
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 15, 0xFFFFFF);
        
        // Draw tab panel background
        int tabBarX = this.width - 175;
        int tabBarY = 45;
        int tabBarWidth = 170;
        int tabBarHeight = (Tab.values().length * 34) + 10;
        
        // Semi-transparent background for tab area
        context.fill(tabBarX - 5, tabBarY - 5, tabBarX + tabBarWidth + 5, tabBarY + tabBarHeight + 5, 0x88000000);
        
        // Draw content area background
        int contentX = 15;
        int contentY = 50;
        int contentWidth = this.width - 210;
        int contentHeight = this.height - 100;
        
        context.fill(contentX, contentY, contentX + contentWidth, contentY + contentHeight, 0x44000000);
        
        // Draw section title
        String sectionTitle = currentTab.name + " Settings";
        context.drawTextWithShadow(this.textRenderer, sectionTitle, contentX + 10, contentY + 10, 0xFFFFFF);
        
        // Draw section-specific labels
        int labelX = contentX + 10;
        int labelY = 90;
        
        switch (currentTab) {
            case DISPLAY:
                context.drawTextWithShadow(this.textRenderer, "Screen Resolution Mode:", labelX, labelY - 20, 0xAAAAAA);
                context.drawTextWithShadow(this.textRenderer, "Screen Dimensions:", labelX, labelY + 20, 0xAAAAAA);
                break;
            case TOUCH:
                context.drawTextWithShadow(this.textRenderer, "Visual Debugging:", labelX, labelY - 20, 0xAAAAAA);
                context.drawTextWithShadow(this.textRenderer, "Touch Device:", labelX, labelY + 20, 0xAAAAAA);
                context.drawTextWithShadow(this.textRenderer, "Touch Resolution Mode:", labelX, labelY + 60, 0xAAAAAA);
                context.drawTextWithShadow(this.textRenderer, "Touch Maximum Values:", labelX, labelY + 100, 0xAAAAAA);
                break;
            case MAPPING:
                context.drawTextWithShadow(this.textRenderer, "Mapping Preset:", labelX, labelY - 20, 0xAAAAAA);
                if (config.mappingMode == TouchConfig.MappingMode.CUSTOM) {
                    context.drawTextWithShadow(this.textRenderer, "Custom Settings:", labelX, labelY + 20, 0xAAAAAA);
                }
                break;
            case SYSTEM:
                context.drawTextWithShadow(this.textRenderer, "System Scale Mode:", labelX, labelY - 20, 0xAAAAAA);
                context.drawTextWithShadow(this.textRenderer, "Manual Scale Value:", labelX, labelY + 20, 0xAAAAAA);
                break;
        }
        
        // Help text
        String helpText = "💡 Tip: Changes are saved when you click 'Done'";
        context.drawCenteredTextWithShadow(this.textRenderer, 
            Text.literal(helpText), 
            (contentWidth / 2) + contentX, this.height - 50, 0x888888);
    }
    
    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }
}
