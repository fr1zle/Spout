/*
 * This file is part of Spout.
 *
 * Copyright (c) 2011-2012, SpoutDev <http://www.spout.org/>
 * Spout is licensed under the SpoutDev License Version 1.
 *
 * Spout is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * In addition, 180 days after any changes are published, you can use the
 * software, incorporating those changes, under the terms of the MIT license,
 * as described in the SpoutDev License Version 1.
 *
 * Spout is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License,
 * the MIT license and the SpoutDev License Version 1 along with this program.
 * If not, see <http://www.gnu.org/licenses/> for the GNU Lesser General Public
 * License and see <http://www.spout.org/SpoutDevLicenseV1.txt> for the full license,
 * including the MIT license.
 */
package org.spout.engine.security;

import java.io.File;
import java.security.AllPermission;
import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Policy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.spout.api.Spout;
import org.spout.api.plugin.Platform;
import org.spout.engine.SpoutApplication;

public class CommonPolicy extends Policy {
	private static PermissionCollection spoutPerms;
	private static PermissionCollection pluginPerms;
	private static PermissionCollection clientPluginPerms;
	private CodeSource spoutCodeSource;

	public CommonPolicy() {
		super();
		spoutPerms = new PublicPermissionCollection(new AllPermission());
	}

	@Override
	public PermissionCollection getPermissions(CodeSource codesource) {
		if (isSpout(codesource)) {
			return spoutPerms;
		} else if (Spout.getEngine().getPlatform() == Platform.CLIENT) {
			return clientPluginPerms;
		}
		return pluginPerms;
	}

	public boolean isSpout(CodeSource codeSource) {
		if (spoutCodeSource == null) {
			File absoluteSource = new File(codeSource.getLocation().getFile()).getAbsoluteFile();
			File absoluteSpout = new File(SpoutApplication.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getAbsoluteFile();
			if (absoluteSource.equals(absoluteSpout)) {
				spoutCodeSource = codeSource;
				return true;
			}
		}
		return codeSource.equals(spoutCodeSource);
	}

	public class PublicPermissionCollection extends PermissionCollection implements Cloneable {
		private static final long serialVersionUID = -7823947134698234691L;
		private final List<Permission> perms = new ArrayList<Permission>();

		public PublicPermissionCollection() {
		}

		public PublicPermissionCollection(Permission... perms) {
			this.perms.addAll(Arrays.asList(perms));
		}

		public void add(Permission p) {
			perms.add(p);
		}

		public boolean implies(Permission p) {
			for (Permission perm : perms) {
				if ((perm).implies(p)) {
					return true;
				}
			}
			return false;
		}

		public Enumeration<Permission> elements() {
			return Collections.enumeration(perms);
		}

		public boolean isReadOnly() {
			return false;
		}
	}
}
