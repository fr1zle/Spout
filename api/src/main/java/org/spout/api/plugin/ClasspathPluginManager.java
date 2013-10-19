/*
 * This file is part of Spout.
 *
 * Copyright (c) 2011 Spout LLC <http://www.spout.org/>
 * Spout is licensed under the Spout License Version 1.
 *
 * Spout is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * In addition, 180 days after any changes are published, you can use the
 * software, incorporating those changes, under the terms of the MIT license,
 * as described in the Spout License Version 1.
 *
 * Spout is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License,
 * the MIT license and the Spout License Version 1 along with this program.
 * If not, see <http://www.gnu.org/licenses/> for the GNU Lesser General Public
 * License and see <http://spout.in/licensev1> for the full license, including
 * the MIT license.
 */
package org.spout.api.plugin;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.spout.api.Engine;
import org.spout.api.Spout;
import org.spout.api.exception.InvalidDescriptionFileException;
import org.spout.api.exception.InvalidPluginException;
import org.spout.api.exception.UnknownDependencyException;
import org.spout.api.meta.SpoutMetaPlugin;
import org.spout.api.protocol.Protocol;

import com.google.common.base.Preconditions;

/**
 * A PluginManager that loads and handles plugins from the current classpath.
 *
 * This PluginManager should <b>ONLY</b> be used inside your development environment as it does not properly separate classloaders.
 */
public class ClasspathPluginManager implements PluginManager {
	private String pluginsInCp = System.getProperty("spout.plugins");
	private List<Plugin> plugins = new ArrayList<>();
	private Map<String, Plugin> names = new HashMap<>();
	private final SpoutMetaPlugin metaPlugin;
	private Engine engine;

	public ClasspathPluginManager(Engine engine) {
		this.engine = engine;
		this.metaPlugin = new SpoutMetaPlugin(engine);
	}

	@Override
	public Plugin getPlugin(String plugin) {
		return names.get(plugin);
	}

	@Override
	public List<Plugin> getPlugins() {
		return Collections.unmodifiableList(plugins);
	}

	@Override
	public Plugin loadPlugin(File file) throws InvalidPluginException, InvalidDescriptionFileException, UnknownDependencyException {
		throw new UnsupportedOperationException("ClasspathPluginManager does not support loading plugins from files.");
	}

	@Override
	public void installUpdates() {
		Spout.warn("Installing updates is not supported when loading plugins from the classpath.");
	}

	@Override
	public List<Plugin> loadPlugins(File paramFile) {
		Preconditions.checkArgument(!StringUtils.isEmpty(pluginsInCp), "No plugins defined in system property 'spout.plugins'");
		String[] pluginsToLoad = pluginsInCp.split(",");
		for (String pluginToLoad : pluginsToLoad) {
			try {
				Class<Plugin> pluginClass = (Class<Plugin>) Class.forName(pluginToLoad);
				URL codeSource = pluginClass.getProtectionDomain().getCodeSource().getLocation();
				Spout.info(pluginClass.getCanonicalName() + " in " + codeSource.toExternalForm());
				Plugin plugin = pluginClass.newInstance();
				PluginDescriptionFile pluginDescriptionFile = new PluginDescriptionFile(new FileInputStream(new File(new File(codeSource.getFile()), "properties.yml")));
				plugin.description = pluginDescriptionFile;
				plugin.dataFolder = new File(engine.getPluginFolder(), pluginDescriptionFile.getName());
				plugin.engine = engine;
				plugin.logger = new PluginLogger(plugin);
				for (String protocolString : pluginDescriptionFile.getProtocols()) {
					Class<? extends Protocol> protocol = Class.forName(protocolString).asSubclass(Protocol.class);
					Constructor<? extends Protocol> pConstructor = protocol.getConstructor();
					Protocol.registerProtocol(pConstructor.newInstance());
				}
				plugin.onLoad();
				names.put(pluginDescriptionFile.getName(), plugin);
				plugins.add(plugin);
			} catch (Exception e) {
				Spout.warn("Could not load plugin " + pluginToLoad, e);
			}
		}
		return plugins;
	}

	@Override
	public void disablePlugins() {
		for (Plugin plugin : plugins) {
			plugin.onDisable();
		}
	}

	@Override
	public void clearPlugins() {
		synchronized (this) {
			disablePlugins();
			plugins.clear();
		}
	}

	@Override
	public void enablePlugin(Plugin plugin) {
		if (plugin == metaPlugin) {
			return;
		}
		if (!plugin.isEnabled()) {
			plugin.enabled = true;
			plugin.onEnable();
			Spout.info("Enabled plugin " + plugin.getClass().getCanonicalName());
		}
	}

	@Override
	public void disablePlugin(Plugin plugin) {
		if (plugin == metaPlugin) {
			return;
		}
		if (plugin.isEnabled()) {
			plugin.enabled = false;
			plugin.onDisable();
			Spout.info("Disabled plugin " + plugin.getClass().getCanonicalName());
		}
	}

	@Override
	public SpoutMetaPlugin getMetaPlugin() {
		return metaPlugin; //To change body of implemented methods use File | Settings | File Templates.
	}
}
