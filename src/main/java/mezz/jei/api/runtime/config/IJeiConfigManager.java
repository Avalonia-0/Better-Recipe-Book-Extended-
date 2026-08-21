// Forked from JustEnoughItems (https://github.com/mezz/JustEnoughItems), MIT License.
// Copyright (c) 2014-2015 mezz. See jei-plugins/LICENSE.txt for the full license text.
package mezz.jei.api.runtime.config;

import mezz.jei.api.runtime.IJeiRuntime;

import java.util.Collection;

/**
 * Gives access to JEI's config files.
 * Useful for mods that let users change configs in-game.
 *
 * Get an instance from {@link IJeiRuntime#getConfigManager()}
 *
 * @since 12.1.0
 */
public interface IJeiConfigManager {
	/**
	 * @return all of JEI's config files.
	 * @see IJeiConfigFile
	 *
	 * @since 12.1.0
	 */
	Collection<IJeiConfigFile> getConfigFiles();
}
