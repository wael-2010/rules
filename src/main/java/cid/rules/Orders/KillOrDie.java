package cid.rules.Orders;

import cid.rules.Manager;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
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

import java.util.*;

public class KillOrDie extends Rule{

    private static final int DURATION_TICKS = Manager.RULE_DURATION_TICKS;

    private static final int REQUIRED_KILLS = 1;
    private static final boolean ONLY_HOSTILE = false;

    private static final boolean IGNORE_CREATIVE = false;
    private static final int[] REMINDER_SECONDS = {20, 10 , 5 , 4 , 3 ,2 ,1 };

    private static final Map<UUID, Integer> kills = new HashMap<>();
    private static final Set<UUID> completed = new HashSet<>();

    private int ticksSinceStart = 0;

    @Override
    public String name() {
        return " Kill a Mob! OR DIE!!!";

    }

    @Override
    public void registerEvents() {
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register(((world, entity, killedEntity) -> {
            if (!isActive())
                return;

            if(!(entity instanceof ServerPlayerEntity player))

                return;

            if (!countAsMob(killedEntity))
                return;

            if (shouldIgnore(player))

                return;

            if (completed.contains(player.getUuid()))
                return;

            handleKill(player);

        }));

        ServerPlayConnectionEvents.DISCONNECT.register(((handler, server) -> {

            UUID id = handler.getPlayer().getUuid();
            kills.remove(id);
            completed.remove(id);

        }));
    }

    @Override
    public void onStart(MinecraftServer server) {
        clearAll();

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            showStartTitle(player);
            player.sendMessage(Text.literal("§eKill " + killsText(REQUIRED_KILLS)
                    + " before the time runs out or you die!"), false);
        }
    }

    @Override
    public void onTick(MinecraftServer server) {
        ticksSinceStart++;

        boolean everySecond = ticksSinceStart % 20 == 0;
        int secondsLeft = (DURATION_TICKS - ticksSinceStart) / 20;
        boolean lastTick = ticksSinceStart == DURATION_TICKS;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (shouldIgnore(player))
                continue;

            if (completed.contains(player.getUuid()))
                continue;

            if (lastTick) {
                failPlayer(player);
                continue;

            }

            if (everySecond && isReminderSecond(secondsLeft)) {
                int have = kills.getOrDefault(player.getUuid(), 0);
                showWarning(player, "§c" + secondsLeft + "s left! Kill " + killsText(REQUIRED_KILLS - have) + "!");
            }
        }
    }

    @Override
    public void onEnd(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {

            int count = kills.getOrDefault(player.getUuid(), 0);
            showWarning(player, "§aMob hunting is over.");
            player.sendMessage(Text.literal("§7You killed §e" + count + "§7 mob"
                    + (count == 1 ? "" : "s") + " during that rule."), false);

        }

        clearAll();
    }

    private void handleKill(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        int count = kills.merge(id, 1, Integer::sum);

        if (count >= REQUIRED_KILLS) {
            completed.add(id);
            playSuccessEffects(player);
            showWarning(player, "§aDone! You're safe for now .");
        return;
        }

        showWarning(player, "§eKills " + count + "/" + REQUIRED_KILLS + ", keep going!");
    }

    private void failPlayer(ServerPlayerEntity player) {
        playDeathEffects(player);
        BrokeRules(player);

    }

    private boolean countAsMob(LivingEntity killed) {
        if (!(killed instanceof MobEntity))
            return false;

        return !ONLY_HOSTILE || killed instanceof Monster;
    }

    private boolean isReminderSecond(int secondsLeft) {
        for (int s : REMINDER_SECONDS) {
            if (s == secondsLeft )
                return true;

        }
        return false;

    }

    private String killsText(int amount) {
        return amount == 1 ? "1 mob" : amount + " mobs";
    }
    private  boolean shouldIgnore(ServerPlayerEntity player) {
        if (!player.isAlive() || player.isSpectator())
            return true;
        return IGNORE_CREATIVE && player.isCreative();

    }

    private void clearAll() {
        kills.clear();
        completed.clear();
        ticksSinceStart = 0;
    }

    private void showWarning(ServerPlayerEntity player, String message) {
        player.networkHandler.sendPacket(new TitleFadeS2CPacket(0 , 30 , 10));
        player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("")));
        player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal(message)));

    }

    private void showStartTitle(ServerPlayerEntity player) {
        player.networkHandler.sendPacket(new TitleFadeS2CPacket(5, 40, 10));
        player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("§cKill a Mob!")));
        player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal("§7Kill " + killsText(REQUIRED_KILLS)
                + (ONLY_HOSTILE ? " (hostile)" : ""))));

    }

        private void playSuccessEffects (ServerPlayerEntity player){
            ServerWorld world = player.getServerWorld();
            world.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    15, 0.4, 0.6, 0.4, 0.05);
            world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP,
                    SoundCategory.PLAYERS, 1.0f, 1.0f);
        }

        private void playDeathEffects (ServerPlayerEntity player){
            ServerWorld world = player.getServerWorld();
            world.spawnParticles(ParticleTypes.ANGRY_VILLAGER,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    15, 0.4, 0.6, 0.4, 0.05);
            world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER,
                    SoundCategory.PLAYERS, 0.6f, 1.2f);
        }
    }