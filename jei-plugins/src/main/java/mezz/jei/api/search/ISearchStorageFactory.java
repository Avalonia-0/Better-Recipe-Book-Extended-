// Forked from JustEnoughItems (https://github.com/mezz/JustEnoughItems), MIT License.
// Copyright (c) 2014-2015 mezz. See jei-plugins/LICENSE.txt for the full license text.
package mezz.jei.api.search;

/**
 * Creates search storage instances for JEI's ingredient search.
 *
 * @since 30.10.0
 */
@FunctionalInterface
public interface ISearchStorageFactory {
	/**
	 * Create a new empty search storage.
	 *
	 * @param <T> the type of values stored in the search index
	 * @since 30.10.0
	 */
	<T> ISearchStorage<T> createSearchStorage();
}
