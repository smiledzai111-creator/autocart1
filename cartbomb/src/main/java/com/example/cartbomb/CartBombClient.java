package com.example.cartbomb;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public class CartBombClient implements ClientModInitializer {

    // Thoi gian theo tick (20 tick = 1 giay)
    private static final int T_PLACE_RAIL = 0;
    private static final int T_MINECART   = 4;
    private static final int T_DRAW_BOW   = 8;
    private static final int T_RELEASE    = 32;
    private static final int T_FINISH     = 38;

    // Mixin doc bien nay de ep game thay chuot phai dang duoc giu
    public static volatile boolean holdUse = false;

    private static KeyMapping triggerKey;

    private boolean running = false;
    private int tick = 0;
    private int originalSlot, bowSlot, railSlot, cartSlot;
    private BlockPos railPos;

    @Override
    public void onInitializeClient() {
        triggerKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.cartbomb.trigger",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                KeyMapping.Category.MISC));

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null || mc.level == null) {
            running = false;
            holdUse = false;
            return;
        }

        if (!running) {
            holdUse = false;
            while (triggerKey.consumeClick()) {
                start(mc, player);
            }
            return;
        }

        if (tick == T_PLACE_RAIL) {
            player.getInventory().setSelectedSlot(railSlot);
            BlockHitResult hit = (BlockHitResult) mc.hitResult;
            BlockPos below = hit.getBlockPos();
            railPos = below.above();
            BlockHitResult place = new BlockHitResult(
                    Vec3.atBottomCenterOf(railPos), Direction.UP, below, false);
            mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, place);
            player.swing(InteractionHand.MAIN_HAND);
        }

        if (tick == T_MINECART) {
            player.getInventory().setSelectedSlot(cartSlot);
            BlockHitResult place = new BlockHitResult(
                    Vec3.atCenterOf(railPos), Direction.UP, railPos, false);
            mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, place);
            player.swing(InteractionHand.MAIN_HAND);
        }

        if (tick == T_DRAW_BOW) {
            player.getInventory().setSelectedSlot(bowSlot);
        }

        if (tick > T_DRAW_BOW && tick < T_RELEASE) {
            holdUse = true;
            if (!player.isUsingItem()) {
                mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
            }
        }

        if (tick == T_RELEASE) {
            int arrows = 0;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack s = player.getInventory().getItem(i);
                if (s.is(Items.ARROW) || s.is(Items.SPECTRAL_ARROW) || s.is(Items.TIPPED_ARROW)) {
                    arrows += s.getCount();
                }
            }
            player.displayClientMessage(Component.literal(
                    "§e[CartBomb] Dang keo cung: " + player.isUsingItem()
                    + " | tick keo: " + player.getTicksUsingItem()
                    + " | mui ten: " + arrows), true);
            holdUse = false;
            if (player.isUsingItem()) {
                mc.gameMode.releaseUsingItem(player);
            }
        }

        if (tick >= T_FINISH) {
            holdUse = false;
            player.getInventory().setSelectedSlot(originalSlot);
            running = false;
            return;
        }

        tick++;
    }

    private void start(Minecraft mc, LocalPlayer player) {
        bowSlot = railSlot = cartSlot = -1;

        for (int i = 0; i < 9; i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (bowSlot < 0 && s.is(Items.BOW)) bowSlot = i;
            else if (railSlot < 0 && s.is(Items.RAIL)) railSlot = i;
            else if (cartSlot < 0 && (s.is(Items.TNT_MINECART) || s.is(Items.MINECART))) cartSlot = i;
        }

        if (bowSlot < 0 || railSlot < 0 || cartSlot < 0) {
            player.displayClientMessage(Component.literal(
                    "§c[CartBomb] Thieu vat pham trong hotbar: "
                    + (bowSlot < 0 ? "Cung " : "")
                    + (railSlot < 0 ? "Duong ray " : "")
                    + (cartSlot < 0 ? "Minecart " : "")), true);
            return;
        }

        if (!(mc.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) {
            player.displayClientMessage(Component.literal(
                    "§c[CartBomb] Hay nhin vao mot block truoc khi nhan phim."), true);
            return;
        }

        originalSlot = player.getInventory().getSelectedSlot();
        tick = 0;
        running = true;
    }
}
