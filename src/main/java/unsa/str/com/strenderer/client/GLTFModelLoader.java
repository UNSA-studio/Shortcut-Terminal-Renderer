package unsa.str.com.strenderer.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import de.javagl.jgltf.model.*;
import de.javagl.jgltf.model.io.GltfModelReader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import unsa.str.com.strenderer.ShortcutTerminalRenderer;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.List;
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
                GltfModelReader reader = new GltfModelReader();
                GltfModel model = reader.readWithoutReferences(is);
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
        if (model == null) return;
        Matrix4f matrix = poseStack.last().pose();
        for (MeshModel mesh : model.getMeshModels()) {
            for (MeshPrimitiveModel primitive : mesh.getMeshPrimitiveModels()) {
                renderPrimitive(primitive, matrix, buffer, light, overlay);
            }
        }
    }

    private static void renderPrimitive(MeshPrimitiveModel primitive, Matrix4f matrix, MultiBufferSource buffer, int light, int overlay) {
        Map<String, AccessorModel> attributes = primitive.getAttributes();
        AccessorModel positionAccessor = attributes.get("POSITION");
        AccessorModel normalAccessor = attributes.get("NORMAL");
        AccessorModel texCoordAccessor = attributes.get("TEXCOORD_0");

        if (positionAccessor == null) return;

        FloatBuffer positions = positionAccessor.getBufferFloat();
        FloatBuffer normals = normalAccessor != null ? normalAccessor.getBufferFloat() : null;
        FloatBuffer texCoords = texCoordAccessor != null ? texCoordAccessor.getBufferFloat() : null;

        AccessorModel indicesAccessor = primitive.getIndices();
        ByteBuffer indicesByte = indicesAccessor != null ? indicesAccessor.getBufferByte() : null;
        int count = indicesByte != null ? indicesAccessor.getCount() : positionAccessor.getCount();

        // 尝试获取材质，若无则使用纯白色
        MaterialModel material = primitive.getMaterialModel();
        ResourceLocation textureLocation = null;
        if (material != null) {
            TextureModel baseColorTexture = material.getBaseColorTexture();
            if (baseColorTexture != null) {
                ImageModel image = baseColorTexture.getImageModel();
                if (image != null) {
                    String uri = image.getUri();
                    if (uri != null && !uri.isEmpty()) {
                        textureLocation = ResourceLocation.parse(uri);
                    }
                }
            }
        }

        VertexConsumer consumer;
        if (textureLocation != null) {
            consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(textureLocation));
        } else {
            consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(
                    ResourceLocation.fromNamespaceAndPath(ShortcutTerminalRenderer.MODID, "textures/misc/white.png")));
        }

        for (int i = 0; i < count; i++) {
            int index;
            if (indicesByte != null) {
                switch (indicesAccessor.getComponentType()) {
                    case GL_UNSIGNED_BYTE:
                        index = indicesByte.get(i) & 0xFF;
                        break;
                    case GL_UNSIGNED_SHORT:
                        index = indicesByte.getShort(i * 2) & 0xFFFF;
                        break;
                    case GL_UNSIGNED_INT:
                        index = indicesByte.getInt(i * 4);
                        break;
                    default:
                        continue;
                }
            } else {
                index = i;
            }

            float x = positions.get(index * 3);
            float y = positions.get(index * 3 + 1);
            float z = positions.get(index * 3 + 2);

            float nx = normals != null ? normals.get(index * 3) : 0;
            float ny = normals != null ? normals.get(index * 3 + 1) : 0;
            float nz = normals != null ? normals.get(index * 3 + 2) : 1;

            float u = texCoords != null ? texCoords.get(index * 2) : 0;
            float v = texCoords != null ? texCoords.get(index * 2 + 1) : 0;

            consumer.addVertex(matrix, x, y, z)
                    .setColor(-1)
                    .setUv(u, v)
                    .setUv2(light)
                    .setNormal(nx, ny, nz);
        }
    }

    public static void clearCache() {
        MODEL_CACHE.clear();
    }
}
