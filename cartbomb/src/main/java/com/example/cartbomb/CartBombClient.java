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
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public class CartBombClient implements ClientModInitializer {

    // Thoi gian theo tick (20 tick = 1 giay). Tong chuoi phai < 10 tick (0.5s)
    private static final int T_PLACE_RAIL = 0;          // lay cart (neu can) + dat ray
    private static final int T_MINECART   = 1;          // dat minecart (neu server lag thi doi thanh 2)
    private static final int T_START_DRAW = T_MINECART; // chon cung + keo cung cung tick voi dat cart
    private static final int DRAW_TICKS   = 3;          // 3 tick = nguong toi thieu de cung ban ra
    private static final int T_TIMEOUT    = 8;          // du phong: toi da tick 8 la tha cung
    private static final int AFTER_SHOT   = 2;          // so tick sau khi tha thi ket thuc (<= tick 10)

    // Mixin doc bien nay de ep game thay chuot phai dang duoc giu
    public static volatile boolean holdUse = false;

    private static KeyMapping triggerKey;

    private boolean running = false;
    private boolean released = false;
    private int releaseTick = 0;
    private int tick = 0;
    private int originalSlot, bowSlot, railSlot, cartSlot;
    private BlockPos railPos;
    private BlockHitResult startHit;

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
            boolean pressed = false;
            while (triggerKey.consumeClick()) {
                pressed = true;
            }
            if (pressed && mc.screen == null) {
                start(mc, player);
            }
            return;
        }

        if (mc.screen != null || !player.isAlive()) {
            abort(player, null);
            return;
        }

        // ---- Tick 0: dat ray ----
        if (tick == T_PLACE_RAIL) {
            player.getInventory().setSelectedSlot(railSlot);
            railPos = startHit.getBlockPos().above();
            BlockPos below = startHit.getBlockPos();
            BlockHitResult place = new BlockHitResult(
                    Vec3.atBottomCenterOf(railPos), Direction.UP, below, false);
            mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, place);
            player.swing(InteractionHand.MAIN_HAND);
        }

        // ---- Tick 1: dat minecart + chon cung + bat dau keo (cung tick) ----
        if (tick == T_MINECART) {
            if (!mc.level.getBlockState(railPos).is(BlockTags.RAILS)) {
                abort(player, "§c[CartBomb] Khong dat duoc ray, huy.");
                return;
            }
            player.getInventory().setSelectedSlot(cartSlot);
            BlockHitResult place = new BlockHitResult(
                    Vec3.atCenterOf(railPos), Direction.UP, railPos, false);
            mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, place);
            player.swing(InteractionHand.MAIN_HAND);

            // Chon cung ngay lap tuc de bat dau keo
            player.getInventory().setSelectedSlot(bowSlot);
        }

        if (tick >= T_START_DRAW && !released) {
            boolean ready = player.isUsingItem() && player.getTicksUsingItem() >= DRAW_TICKS;
            boolean timeout = tick >= T_TIMEOUT;

            if (ready || timeout) {
                player.displayClientMessage(Component.literal(
                        (ready ? "§a[CartBomb] Ban! " : "§e[CartBomb] Het gio, tha cung. ")
                        + "(tick keo: " + player.getTicksUsingItem() + ")"), true);
                holdUse = false;
                if (player.isUsingItem()) {
                    mc.gameMode.releaseUsingItem(player);
                }
                released = true;
                releaseTick = tick;
            } else {
                holdUse = true;
                if (!player.isUsingItem()) {
                    mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
                }
            }
        }

        if (released && tick >= releaseTick + AFTER_SHOT) {
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
            else if (cartSlot < 0 && s.is(Items.TNT_MINECART)) cartSlot = i;
        }

        // Cung va ray bat buoc phai co trong hotbar
        if (bowSlot < 0 || railSlot < 0) {
            player.displayClientMessage(Component.literal(
                    "§c[CartBomb] Thieu vat pham trong hotbar: "
                    + (bowSlot < 0 ? "Cung " : "")
                    + (railSlot < 0 ? "Duong ray " : "")), true);
            return;
        }

        // Neu hotbar khong co TNT minecart, kiem tra xem trong kho co khong
        if (cartSlot < 0 && findCartInInventory(player) < 0) {
            player.displayClientMessage(Component.literal(
                    "§c[CartBomb] Khong co TNT Minecart trong hotbar lan kho."), true);
            return;
        }

        ItemStack bow = player.getInventory().getItem(bowSlot);

        if (player.getProjectile(bow).isEmpty()) {
            player.displayClientMessage(Component.literal(
                    "§c[CartBomb] Khong co mui ten trong inventory."), true);
            return;
        }

        var flame = mc.level.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.FLAME);
        if (EnchantmentHelper.getItemEnchantmentLevel(flame, bow) <= 0) {
            player.displayClientMessage(Component.literal(
                    "§c[CartBomb] Cung khong co Flame, TNT minecart se khong no."), true);
            return;
        }

        if (!(mc.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) {
            player.displayClientMessage(Component.literal(
                    "§c[CartBomb] Hay nhin vao mot block truoc khi nhan phim."), true);
            return;
        }

        BlockPos below = hit.getBlockPos();
        BlockPos above = below.above();
        BlockState aboveState = mc.level.getBlockState(above);
        if (!aboveState.canBeReplaced()
                || !mc.level.getBlockState(below).isFaceSturdy(mc.level, below, Direction.UP)) {
            player.displayClientMessage(Component.literal(
                    "§c[CartBomb] Khong the dat ray o vi tri nay."), true);
            return;
        }

        // Moi thu hop le -> luc nay moi tu dong lay cart tu kho (neu can)
        if (cartSlot < 0) {
            cartSlot = fetchCartToHotbar(mc, player);
            if (cartSlot < 0) {
                player.displayClientMessage(Component.literal(
                        "§c[CartBomb] Khong chuyen duoc TNT Minecart vao hotbar."), true);
                return;
            }
        }

        startHit = hit;
        originalSlot = player.getInventory().getSelectedSlot();
        tick = 0;
        released = false;
        releaseTick = 0;
        running = true;
    }

    /** Tim TNT minecart trong kho chinh (o 9..35). Tra ve -1 neu khong co. */
    private int findCartInInventory(LocalPlayer player) {
        for (int i = 9; i < 36; i++) {
            if (player.getInventory().getItem(i).is(Items.TNT_MINECART)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Dung 1 click SWAP (1 packet, khong can mo kho) de dua TNT minecart vao hotbar.
     * Uu tien o trong; neu hotbar day thi doi vao o khong chua cung/ray.
     * Tra ve so o hotbar chua cart, hoac -1 neu that bai.
     */
    private int fetchCartToHotbar(Minecraft mc, LocalPlayer player) {
        int invSlot = findCartInInventory(player);
        if (invSlot < 0) return -1;

        int target = -1;
        for (int h = 0; h < 9; h++) {
            if (player.getInventory().getItem(h).isEmpty()) {
                target = h;
                break;
            }
        }
        if (target < 0) {
            for (int h = 0; h < 9; h++) {
                if (h != bowSlot && h != railSlot) {
                    target = h;
                    break;
                }
            }
        }
        if (target < 0) return -1;

        // invSlot 9..35 trung voi chi so slot trong inventoryMenu; button = o hotbar (0..8)
        mc.gameMode.handleInventoryMouseClick(
                player.inventoryMenu.containerId, invSlot, target, ClickType.SWAP, player);

        return player.getInventory().getItem(target).is(Items.TNT_MINECART) ? target : -1;
    }

    private void abort(LocalPlayer player, String message) {
        holdUse = false;
        if (player.isUsingItem()) {
            player.stopUsingItem();
        }
        player.getInventory().setSelectedSlot(originalSlot);
        running = false;
        if (message != null) {
            player.displayClientMessage(Component.literal(message), true);
        }
    }
}
