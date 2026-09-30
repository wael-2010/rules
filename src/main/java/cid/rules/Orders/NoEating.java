package cid.rules.Orders;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

public class NoEating extends Rule {

    @Override
    public String name() {
        return "No Eating!";
    }

    @Override
    public void onTick(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.isUsingItem() && p.getActiveItem().contains(DataComponentTypes.FOOD)) {
                BrokeRules(p);
            }
        }
    }
}
