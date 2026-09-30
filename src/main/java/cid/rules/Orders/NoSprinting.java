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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class NoSprinting extends Rule{


    private static int WARNING_TICKS = 3 * 20;

    // the sprint number that kills you (3 = 2 free sprints, die on the 3rd)
    private static final int MAX_STRIKES = 3;

    private static final int STRIKE_TICKS = 20;
    private static boolean IGNORE_CREATIVE = false;
    private static final int SLOWNESS_TICKS = 3 * 20;


    private final Map<UUID, Integer> strikes = new HashMap<>();
    private final Map<UUID, Integer> cooldowns = new HashMap<>();
    private int ticksSinceStart = 0;

    @Override
    public String name() {
        return "No Sprinting!";
    }

    @Override
    public void registerEvents() {
        ServerPlayConnectionEvents.DISCONNECT.register(((handler, server) -> {
            UUID id = handler.getPlayer().getUuid();
            strikes.remove(id);
            cooldowns.remove(id);
        }));
    }

    @Override
    public void onStart(MinecraftServer server) {
        strikes.clear();
        cooldowns.clear();
        ticksSinceStart = 0;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            showStartTitle(player);
            player.sendMessage(Text.literal("§eYou have " + (WARNING_TICKS / 20)
                    + " seconds before sprinting is not allowed. " ), false);
        }
    }

    @Override
    public void onTick(MinecraftServer server) {
        ticksSinceStart++;
        tickCooldowns();

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (shouldIgnore(player))
                continue;
            if(!player.isSprinting())
                continue;

            if (isGracePeriod ()) {
                player.setSprinting(false);
                if (ticksSinceStart % 10 == 0) {
                    showWarning(player, "§cDon't sprint! Grace period ends in " + graceSecondsLeft() + "s");
                }
                continue;
            }

            if (cooldowns.containsKey(player.getUuid())) {
                player.setSprinting(false);
                continue;
            }
            handleStrike(player);
        }
    }

    @Override
    public void onEnd(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            showWarning(player, "§aYou can sprint again.");
        }
        strikes.clear();
        cooldowns.clear();
        ticksSinceStart = 0;
    }
    private void handleStrike(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        int count = strikes.merge(id, 1 , Integer::sum);

        if (count >= MAX_STRIKES) {
            strikes.remove(id);
            cooldowns.remove(id);
            playDeathEffects(player);
            BrokeRules(player);
            return;
        }

        int left = MAX_STRIKES - count;   // sprints left until the killing one
        int allowed = MAX_STRIKES - 1;    // free sprints you get
        player.setSprinting(false);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, SLOWNESS_TICKS, 1));
        showWarning(player, "§cStrike " + count + "/" + allowed
                + (left == 1 ? "! Next sprint kills you!"
                : "! " + (left - 1) + " more sprint" + (left - 1 == 1 ? "" : "s") + " allowed."));
        playWarningSound(player);
        cooldowns.put(id, STRIKE_TICKS);

    }
    private boolean isGracePeriod() {

        return ticksSinceStart <= WARNING_TICKS;
    }

    private int graceSecondsLeft() {
        return Math.max(0, (WARNING_TICKS - ticksSinceStart) / 20 + 1);

    }

    private boolean shouldIgnore(ServerPlayerEntity player) {
        if (!player.isAlive() || player.isSpectator())
            return true;
        return IGNORE_CREATIVE && player.isCreative();

    }

    private void tickCooldowns() {
        cooldowns.replaceAll((id, ticks) -> ticks - 1);
        cooldowns.values().removeIf(ticks -> ticks <= 0);
    }

    private void showWarning(ServerPlayerEntity player, String message) {
        player.networkHandler.sendPacket(new TitleFadeS2CPacket(0, 30, 10));
        player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("")));
        player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal(message)));
    }

    private void showStartTitle(ServerPlayerEntity player) {
        player.networkHandler.sendPacket(new TitleFadeS2CPacket(5, 40, 10));
        player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("§cNo Sprinting!")));
        player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal("§7Walk, don't run")));

    }

    private void playDeathEffects(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        world.spawnParticles(ParticleTypes.ANGRY_VILLAGER, player.getX(), player.getY() + 1.0 , player.getZ(), 15,0.4
                , 0.6, 0.4 , 0.05);

    }

    private void playWarningSound(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_VILLAGER_NO,
                SoundCategory.PLAYERS, 1.0f , 1.0f);
    }
}