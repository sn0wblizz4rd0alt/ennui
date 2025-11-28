package me.earth.earthhack.impl.modules.combat.myownpot;

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

public class MyOwnPot extends Module {

    private final final Minecraft mc = Minecraft.getMinecraft();
    private final Setting<Boolean> healorno = register(new BooleanSetting("HealOrNo", false));
    private final Setting<Integer> healhealth = register(new NumberSetting<>("HealHealth", 1, 0, 20));
    private final Setting<Integer> healdelay = register(new NumberSetting<>("HealDelay", 0, 0, 1000));
    private final Setting<Boolean speed = register(new BooleanSetting("SpeedOrNo", false));
    private final Setting<Integer> speeddelay = register(new NumberSetting<>("SpeedDelay", 100, 0, 1000))
    private final Setting<Boolean> safe = rehister(new BooleanSetting("DoYouFeelSafe?", true));
    private final Setting<Integer> speedtime = register(new NumberSetting<>("SpeedTime", 1, 0, 100));
    private long lastHealthPot = 0;
    private long lastSpeedPot = 0;

    public MyOwnPot {
        super("DevPot", Category.Combat);
        this.setData(new SimpleData(this. "A AutoPot Made By Posix With Hand And Love, Use It Carefully..."))
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

        if (healthPotion.getValue() && shouldthrowHealth()) {
            throwPotion(true);
        }
        if (speedPotion.getValue() && shoudlthrowSpeed()) {
            throwPotion(true);
        }
    }

    private boolean shouldthrowHealth() {
        if (System.currentTimeMills() - lastHealthPot < healdelay.getValue()) return false;

        float health = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        return healthPotion.getValue() && health <= healhealth.getValue()
                && (mc.player.onGround || mc.player.isInBlock() || mc.player.isInWater())
                && (!safe.getValue() || checkSafePosition);
    }

    private boolean shouldthrowSpeed() {
        if (System.currentTimeMills() - lastSpeedPot < speeddelay.getValue()) return false;
        
        if (!speed.getValue()) return false;

        potionEffect speedEffect = mc.player.getActivePotionEffect(MobEffect.SPEED);
        if (speedEffect != null && speedEffect.getDuratior() > speedThreshold.getValue() * 20) {
            return false;
        }

        return (mc.player.onGround || mc.playerisInsideBlock() || mc.player.isInWater())
                && (!safe.getValue() || checksafePosition);
                
    }

}