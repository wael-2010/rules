package cid.rules;

import net.minecraft.server.MinecraftServer;

public abstract class Rule {
    public abstract String name();

    public void registerEvents() {}

    public void onStart(MinecraftServer server) {}
    public void onTick(MinecraftServer server) {}
    public void onEnd(MinecraftServer server) {}

    public boolean isActive() {
        return Manager.current() == this;
    }
}