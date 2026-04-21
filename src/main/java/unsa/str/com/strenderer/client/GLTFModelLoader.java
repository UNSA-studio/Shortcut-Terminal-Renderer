package unsa.str.com.strenderer.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import de.javagl.jgltf.model.*;
import de.javagl.jgltf.model.io.GltfModelReader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
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
        // 获取网格模型
        List<MeshModel> meshes = model.getMeshModels();
        for (MeshModel mesh : meshes) {
            List<MeshPrimitiveModel> primitives = mesh.getMeshPrimitiveModels();
            for (MeshPrimitiveModel primitive : primitives) {
                renderPrimitive(primitive, matrix, buffer, light, overlay);
            }
        }
    }

    private static void renderPrimitive(MeshPrimitiveModel primitive, Matrix4f matrix, MultiBufferSource buffer, int light, int overlay) {
        Map<String, AccessorModel> attributes = primitive.getAttributes();
        AccessorModel positionAccessor = attributes.get("POSITION");
        if (positionAccessor == null) return;

        // 获取索引数据
        AccessorModel indicesAccessor = primitive.getIndices();
        int vertexCount = indicesAccessor != null ? indicesAccessor.getCount() : positionAccessor.getCount();

        // 获取属性缓冲区
        FloatBuffer positions = positionAccessor.getBufferFloat();
        FloatBuffer normals = attributes.containsKey("NORMAL") ? attributes.get("NORMAL").getBufferFloat() : null;
        FloatBuffer texCoords = attributes.containsKey("TEXCOORD_0") ? attributes.get("TEXCOORD_0").getBufferFloat() : null;

        // 索引缓冲区
        ByteBuffer indexBuffer = indicesAccessor != null ? indicesAccessor.getBufferByte() : null;
        int indexComponentType = indicesAccessor != null ? indicesAccessor.getComponentType() : 0;

        // 使用线框模式确保可见 (后续可改为实体渲染)
        VertexConsumer consumer = buffer.getBuffer(RenderType.lines());

        int packedLight = LightTexture.pack(light, overlay);

        if (indexBuffer == null) {
            // 无索引，按顺序渲染
            for (int i = 0; i < vertexCount; i += 3) {
                for (int j = 0; j < 3; j++) {
                    int idx = i + j;
                    float x = positions.get(idx * 3);
                    float y = positions.get(idx * 3 + 1);
                    float z = positions.get(idx * 3 + 2);
                    float nx = normals != null ? normals.get(idx * 3) : 0;
                    float ny = normals != null ? normals.get(idx * 3 + 1) : 0;
                    float nz = normals != null ? normals.get(idx * 3 + 2) : 1;
                    float u = texCoords != null ? texCoords.get(idx * 2) : 0;
                    float v = texCoords != null ? texCoords.get(idx * 2 + 1) : 0;

                    consumer.addVertex(matrix, x, y, z)
                            .setColor(255, 255, 255, 255)
                            .setUv(u, v)
                            .setUv2(packedLight)
                            .setNormal(nx, ny, nz);
                }
            }
        } else {
            // 有索引缓冲区
            for (int i = 0; i < vertexCount; i++) {
                int index = readIndex(indexBuffer, indexComponentType, i);
                float x = positions.get(index * 3);
                float y = positions.get(index * 3 + 1);
                float z = positions.get(index * 3 + 2);
                float nx = normals != null ? normals.get(index * 3) : 0;
                float ny = normals != null ? normals.get(index * 3 + 1) : 0;
                float nz = normals != null ? normals.get(index * 3 + 2) : 1;
                float u = texCoords != null ? texCoords.get(index * 2) : 0;
                float v = texCoords != null ? texCoords.get(index * 2 + 1) : 0;

                consumer.addVertex(matrix, x, y, z)
                        .setColor(255, 255, 255, 255)
                        .setUv(u, v)
                        .setUv2(packedLight)
                        .setNormal(nx, ny, nz);
            }
        }
    }

    private static int readIndex(ByteBuffer buffer, int componentType, int offset) {
        switch (componentType) {
            case AccessorModel.ComponentType.GL_UNSIGNED_BYTE:
                return buffer.get(offset) & 0xFF;
            case AccessorModel.ComponentType.GL_UNSIGNED_SHORT:
                return buffer.getShort(offset * 2) & 0xFFFF;
            case AccessorModel.ComponentType.GL_UNSIGNED_INT:
                return buffer.getInt(offset * 4);
            default:
                return 0;
        }
    }

    public static void clearCache() {
        MODEL_CACHE.clear();
    }
}
