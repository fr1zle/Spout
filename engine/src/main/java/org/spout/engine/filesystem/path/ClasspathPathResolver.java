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
package org.spout.engine.filesystem.path;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.spout.api.Spout;
import org.spout.api.plugin.Plugin;
import org.spout.api.resource.ResourcePathResolver;

public class ClasspathPathResolver implements ResourcePathResolver {
	@Override
	public boolean existsInPath(String host, String path) {
		File file = getFile(host, path);
		return file.exists();
	}

	private File getFile(String host, String path) {
		Plugin plugin = Spout.getEngine().getPluginManager().getPlugin(host);
		URL codeSource = plugin.getClass().getProtectionDomain().getCodeSource().getLocation();
		return new File(new File(codeSource.getFile()), path);
	}

	@Override
	public boolean existsInPath(URI uri) {
		return existsInPath(uri.getHost(), uri.getPath());
	}

	@Override
	public InputStream getStream(String host, String path) {
		try {
			return new FileInputStream(getFile(host, path));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public InputStream getStream(URI uri) {
		return getStream(uri.getHost(), uri.getPath());
	}

	@Override
	public String[] list(String host, String path) {
		File file = getFile(host, path);
		if (!file.exists()) {
			return null;
		}
		if (file.isDirectory()) {
			List<String> fileList = new ArrayList<>();
			File[] files = file.listFiles();
			for (File innerfile : files) {
				fileList.add(innerfile.getName());
			}
			return fileList.toArray(new String[fileList.size()]);
		} else {
			return new String[] { file.getName() };
		}
	}

	@Override
	public String[] list(URI uri) {
		return list(uri.getHost(), uri.getPath());
	}
}
