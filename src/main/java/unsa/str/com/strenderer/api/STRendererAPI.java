package unsa.str.com.strenderer.api;

import unsa.str.com.strenderer.ShortcutTerminalRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class STRendererAPI {
    private static STRendererAPI INSTANCE;
    private final Map<ResourceLocation, ModelData> MODEL_REGISTRY = new ConcurrentHashMap<>();
    private final Map<ResourceLocation, ModelData> ITEM_MODEL_BINDING = new ConcurrentHashMap<>();

    public static void setInstance(STRendererAPI instance) { INSTANCE = instance; }
    public static STRendererAPI getInstance() {
        if (INSTANCE == null) throw new IllegalStateException("STRendererAPI not initialized!");
        return INSTANCE;
    }

    public void registerGLTFModel(ResourceLocation modelId, String modelPath) {
        MODEL_REGISTRY.put(modelId, new ModelData(ModelType.GLTF, modelPath));
        ShortcutTerminalRenderer.LOGGER.debug("Registered GLTF: {} -> {}", modelId, modelPath);
    }

    public void registerOBJModel(ResourceLocation modelId, String modelPath, @Nullable String mtlPath) {
        MODEL_REGISTRY.put(modelId, new ModelData(ModelType.OBJ, modelPath, mtlPath));
        ShortcutTerminalRenderer.LOGGER.debug("Registered OBJ: {} -> {}", modelId, modelPath);
    }

    public void bindItemToModel(ResourceLocation itemId, ResourceLocation modelId) {
        ModelData data = MODEL_REGISTRY.get(modelId);
        if (data != null) {
            ITEM_MODEL_BINDING.put(itemId, data);
            ShortcutTerminalRenderer.LOGGER.info("Bound item {} to model {}", itemId, modelId);
        } else {
            ShortcutTerminalRenderer.LOGGER.warn("Model {} not found for item {}", modelId, itemId);
        }
    }

    @Nullable public ModelData getModelForItem(ResourceLocation itemId) { return ITEM_MODEL_BINDING.get(itemId); }
    @Nullable public ModelData getModelForItemStack(ItemStack stack) {
        return getModelForItem(stack.getItemHolder().getKey().location());
    }

    public enum ModelType { GLTF, OBJ }
    public static class ModelData {
        private final ModelType type;
        private final String modelPath;
        private final String mtlPath;
        public ModelData(ModelType type, String modelPath) { this(type, modelPath, null); }
        public ModelData(ModelType type, String modelPath, @Nullable String mtlPath) {
            this.type = type; this.modelPath = modelPath; this.mtlPath = mtlPath;
        }
        public ModelType getType() { return type; }
        public String getModelPath() { return modelPath; }
        @Nullable public String getMtlPath() { return mtlPath; }
    }
}
