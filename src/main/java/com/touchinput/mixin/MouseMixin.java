package com.touchinput.mixin;

import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Mouse.class)
public interface MouseMixin {
    @Invoker("onCursorPos")
    void invokeOnCursorPos(long window, double x, double y);
}
