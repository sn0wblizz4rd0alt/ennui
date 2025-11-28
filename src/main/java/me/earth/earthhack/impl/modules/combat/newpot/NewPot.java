package me.earth.earthhack.impl.modules.combat.newpot;

import me.earth.earthhack.api.module.Module;
import me.earth.earthhack.api.module.util.Category;
import me.earth.earthhack.api.setting.Setting;
import me.earth.earthhack.api.setting.settings.*;
import me.earth.earthhack.impl.util.client.SimpleData;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.CPacketHeldItemChange;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.network.play.client.CPacketPlayerTryUseItem;
import net.minecraft.potion.PotionEffect;
import net.minecraft.potion.PotionUtils;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class NewPot extends Module {
    private final Minecraft mc = Minecraft.getMinecraft();

    private final Setting<Boolean> healthPotion = register(new BooleanSetting("HealthPotion", true));
    private final Setting<Integer> healthThreshold = register(new NumberSetting<>("Health", 16, 0, 20));
    private final Setting<Integer> healthSlot = register(new NumberSetting<>("HealthSlot", 1, 1, 9));

    private final Setting<Boolean> speedPotion = register(new BooleanSetting("SpeedPotion", false));
    private final Setting<Integer> speedThreshold = register(new NumberSetting<>("SpeedSeconds", 50, 1, 100)); // seconds left to refresh speed
    private final Setting<Integer> speedSlot = register(new NumberSetting<>("SpeedSlot", 2, 1, 9));

    private final Setting<Integer> healthDelay = register(new NumberSetting<>("HealthDelay", 91, 0, 1000));
    private final Setting<Integer> SpeedDelay = register(new NumberSetting<>("SpeedDelay", 3000, 0, 80000));


    private final Setting<Boolean> debug = register(new BooleanSetting("Debug", true));
    private final Setting<Boolean> packetThrow = register(new BooleanSetting("PacketThrow", true));
    private final Setting<Boolean> safeThrow = register(new BooleanSetting("SafeThrow", true));

    private long lastHealthPot = 0;
    private long lastSpeedPot = 0;

    public NewPot() {
        super("AutoPotRewrite", Category.Combat);
        this.setData(new SimpleData(this, "Automatically throws health and speed potions"));
    }

    @Override
    protected void onEnable() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    protected void onDisable() {
        MinecraftForge.EVENT_BUS.unregister(this);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (mc.player == null || mc.world == null || event.phase != TickEvent.Phase.START) return;
        //if (mc.currentScreen != null) return;

        if (healthPotion.getValue() && shouldThrowHealth()) {
            throwPotion(true);
        }

        if (speedPotion.getValue() && shouldThrowSpeed()) {
            throwPotion(false);
        }
    }

    private boolean shouldThrowHealth() {
        if (System.currentTimeMillis() - lastHealthPot < healthDelay.getValue()) return false;

        float health = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        return healthPotion.getValue() && health <= healthThreshold.getValue()
                && (mc.player.onGround || mc.player.isInWater())
                && (!safeThrow.getValue() || checkSafePosition());
    }

    private boolean shouldThrowSpeed() {
        if (System.currentTimeMillis() - lastSpeedPot < SpeedDelay.getValue()) return false;

        if (!speedPotion.getValue()) return false;

        PotionEffect speedEffect = mc.player.getActivePotionEffect(MobEffects.SPEED);
        if (speedEffect != null && speedEffect.getDuration() > speedThreshold.getValue() * 20) {
            return false;
        }

        return (mc.player.onGround || mc.player.isInWater())
                && (!safeThrow.getValue() || checkSafePosition());
    }

    private void throwPotion(boolean isHealth) {
        int slot = findPotionSlot(isHealth);
        if (slot == -1) {
            if (debug.getValue()) {
                System.out.println("AutoPot: No " + (isHealth ? "health" : "speed") + " potions found.");
            }
            return;
        }

        int originalSlot = mc.player.inventory.currentItem;

        int targetHotbar = (isHealth ? healthSlot.getValue() : speedSlot.getValue()) - 1;
        if (slot >= 9) {
            int windowId = mc.player.openContainer.windowId;
            mc.playerController.windowClick(windowId, slot, targetHotbar, ClickType.SWAP, mc.player);
            slot = targetHotbar;
        }

        float originalYaw = mc.player.rotationYaw;
        float originalPitch = mc.player.rotationPitch;
        mc.player.connection.sendPacket(new CPacketPlayer.Rotation(originalYaw, 90.0f, mc.player.onGround));

        mc.player.connection.sendPacket(new CPacketHeldItemChange(slot));
        mc.player.inventory.currentItem = slot;

        if (packetThrow.getValue()) {
            mc.player.connection.sendPacket(new CPacketPlayerTryUseItem(EnumHand.MAIN_HAND));
        } else {
            mc.playerController.processRightClick(mc.player, mc.world, EnumHand.MAIN_HAND);
        }

        mc.player.connection.sendPacket(new CPacketPlayer.Rotation(originalYaw, originalPitch, mc.player.onGround));

        mc.player.connection.sendPacket(new CPacketHeldItemChange(originalSlot));
        mc.player.inventory.currentItem = originalSlot;

        if (isHealth) {
            lastHealthPot = System.currentTimeMillis();
        } else {
            lastSpeedPot = System.currentTimeMillis();
        }

        if (debug.getValue()) {
            System.out.println("AutoPot: Threw " + (isHealth ? "health" : "speed") + " potion from slot " + slot);
        }
    }

    private int findPotionSlot(boolean isHealth) {

        for (int i = 0; i < 9; i++) {
            if (isValidPotion(mc.player.inventory.getStackInSlot(i), isHealth)) return i;
        }

        for (int i = 9; i < 36; i++) {
            if (isValidPotion(mc.player.inventory.getStackInSlot(i), isHealth)) return i;
        }
        return -1;
    }

    private boolean isValidPotion(ItemStack stack, boolean isHealth) {
        if (stack.getItem() != Items.SPLASH_POTION) return false;
        for (PotionEffect effect : PotionUtils.getEffectsFromStack(stack)) {
            if (isHealth && effect.getPotion() == MobEffects.INSTANT_HEALTH) return true;
            if (!isHealth && effect.getPotion() == MobEffects.SPEED) return true;
        }
        return false;
    }

    private boolean checkSafePosition() {
        Vec3d playerPos = mc.player.getPositionVector();
        AxisAlignedBB potionBox = new AxisAlignedBB(
                playerPos.x - 0.3, playerPos.y, playerPos.z - 0.3,
                playerPos.x + 0.3, playerPos.y + 2.0, playerPos.z + 0.3
        );

        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y <= 2; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos pos = new BlockPos(playerPos).add(x, y, z);
                    if (!mc.world.isAirBlock(pos)) {
                        AxisAlignedBB bb = mc.world.getBlockState(pos).getCollisionBoundingBox(mc.world, pos);
                        if (bb != null && potionBox.intersects(bb.offset(pos))) {
                            if (debug.getValue()) {
                                System.out.println("AutoPot: Unsafe due to block at " + pos);
                            }
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }
}