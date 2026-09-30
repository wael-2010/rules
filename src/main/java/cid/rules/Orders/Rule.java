package cid.rules.Orders;

import cid.rules.Manager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public abstract class Rule {
    public abstract String name();

    public void registerEvents() {}

    public void onStart(MinecraftServer server) {}
    public void onTick(MinecraftServer server) {}
    public void onEnd(MinecraftServer server) {}

    public boolean isActive() {
        return Manager.current() == this;
    }

    protected void BrokeRules(ServerPlayerEntity player) {
        if (player.isSpectator() || !player.isAlive())
            return;

        MinecraftServer server = player.getServer();
        if (server != null) {
            server.getPlayerManager().broadcast(
                    Text.literal("§c" + player.getName().getString() + " broke the rule: " + name()), false);
        }
        player.kill(); }
}