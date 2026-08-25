package mezz.jei.fabric.input;

import mezz.jei.common.input.keys.IJeiKeyMappingBuilder;
import mezz.jei.common.input.keys.IJeiKeyMappingCategoryBuilder;
import net.minecraft.client.KeyMapping;

public class FabricJeiKeyMappingCategoryBuilder implements IJeiKeyMappingCategoryBuilder {
	private final KeyMapping.Category category;

	public FabricJeiKeyMappingCategoryBuilder(KeyMapping.Category category) {
		this.category = category;
	}

	@Override
	public IJeiKeyMappingBuilder createMapping(String description) {
		// BRBE embeds the JEI core headlessly; the optional amecs key-modifier
		// integration is intentionally not bundled (it would expose JEI
		// keybinding features), so mappings always use the plain Fabric builder.
		return new FabricJeiKeyMappingBuilder(category, description);
	}
}
