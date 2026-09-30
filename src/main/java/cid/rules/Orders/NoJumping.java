package cid.rules.Orders;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.particle.ParticleType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class NoJumping extends Rule{

    private static final int WARNING_TICKS = 3 * 20;
    private static final int MAX_STRIKES = 3;
    private static final int STRIKE_TICKS = 10;
    private static final boolean IGNORE_CREATIVE = false;
    private static final int NAUSEA_TICKS = 5 * 20;

    private final Map<UUID, Integer> strikes = new HashMap<>();
    private final Map<UUID, Integer> cooldowns = new HashMap<>();
    private final Map<UUID, Integer> lastJumps = new HashMap<>();
    private final Map<UUID, Integer> totalJumps = new HashMap<>();
    private int ticksSinceStart = 0;

    @Override
    public String name() {
        return " No Jumping!";
    }

    @Override
    public void registerEvents() {
        ServerPlayConnectionEvents.DISCONNECT.register(((handler, server) -> {
            UUID id = handler.getPlayer().getUuid();
            strikes.remove(id);
            cooldowns.remove(id);
            lastJumps.remove(id);
            totalJumps.remove(id);
        }));

    }

    @Override
    public void onStart(MinecraftServer server) {
        clearAll();

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            lastJumps.put(player.getUuid(), getJumpStat(player));
            showStartTitle(player);
            player.sendMessage(Text.literal("§eYou have " + (WARNING_TICKS / 20)
                    + " seconds before jumping is not allowed."), false);

        }


    }

    @Override
    public void onTick(MinecraftServer server) {
        ticksSinceStart++;
        tickCooldowns();

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (shouldIgnore(player))
                continue;

            UUID id = player.getUuid();
            int now = getJumpStat(player);
            Integer last = lastJumps.put(id, now);

            if (last == null || now <= last)
                continue;

            totalJumps.merge(id, now - last, Integer::sum);

            if (isGracePeriod()) {
                showWarning(player, "§cDon't jump! Grace period ends in " + graceSecondsLeft() + "s");
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
            int jumps = totalJumps.getOrDefault(player.getUuid(), 0);
            showWarning(player, "§aYou can jump again.");
            player.sendMessage(Text.literal("§7You jumped §e" + jumps + "§7 time"
                    + (jumps == 1 ? "" : "s") + " during that rule."), false);
        }
        clearAll();
    }
    private void handleStrike(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        int count = strikes.merge(id, 1 ,Integer::sum);

        if (count >= MAX_STRIKES) {
            strikes.remove(id);
            cooldowns.remove(id);
            playDeathEffects(player);
            BrokeRules(player);

            return;
        }

        int left = MAX_STRIKES - count;

        int allowed = MAX_STRIKES - 1;


        player.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, NAUSEA_TICKS, 0));
        showWarning(player, "§cStrike " + count + "/" + allowed + (left == 1 ? "! Next jump kills you!"
                : "! " + (left - 1) + " more jump" + (left - 1 == 1 ? "" : "s") + " allowed."));

        playWarningSound(player, count);
        cooldowns.put(id, STRIKE_TICKS);
    }

    private int getJumpStat(ServerPlayerEntity player) {

        return player.getStatHandler().getStat(Stats.CUSTOM, Stats.JUMP);

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

    private void clearAll() {
        strikes.clear();
        cooldowns.clear();
        lastJumps.clear();
        totalJumps.clear();
        ticksSinceStart = 0;

    }

    private void showWarning(ServerPlayerEntity player, String message) {

        player.networkHandler.sendPacket(new TitleFadeS2CPacket(0, 30 , 10));
        player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("")));
        player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal(message)));

    }

    private void showStartTitle(ServerPlayerEntity player) {

        player.networkHandler.sendPacket(new TitleFadeS2CPacket(5, 40 , 10));
        player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("§cNo Jumping!")));
        player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal("§7Keep your feet on the ground boi ")));

    }

    private void playWarningSound(ServerPlayerEntity player , int strike) {

        ServerWorld world = player.getServerWorld();
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.PLAYERS, 1.0f, 0.8f + strike * 0.2f);

    }


    private void playDeathEffects(ServerPlayerEntity player) {

        ServerWorld world = player.getServerWorld();
        world.spawnParticles(ParticleTypes.ANGRY_VILLAGER,
                player.getX(), player.getY() + 1.0 , player.getZ(), 15 , 0.5 , 0.5 , 0.5, 0.05);
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER,
                SoundCategory.PLAYERS, 0.5f, 1f);
    }


}
