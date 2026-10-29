package com.remplify.pearltimer;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PearlTimerClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("pearltimer");
	public static PearlConfig CONFIG;

	/** Player entity id -> time (ms) they threw their pearl. */
	private static final Map<Integer, Long> TIMERS = new ConcurrentHashMap<>();

	/** How long a "Pearl ?" label stays up while this server's cooldown is still unknown. */
	private static final long UNKNOWN_SHOW_MS = 10_000;
	/** Cooldown packets arriving this close together count as one throw (the server may send vanilla's 1s first, then its own). */
	private static final long DETECT_WINDOW_MS = 1_000;
	private static long detectWindowStart = 0;
	private static int detectWindowMax = 0;
	private static boolean hintShown = false;

	/** A pearl further than this from the thrower's eyes when it appears is not a fresh throw (e.g. it just flew into view). */
	private static final double MAX_SPAWN_DIST_SQ = 5.0 * 5.0;

	@Override
	public void onInitializeClient() {
		CONFIG = PearlConfig.load();

		ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> onEntityLoad(entity));
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> { TIMERS.clear(); hintShown = false; });
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> TIMERS.clear());

		registerCommands();
		registerKeybind();
	}

	private static void registerKeybind() {
		KeyMapping.Category category = KeyMapping.Category.register(
				Identifier.fromNamespaceAndPath("pearltimer", "main"));
		// Unbound by default; set it in Options > Controls > Key Binds > Pearl Timer.
		KeyMapping toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.pearltimer.toggle", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			finishDetection();
			while (toggleKey.consumeClick()) {
				toggle();
				if (client.player != null) {
					client.player.sendOverlayMessage(Component.literal("Pearl timers ")
							.withStyle(ChatFormatting.LIGHT_PURPLE)
							.append(CONFIG.enabled
									? Component.literal("ON").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
									: Component.literal("OFF").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
				}
			}
		});
	}

	private static void toggle() {
		CONFIG.enabled = !CONFIG.enabled;
		CONFIG.save();
	}

	private static void onEntityLoad(Entity entity) {
		if (!(entity instanceof ThrownEnderpearl pearl)) return;
		Entity owner = pearl.getOwner();
		if (!(owner instanceof Player thrower)) return;

		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && thrower.getId() == mc.player.getId()) return; // not you

		if (pearl.distanceToSqr(thrower.getEyePosition()) > MAX_SPAWN_DIST_SQ) return;

		TIMERS.put(thrower.getId(), System.currentTimeMillis());

		if (cooldownTicks() == null && !hintShown && mc.player != null && CONFIG.enabled) {
			hintShown = true;
			mc.player.sendSystemMessage(Component.literal("[Pearl Timer] ").withStyle(ChatFormatting.LIGHT_PURPLE)
					.append(Component.literal("Throw an ender pearl yourself once so I can learn this server's pearl cooldown.")
							.withStyle(ChatFormatting.GRAY)));
		}
	}

	/** Address of the server you're on, used to remember a cooldown per server. */
	private static String serverKey() {
		Minecraft mc = Minecraft.getInstance();
		ServerData server = mc.getCurrentServer();
		if (server == null || server.ip == null) return "singleplayer";
		String ip = server.ip.trim().toLowerCase(Locale.ROOT);
		if (ip.endsWith(":25565")) ip = ip.substring(0, ip.length() - 6);
		if (ip.endsWith(".")) ip = ip.substring(0, ip.length() - 1);
		return ip;
	}

	/** This server's pearl cooldown in ticks, or null if not learned yet. */
	private static Integer cooldownTicks() {
		return CONFIG.serverCooldowns.get(serverKey());
	}

	private static void setCooldownTicks(int ticks) {
		CONFIG.serverCooldowns.put(serverKey(), ticks);
		CONFIG.save();
	}

	/** Called when the server puts a cooldown on your own ender pearls. */
	public static void onOwnPearlCooldown(int ticks) {
		if (!CONFIG.autoDetect || ticks <= 0) return;

		// Collect for a moment and keep the longest value; finishDetection() saves it.
		if (detectWindowMax == 0) detectWindowStart = System.currentTimeMillis();
		detectWindowMax = Math.max(detectWindowMax, ticks);
	}

	private static void finishDetection() {
		if (detectWindowMax == 0 || System.currentTimeMillis() - detectWindowStart < DETECT_WINDOW_MS) return;
		int ticks = detectWindowMax;
		detectWindowMax = 0;

		Integer old = cooldownTicks();
		if (old != null && old == ticks) return;
		setCooldownTicks(ticks);
		LOGGER.info("Detected ender pearl cooldown on {}: {} ticks", serverKey(), ticks);

		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.sendSystemMessage(Component.literal("[Pearl Timer] ").withStyle(ChatFormatting.LIGHT_PURPLE)
					.append(Component.literal("Pearl cooldown on " + serverKey() + " is " + seconds(ticks) + "s")
							.withStyle(ChatFormatting.GRAY)));
		}
	}

	/** Returns the label to draw above this player, or null if there is no active timer. */
	public static Component getLabel(int entityId) {
		if (!CONFIG.enabled) return null;
		Long thrownAt = TIMERS.get(entityId);
		if (thrownAt == null) return null;
		long elapsed = System.currentTimeMillis() - thrownAt;

		Integer ticks = cooldownTicks();
		if (ticks == null) {
			// Cooldown not learned yet: just mark that they threw one.
			if (elapsed > UNKNOWN_SHOW_MS) {
				TIMERS.remove(entityId);
				return null;
			}
			return Component.literal("Pearl ").withStyle(ChatFormatting.LIGHT_PURPLE)
					.append(Component.literal("?").withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD));
		}

		long total = ticks * 50L;
		long remaining = total - elapsed;
		if (remaining <= 0) {
			TIMERS.remove(entityId);
			return null;
		}

		String time = CONFIG.showDecimals
				? String.format("%.1fs", remaining / 1000.0)
				: ((remaining + 999) / 1000) + "s";

		double fraction = remaining / (double) total;
		ChatFormatting colour = fraction > 0.5 ? ChatFormatting.RED
				: fraction > 0.2 ? ChatFormatting.GOLD
				: ChatFormatting.GREEN;

		return Component.literal("Pearl ").withStyle(ChatFormatting.LIGHT_PURPLE)
				.append(Component.literal(time).withStyle(colour, ChatFormatting.BOLD));
	}

	private static void cleanup() {
		Integer ticks = cooldownTicks();
		long keepMs = ticks == null ? UNKNOWN_SHOW_MS : ticks * 50L;
		long now = System.currentTimeMillis();
		for (Iterator<Long> it = TIMERS.values().iterator(); it.hasNext(); ) {
			if (now - it.next() > keepMs) it.remove();
		}
	}

	private static String seconds(int ticks) {
		double s = ticks / 20.0;
		return s == Math.floor(s) ? String.valueOf((int) s) : String.valueOf(s);
	}

	private static void registerCommands() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				ClientCommands.literal("pearltimer")
						.executes(ctx -> {
							cleanup();
							ctx.getSource().sendFeedback(Component.literal(
									"Pearl Timer: " + (CONFIG.enabled ? "on" : "off")
											+ " | cooldown on " + serverKey() + ": "
											+ (cooldownTicks() == null ? "not detected yet (throw a pearl)" : seconds(cooldownTicks()) + "s")
											+ " | auto-detect " + (CONFIG.autoDetect ? "on" : "off")
											+ " | decimals " + (CONFIG.showDecimals ? "on" : "off"))
									.withStyle(ChatFormatting.LIGHT_PURPLE));
							return 1;
						})
						.then(ClientCommands.literal("toggle").executes(ctx -> {
							toggle();
							ctx.getSource().sendFeedback(Component.literal("Pearl timers " + (CONFIG.enabled ? "on" : "off")));
							return 1;
						}))
						.then(ClientCommands.literal("cooldown")
								.then(ClientCommands.argument("seconds", DoubleArgumentType.doubleArg(0.05, 600))
										.executes(ctx -> {
											double s = DoubleArgumentType.getDouble(ctx, "seconds");
											setCooldownTicks((int) Math.round(s * 20));
											ctx.getSource().sendFeedback(Component.literal(
													"Pearl cooldown on " + serverKey() + " set to " + seconds(cooldownTicks()) + "s"
															+ (CONFIG.autoDetect ? " (auto-detect is on and may overwrite this when you throw a pearl)" : "")));
											return 1;
										})))
						.then(ClientCommands.literal("autodetect")
								.then(ClientCommands.argument("on", BoolArgumentType.bool())
										.executes(ctx -> {
											CONFIG.autoDetect = BoolArgumentType.getBool(ctx, "on");
											CONFIG.save();
											ctx.getSource().sendFeedback(Component.literal("Auto-detect " + (CONFIG.autoDetect ? "on" : "off")));
											return 1;
										})))
						.then(ClientCommands.literal("decimals")
								.then(ClientCommands.argument("on", BoolArgumentType.bool())
										.executes(ctx -> {
											CONFIG.showDecimals = BoolArgumentType.getBool(ctx, "on");
											CONFIG.save();
											ctx.getSource().sendFeedback(Component.literal("Decimals " + (CONFIG.showDecimals ? "on" : "off")));
											return 1;
										})))
						.then(ClientCommands.literal("reset").executes(ctx -> {
							CONFIG.serverCooldowns.remove(serverKey());
							CONFIG.save();
							ctx.getSource().sendFeedback(Component.literal(
									"Forgot the pearl cooldown for " + serverKey() + ". Throw a pearl to detect it again."));
							return 1;
						}))
						.then(ClientCommands.literal("clear").executes(ctx -> {
							TIMERS.clear();
							ctx.getSource().sendFeedback(Component.literal("Cleared all pearl timers"));
							return 1;
						}))
		));
	}
}
