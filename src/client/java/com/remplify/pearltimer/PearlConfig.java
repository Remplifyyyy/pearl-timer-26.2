package com.remplify.pearltimer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Settings saved to config/pearltimer.json. */
public class PearlConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("pearltimer.json");

	/** Pearl cooldown length in ticks (20 ticks = 1 second) for each server address, learned when you throw a pearl. */
	public Map<String, Integer> serverCooldowns = new HashMap<>();
	/** Learn the cooldown automatically from the server when you throw a pearl yourself. */
	public boolean autoDetect = true;
	/** Show the timers at all. */
	public boolean enabled = true;
	/** Show tenths of a second (12.4s) instead of whole seconds (13s). */
	public boolean showDecimals = false;

	public static PearlConfig load() {
		try {
			if (Files.exists(FILE)) {
				try (Reader r = Files.newBufferedReader(FILE)) {
					PearlConfig c = GSON.fromJson(r, PearlConfig.class);
					if (c != null) {
						if (c.serverCooldowns == null) c.serverCooldowns = new HashMap<>();
						return c;
					}
				}
			}
		} catch (Exception e) {
			PearlTimerClient.LOGGER.warn("Could not read pearltimer.json, using defaults", e);
		}
		PearlConfig c = new PearlConfig();
		c.save();
		return c;
	}

	public void save() {
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer w = Files.newBufferedWriter(FILE)) {
				GSON.toJson(this, w);
			}
		} catch (Exception e) {
			PearlTimerClient.LOGGER.warn("Could not save pearltimer.json", e);
		}
	}
}
