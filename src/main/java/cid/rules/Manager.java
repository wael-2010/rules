package cid.rules;

import cid.rules.Orders.Rule;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Manager {
    public static final int RULE_DURATION_TICKS = 30 * 20; // 20 * 20 for 20 seconds

    private static final Random RANDOM = new Random();
    private static final List<Rule> RULES = new ArrayList<>();
    private static boolean running = false;
    private static Rule current = null;
    private static int ticksLeft = 0;

    public static void register(Rule rule) {
        RULES.add(rule);
        rule.registerEvents();
    }

    public static Rule current() { return running ? current : null; }
    public static boolean isRunning() { return running; }

    public static void start(MinecraftServer server) {
        if (RULES.isEmpty()) return;
        running = true;
        current = null;
        pickNewRule(server);
    }

    public static void stop(MinecraftServer server) {
        if (current != null) current.onEnd(server);
        running = false;
        current = null;
    }

    public static void tick(MinecraftServer server) {
        if (!running) return;

        current.onTick(server);
        ticksLeft--;

        if (ticksLeft % 20 == 0) {
            Text msg = Text.literal(current.name() + " (" + (ticksLeft / 20) + "s)");
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                p.sendMessage(msg, true);
            }
        }

        if (ticksLeft <= 0) pickNewRule(server);
    }

    private static void pickNewRule(MinecraftServer server) {
        if (current != null) current.onEnd(server);

        List<Rule> options = new ArrayList<>(RULES);
        if (current != null && options.size() > 1) options.remove(current);
        current = options.get(RANDOM.nextInt(options.size()));
        ticksLeft = RULE_DURATION_TICKS;

        current.onStart(server);
        server.getPlayerManager().broadcast(Text.literal("§6New rule: §e" + current.name()), false);
    }
}

