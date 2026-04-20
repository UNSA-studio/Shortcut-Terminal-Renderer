package com.yourname.strenderer.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import com.yourname.strenderer.ShortcutTerminalRenderer;
import de.javagl.jgltf.model.GltfModel;
import de.javagl.jgltf.model.io.GltfModelReader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import javax.annotation.Nullable;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GLTFModelLoader {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, GltfModel> MODEL_CACHE = new ConcurrentHashMap<>();

    @Nullable
    public static GltfModel loadModel(String modelPath) {
        if (MODEL_CACHE.containsKey(modelPath)) return MODEL_CACHE.get(modelPath);
        try {
            ResourceManager manager = Minecraft.getInstance().getResourceManager();
            ResourceLocation loc = ResourceLocation.parse(modelPath);
            Resource res = manager.getResource(loc).orElseThrow(() -> new RuntimeException("Not found: " + modelPath));
            try (InputStream is = res.open()) {
                GltfModel model = new GltfModelReader().read(is);
                MODEL_CACHE.put(modelPath, model);
                LOGGER.debug("Loaded GLTF: {}", modelPath);
                return model;
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load GLTF: {}", modelPath, e);
            return null;
        }
    }

    public static void renderModel(GltfModel model, PoseStack poseStack, MultiBufferSource buffer, int light, int overlay) {
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(
                ResourceLocation.fromNamespaceAndPath(ShortcutTerminalRenderer.MODID, "textures/misc/white.png")));
        Matrix4f matrix = poseStack.last().pose();
        // TODO: 实际网格遍历逻辑
        LOGGER.trace("Rendered GLTF model");
    }

    public static void clearCache() { MODEL_CACHE.clear(); }
}
