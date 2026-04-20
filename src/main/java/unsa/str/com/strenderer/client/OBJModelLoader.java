import net.minecraft.client.Minecraft;
package unsa.str.com.strenderer.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class OBJModelLoader {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, BakedModel> MODEL_CACHE = new ConcurrentHashMap<>();

    public static BakedModel loadModel(String modelPath) {
        if (MODEL_CACHE.containsKey(modelPath)) return MODEL_CACHE.get(modelPath);
        try {
            ResourceLocation loc = ResourceLocation.parse(modelPath);
            ModelResourceLocation mrl = ModelResourceLocation.inventory(loc);
            BakedModel model = Minecraft.getInstance().getModelManager().getModel(mrl);
            if (model != null) {
                MODEL_CACHE.put(modelPath, model);
                LOGGER.debug("Loaded OBJ: {}", modelPath);
                return model;
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load OBJ: {}", modelPath, e);
        }
        return null;
    }

    public static void clearCache() { MODEL_CACHE.clear(); }
}
