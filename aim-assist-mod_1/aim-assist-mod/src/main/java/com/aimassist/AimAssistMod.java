package com.aimassist;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class AimAssistMod implements ClientModInitializer {

    public static final String MOD_ID = "aimassist";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // ── Настройки ──────────────────────────────────────────────
    /** Радиус поиска целей (блоки) */
    public static final double SEARCH_RADIUS = 6.0;

    /** Максимальный угол от прицела для захвата цели (градусы) */
    public static final float MAX_ANGLE = 30.0f;

    /** Плавность сведения: чем меньше — тем плавнее (0.0–1.0) */
    public static final float SMOOTHING = 0.08f;
    // ───────────────────────────────────────────────────────────

    public static boolean aimAssistEnabled = true;

    private static KeyBinding toggleKey;

    @Override
    public void onInitializeClient() {
        LOGGER.info("[AimAssist] Мод загружен! Нажми V чтобы вкл/выкл.");

        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.aimassist.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                "category.aimassist"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;

        // Переключение клавишей
        while (toggleKey.wasPressed()) {
            aimAssistEnabled = !aimAssistEnabled;
            client.player.sendMessage(
                    Text.literal("[AimAssist] " + (aimAssistEnabled ? "§aВключён" : "§cВыключён")),
                    true
            );
        }

        if (!aimAssistEnabled) return;

        // Работаем только при зажатой ЛКМ (атака)
        if (!client.options.attackKey.isPressed()) return;

        LivingEntity target = findBestTarget(client);
        if (target == null) return;

        smoothAimAt(client, target);
    }

    /**
     * Ищет ближайшую живую цель в конусе перед игроком.
     */
    private LivingEntity findBestTarget(MinecraftClient client) {
        PlayerEntity player = client.player;
        Vec3d eyes = player.getEyePos();

        Box searchBox = new Box(
                eyes.x - SEARCH_RADIUS, eyes.y - SEARCH_RADIUS, eyes.z - SEARCH_RADIUS,
                eyes.x + SEARCH_RADIUS, eyes.y + SEARCH_RADIUS, eyes.z + SEARCH_RADIUS
        );

        List<LivingEntity> entities = client.world.getEntitiesByClass(
                LivingEntity.class, searchBox,
                e -> e != player && !e.isDead() && !e.isSpectator()
        );

        LivingEntity best = null;
        float bestAngle = MAX_ANGLE;

        for (LivingEntity entity : entities) {
            float angle = getAngleToEntity(client, entity);
            if (angle < bestAngle) {
                bestAngle = angle;
                best = entity;
            }
        }
        return best;
    }

    /**
     * Угол (в градусах) между направлением взгляда и вектором к центру цели.
     */
    private float getAngleToEntity(MinecraftClient client, LivingEntity entity) {
        PlayerEntity player = client.player;
        Vec3d eyes = player.getEyePos();
        Vec3d targetCenter = entity.getEyePos();

        Vec3d toTarget = targetCenter.subtract(eyes).normalize();

        float yaw   = (float) Math.toRadians(player.getYaw());
        float pitch = (float) Math.toRadians(player.getPitch());

        Vec3d lookVec = new Vec3d(
                -Math.sin(yaw) * Math.cos(pitch),
                -Math.sin(pitch),
                 Math.cos(yaw) * Math.cos(pitch)
        );

        double dot = MathHelper.clamp(lookVec.dotProduct(toTarget), -1.0, 1.0);
        return (float) Math.toDegrees(Math.acos(dot));
    }

    /**
     * Плавно поворачивает камеру игрока в сторону цели.
     */
    private void smoothAimAt(MinecraftClient client, LivingEntity target) {
        PlayerEntity player = client.player;
        Vec3d eyes    = player.getEyePos();
        Vec3d targetCenter = target.getEyePos();
        Vec3d delta   = targetCenter.subtract(eyes);

        // Целевые углы
        double horizontalDist = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float targetYaw   = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float targetPitch = (float) Math.toDegrees(-Math.atan2(delta.y, horizontalDist));

        float currentYaw   = player.getYaw();
        float currentPitch = player.getPitch();

        // Нормализуем разницу углов в диапазон [-180, 180]
        float dYaw = wrapDegrees(targetYaw - currentYaw);
        float dPitch = targetPitch - currentPitch;

        // Применяем интерполяцию (плавное наведение)
        float newYaw   = currentYaw   + dYaw   * SMOOTHING;
        float newPitch = MathHelper.clamp(currentPitch + dPitch * SMOOTHING, -90f, 90f);

        player.setYaw(newYaw);
        player.setPitch(newPitch);
    }

    private float wrapDegrees(float degrees) {
        degrees %= 360f;
        if (degrees >= 180f)  degrees -= 360f;
        if (degrees < -180f)  degrees += 360f;
        return degrees;
    }
}
