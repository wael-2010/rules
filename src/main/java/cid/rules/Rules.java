package cid.rules;

import cid.rules.Orders.NoEating;
import cid.rules.Orders.NoJumping;
import cid.rules.Orders.NoSprinting;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Rules implements ModInitializer {
	public static final String MOD_ID = "rules";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {

		Manager.register(new NoEating());
		Manager.register(new NoSprinting());
        Manager.register(new NoJumping());









		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(CommandManager.literal("start").executes(ctx -> {
				Manager.start(ctx.getSource().getServer());
				ctx.getSource().sendFeedback(() -> Text.literal("§cFollow the Rules or DIE!"), true);
				return 1;
			}));
			dispatcher.register(CommandManager.literal("stop").executes(ctx -> {
				Manager.stop(ctx.getSource().getServer());
				ctx.getSource().sendFeedback(() -> Text.literal("§2You are Free , For now....."), true);
				return 1;
			}));
		});

		ServerTickEvents.END_SERVER_TICK.register(Manager::tick);

	}}