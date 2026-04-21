package unsa.str.com.strenderer.client.gltf;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.logging.LogUtils;
import de.javagl.jgltf.model.*;
import de.javagl.jgltf.model.io.GltfModelReader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.slf4j.Logger;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GltfModelLoader {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, BakedModel> MODEL_CACHE = new ConcurrentHashMap<>();

    @Nullable
    public static BakedModel loadModel(String modelPath) {
        if (MODEL_CACHE.containsKey(modelPath)) return MODEL_CACHE.get(modelPath);
        try {
            ResourceManager manager = Minecraft.getInstance().getResourceManager();
            ResourceLocation loc = ResourceLocation.parse(modelPath);
            Resource res = manager.getResource(loc).orElseThrow(() -> new RuntimeException("Model not found: " + modelPath));
            try (InputStream is = res.open()) {
                GltfModelReader reader = new GltfModelReader();
                // 使用正确的API：readWithoutReferences 返回 GltfModel
                GltfModel model = reader.readWithoutReferences(is);
                BakedModel bakedModel = new GlTFBakedModel(model);
                MODEL_CACHE.put(modelPath, bakedModel);
                LOGGER.debug("Successfully loaded GLTF model: {}", modelPath);
                return bakedModel;
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load GLTF model: {}", modelPath, e);
            return null;
        }
    }

    public static void clearCache() {
        MODEL_CACHE.clear();
    }

    private static class GlTFBakedModel implements IDynamicBakedModel {
        private final List<BakedQuad> quads = new ArrayList<>();

        public GlTFBakedModel(GltfModel model) {
            // 遍历所有节点
            for (NodeModel node : model.getNodes()) {
                MeshModel mesh = node.getMesh();
                if (mesh == null) continue;
                // 遍历网格的所有图元
                for (MeshPrimitiveModel primitive : mesh.getMeshPrimitives()) {
                    if (primitive.getMode() != 4) { // 4 代表 TRIANGLES
                        LOGGER.warn("Skipping non-triangle primitive with mode: {}", primitive.getMode());
                        continue;
                    }
                    List<BakedQuad> primitiveQuads = createQuadsForPrimitive(primitive);
                    quads.addAll(primitiveQuads);
                }
            }
            LOGGER.debug("Built {} quads for GLTF model", quads.size());
        }

        private List<BakedQuad> createQuadsForPrimitive(MeshPrimitiveModel primitive) {
            Map<String, AccessorModel> attributes = primitive.getAttributes();
            AccessorModel posAccessor = attributes.get("POSITION");
            AccessorModel normalAccessor = attributes.get("NORMAL");
            AccessorModel uvAccessor = attributes.get("TEXCOORD_0");
            if (posAccessor == null) return Collections.emptyList();

            // 获取顶点数据缓冲区
            FloatBuffer positions = asFloatBuffer(posAccessor.getBuffer());
            FloatBuffer normals = normalAccessor != null ? asFloatBuffer(normalAccessor.getBuffer()) : null;
            FloatBuffer texCoords = uvAccessor != null ? asFloatBuffer(uvAccessor.getBuffer()) : null;

            // 索引数据
            AccessorModel indicesAccessor = primitive.getIndices();
            int vertexCount = indicesAccessor != null ? indicesAccessor.getCount() : posAccessor.getCount();
            ByteBuffer indexBuffer = indicesAccessor != null ? indicesAccessor.getBuffer() : null;
            int indexComponentType = indicesAccessor != null ? indicesAccessor.getComponentType() : 0;

            // 使用缺失纹理作为占位符，后续可扩展材质支持
            TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                    .apply(MissingTextureAtlasSprite.getLocation());

            List<BakedQuad> quadList = new ArrayList<>();
            QuadBakingVertexConsumer baker = new QuadBakingVertexConsumer(q -> quadList.add(q));
            baker.setSprite(sprite);
            baker.setDirection(Direction.NORTH); // 物品模型方向无所谓

            for (int i = 0; i < vertexCount; i += 3) {
                int[] indices = new int[3];
                if (indexBuffer != null) {
                    indices[0] = readIndex(indexBuffer, indexComponentType, i);
                    indices[1] = readIndex(indexBuffer, indexComponentType, i + 1);
                    indices[2] = readIndex(indexBuffer, indexComponentType, i + 2);
                } else {
                    indices[0] = i; indices[1] = i + 1; indices[2] = i + 2;
                }

                for (int j = 0; j < 3; j++) {
                    int idx = indices[j];
                    Vector3f pos = new Vector3f(positions.get(idx * 3), positions.get(idx * 3 + 1), positions.get(idx * 3 + 2));
                    Vector3f normal = normals != null ? new Vector3f(normals.get(idx * 3), normals.get(idx * 3 + 1), normals.get(idx * 3 + 2)) : new Vector3f(0, 1, 0);
                    float u = texCoords != null ? texCoords.get(idx * 2) : 0;
                    float v = texCoords != null ? texCoords.get(idx * 2 + 1) : 0;

                    baker.vertex(pos.x, pos.y, pos.z)
                         .color(255, 255, 255, 255)
                         .uv(sprite.getU(u), sprite.getV(v))
                         .uv2(0xF000F0) // 默认满光照
                         .normal(normal.x, normal.y, normal.z)
                         .endVertex();
                }
            }
            return quadList;
        }

        private FloatBuffer asFloatBuffer(ByteBuffer buffer) {
            return buffer.asFloatBuffer();
        }

        private int readIndex(ByteBuffer buffer, int componentType, int offset) {
            switch (componentType) {
                case 5121: // GL_UNSIGNED_BYTE
                    return buffer.get(offset) & 0xFF;
                case 5123: // GL_UNSIGNED_SHORT
                    return buffer.getShort(offset * 2) & 0xFFFF;
                case 5125: // GL_UNSIGNED_INT
                    return buffer.getInt(offset * 4);
                default:
                    return 0;
            }
        }

        @Override
        public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource rand, @NotNull ModelData data, @Nullable RenderType renderType) {
            return quads;
        }

        @Override public boolean useAmbientOcclusion() { return true; }
        @Override public boolean isGui3d() { return true; }
        @Override public boolean usesBlockLight() { return true; }
        @Override public boolean isCustomRenderer() { return false; }
        @Override public @NotNull TextureAtlasSprite getParticleIcon() {
            return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(MissingTextureAtlasSprite.getLocation());
        }
        @Override public @NotNull net.minecraft.client.renderer.block.model.ItemOverrides getOverrides() {
            return net.minecraft.client.renderer.block.model.ItemOverrides.EMPTY;
        }
    }
}
