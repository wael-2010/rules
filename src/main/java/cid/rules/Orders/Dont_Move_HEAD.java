package cid.rules.Orders;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.apache.logging.log4j.core.jmx.Server;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Dont_Move_HEAD extends Rule{

    private static final int WARNING_TICKS = 3 * 20;
    private static final int MAX_STRIKES = 3;
    private static final int STRIKES_TICKS = 20;

    private static final float TOLERANCE_DEGREES = 3.0f;

    private static final boolean CHECK_YAW = true;
    private static final boolean CHECK_PITCH = true;
    private static final boolean IGNORE_CREATIVE = false;

    private static int SLOWNESS_TICKS = 3 * 20;

    private final Map<UUID, Integer> strikes = new HashMap<>();
    private final Map<UUID, Integer> cooldowns = new HashMap<>();
    private final Map<UUID, float[]> savedLook = new HashMap<>();
    private final Map<UUID, Integer> totalMoves = new HashMap<>();

    private int ticksSinceStart = 0;

    @Override
    public String name() {

        return "Don't Move Your Head! ";

    }

    @Override
    public void registerEvents() {

        ServerPlayConnectionEvents.DISCONNECT.register(((handler, server) -> {
            UUID id = handler.getPlayer().getUuid();
            strikes.remove(id);
            cooldowns.remove(id);
            savedLook.remove(id);
            totalMoves.remove(id);

        }));
    }

    @Override
    public void onStart(MinecraftServer server) {
        clearAll();

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            saveLook(player);
            showStartTitle(player);
            player.sendMessage(Text.literal("§eYou have " + (WARNING_TICKS / 20)
                    + " seconds before moving your head is not allowed."), false);

        }

    }

    @Override
    public void onTick(MinecraftServer server) {
        ticksSinceStart++;
        tickCooldowns();

        for (ServerPlayerEntity player: server.getPlayerManager().getPlayerList()) {
            if (shouldIgnore(player))
                continue;

            UUID id = player.getUuid();

            float[] saved = savedLook.get(id);

            if (saved == null) {
                saveLook(player);
                continue;
            }

            if (!hasMovedHead(player, saved))

                continue;

            saveLook(player);
            totalMoves.merge(id, 1 , Integer::sum);

            if (isGracePeriod()) {
                showWarning(player, "§cDon't move your head! Grace period ends in " + graceSecondsLeft() + "s");

                continue;

            }

            if (cooldowns.containsKey(id))

                continue;

            handleStrike(player);

        }
    }

    @Override
    public void onEnd(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {

            int moves = totalMoves.getOrDefault(player.getUuid(), 0);
            showWarning(player, "§aYou can look around again.");
            player.sendMessage(Text.literal("§7You moved your head §e" + moves + "§7 time"
                    + (moves == 1 ? "" : "s") + " during that rule."), false);

        }

        clearAll();

    }

    private void handleStrike(ServerPlayerEntity player) {

        UUID id = player.getUuid();
        int count = strikes.merge(id, 1, Integer::sum);

        if (count >= MAX_STRIKES) {
            strikes.remove(id);
            cooldowns.remove(id);
            savedLook.remove(id);
            playDeathEffects(player);
            BrokeRules(player);

            return;

        }

        int left = MAX_STRIKES - count;

        int allowed = MAX_STRIKES - 1;

        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, SLOWNESS_TICKS, 1));

        showWarning(player, "§cStrike " + count + "/" + allowed
                + (left == 1 ? "! Next head movement kills you!"
                : "! " + (left - 1) + " more movement" + (left - 1 == 1 ? "" : "s") + " allowed."));

        playWarningSound(player, count);
        cooldowns.put(id, STRIKES_TICKS);

    }


    private void saveLook(ServerPlayerEntity player) {

        savedLook.put(player.getUuid(), new float[]{player.getYaw(), player.getPitch()});

    }
    private boolean hasMovedHead(ServerPlayerEntity player, float[] saved) {

        float yawDiff = Math.abs(MathHelper.wrapDegrees(player.getYaw() - saved[0]));

        float pitchDiff = Math.abs(player.getPitch() - saved[1]);

        boolean yawMoved = CHECK_YAW && yawDiff > TOLERANCE_DEGREES;
        boolean pitchMoved = CHECK_PITCH && pitchDiff > TOLERANCE_DEGREES;

        return yawMoved || pitchMoved;

    }

    private boolean isGracePeriod() {

        return ticksSinceStart <= WARNING_TICKS;

    }

    private int graceSecondsLeft() {

        return Math.max(0, (WARNING_TICKS - ticksSinceStart) / 20 + 1);

    }

    private boolean shouldIgnore(ServerPlayerEntity player) {

        if (!player.isAlive() || player.isSpectator())
            return  true;
        return IGNORE_CREATIVE && player.isCreative();

    }

    private void tickCooldowns() {

        cooldowns.replaceAll((id, ticks) -> ticks - 1);
        cooldowns.values().removeIf(ticks -> ticks <= 0);

    }
    private void clearAll() {
        strikes.clear();
        cooldowns.clear();
        savedLook.clear();;
        totalMoves.clear();
        ticksSinceStart = 0;

    }

    private void showWarning(ServerPlayerEntity player, String message) {

        player.networkHandler.sendPacket(new TitleFadeS2CPacket(0, 30 , 10));
        player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("")));
        player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal(message)));

    }

    private void showStartTitle(ServerPlayerEntity player) {

        player.networkHandler.sendPacket(new TitleFadeS2CPacket(5, 40 , 10));
        player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("§cDon't Move Your Head!")));
        player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal("§7Keep your eyes still")));

    }

    private void playWarningSound(ServerPlayerEntity player, int strike) {

        ServerWorld world = player.getServerWorld();

        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_VILLAGER_NO,
                SoundCategory.PLAYERS, 1.0f, 0.8f + strike * 0.2f);

    }

    private void playDeathEffects(ServerPlayerEntity player) {

        ServerWorld world = player.getServerWorld();
        world.spawnParticles(ParticleTypes.ANGRY_VILLAGER,
                player.getX(), player.getY() + 1.0, player.getZ(),
                15, 0.4 , 0.6 , 0.4 , 0.05);
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER,
                SoundCategory.PLAYERS, 0.6f , 1.2f);

    }


}