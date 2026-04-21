package unsa.str.com.strenderer.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import de.javagl.jgltf.model.*;
import de.javagl.jgltf.model.io.GltfModelReader;
import net.minecraft.Util;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.slf4j.Logger;
import unsa.str.com.strenderer.ShortcutTerminalRenderer;

import javax.annotation.Nonnull;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GLTFModelLoader {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, BakedModel> MODEL_CACHE = new ConcurrentHashMap<>();

    @Nullable
    public static BakedModel loadModel(String modelPath) {
        if (MODEL_CACHE.containsKey(modelPath)) return MODEL_CACHE.get(modelPath);
        try {
            ResourceManager manager = Minecraft.getInstance().getResourceManager();
            ResourceLocation loc = ResourceLocation.parse(modelPath);
            Resource res = manager.getResource(loc).orElseThrow(() -> new RuntimeException("Not found: " + modelPath));
            try (InputStream is = res.open()) {
                GltfModelReader reader = new GltfModelReader();
                GltfModel model = reader.readWithoutReferences(is);
                BakedModel bakedModel = new GlTFBakedModel(model, loc);
                MODEL_CACHE.put(modelPath, bakedModel);
                LOGGER.debug("Loaded GLTF as BakedModel: {}", modelPath);
                return bakedModel;
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load GLTF: {}", modelPath, e);
            return null;
        }
    }

    public static void clearCache() {
        MODEL_CACHE.clear();
    }

    // 核心：将 GltfModel 转换为 BakedModel 的实现
    private static class GlTFBakedModel implements IDynamicBakedModel {
        private final List<BakedQuad> quads = new ArrayList<>();
        private final ResourceLocation modelLocation;

        public GlTFBakedModel(GltfModel model, ResourceLocation modelLocation) {
            this.modelLocation = modelLocation;
            LOGGER.debug("Building BakedModel for: {}", modelLocation);
            for (MeshModel mesh : model.getMeshModels()) {
                for (MeshPrimitiveModel primitive : mesh.getMeshPrimitiveModels()) {
                    if (primitive.getMode() != MeshPrimitiveModel.Mode.TRIANGLES) {
                        LOGGER.warn("Skipping primitive with non-TRIANGLES mode: {}", primitive.getMode());
                        continue;
                    }
                    List<BakedQuad> primitiveQuads = createQuadsForPrimitive(primitive);
                    quads.addAll(primitiveQuads);
                    LOGGER.debug("Added {} quads from primitive", primitiveQuads.size());
                }
            }
            LOGGER.debug("Total quads for {}: {}", modelLocation, quads.size());
        }

        private List<BakedQuad> createQuadsForPrimitive(MeshPrimitiveModel primitive) {
            Map<String, AccessorModel> attributes = primitive.getAttributes();
            AccessorModel posAccessor = attributes.get("POSITION");
            AccessorModel normalAccessor = attributes.get("NORMAL");
            AccessorModel uvAccessor = attributes.get("TEXCOORD_0");
            if (posAccessor == null) return Collections.emptyList();

            FloatBuffer positions = posAccessor.getBufferFloat();
            FloatBuffer normals = normalAccessor != null ? normalAccessor.getBufferFloat() : null;
            FloatBuffer texCoords = uvAccessor != null ? uvAccessor.getBufferFloat() : null;

            AccessorModel indicesAccessor = primitive.getIndices();
            int vertexCount = indicesAccessor != null ? indicesAccessor.getCount() : posAccessor.getCount();
            ByteBuffer indexBuffer = indicesAccessor != null ? indicesAccessor.getBufferByte() : null;
            int indexComponentType = indicesAccessor != null ? indicesAccessor.getComponentType() : 0;

            // 使用默认纹理（后续可在此处根据材质信息替换真实纹理）
            TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                    .apply(MissingTextureAtlasSprite.getLocation());

            List<BakedQuad> quadList = new ArrayList<>();
            
            // 计算模型包围盒，用于调试
            Vector3f min = new Vector3f(Float.MAX_VALUE);
            Vector3f max = new Vector3f(-Float.MAX_VALUE);
            
            for (int i = 0; i < vertexCount; i += 3) {
                int[] indices = new int[3];
                if (indexBuffer != null) {
                    indices[0] = readIndex(indexBuffer, indexComponentType, i);
                    indices[1] = readIndex(indexBuffer, indexComponentType, i + 1);
                    indices[2] = readIndex(indexBuffer, indexComponentType, i + 2);
                } else {
                    indices[0] = i; indices[1] = i + 1; indices[2] = i + 2;
                }
                // 收集顶点数据并构建BakedQuad
                int[] packedData = new int[DefaultVertexFormat.BLOCK.getIntegerSize() * 3];
                for (int j = 0; j < 3; j++) {
                    int idx = indices[j];
                    Vector3f pos = new Vector3f(positions.get(idx * 3), positions.get(idx * 3 + 1), positions.get(idx * 3 + 2));
                    min.min(pos);
                    max.max(pos);
                    Vector3f normal = normals != null ? new Vector3f(normals.get(idx * 3), normals.get(idx * 3 + 1), normals.get(idx * 3 + 2)) : new Vector3f(0, 1, 0);
                    float u = texCoords != null ? texCoords.get(idx * 2) : 0;
                    float v = texCoords != null ? texCoords.get(idx * 2 + 1) : 0;
                    putVertexData(packedData, j, pos, normal, u, v, sprite);
                }
                // 创建 BakedQuad，面方向默认为北（对于物品模型方向不重要）
                BakedQuad quad = new BakedQuad(packedData, -1, Direction.NORTH, sprite, true);
                quadList.add(quad);
            }
            
            // 输出模型尺寸信息，便于调试
            LOGGER.debug("Primitive bounds: min={}, max={}", min, max);
            float sizeX = max.x - min.x;
            float sizeY = max.y - min.y;
            float sizeZ = max.z - min.z;
            LOGGER.debug("Primitive size: X={}, Y={}, Z={}", sizeX, sizeY, sizeZ);
            
            return quadList;
        }

        private int readIndex(ByteBuffer buffer, int componentType, int offset) {
            switch (componentType) {
                case 5121: return buffer.get(offset) & 0xFF;
                case 5123: return buffer.getShort(offset * 2) & 0xFFFF;
                case 5125: return buffer.getInt(offset * 4);
                default: return 0;
            }
        }

        private void putVertexData(int[] packedData, int vertexIdx, Vector3f pos, Vector3f normal, float u, float v, TextureAtlasSprite sprite) {
            int offset = vertexIdx * DefaultVertexFormat.BLOCK.getIntegerSize();
            // 填充顶点数据 (位置, 颜色, UV, 光照, 法线等)
            packedData[offset] = Float.floatToRawIntBits(pos.x);
            packedData[offset + 1] = Float.floatToRawIntBits(pos.y);
            packedData[offset + 2] = Float.floatToRawIntBits(pos.z);
            packedData[offset + 3] = -1;
            packedData[offset + 4] = Float.floatToRawIntBits(sprite.getU(u));
            packedData[offset + 5] = Float.floatToRawIntBits(sprite.getV(v));
            // 光照信息简化处理
            packedData[offset + 6] = 0;
            packedData[offset + 7] = Float.floatToRawIntBits(normal.x);
            packedData[offset + 8] = Float.floatToRawIntBits(normal.y);
            packedData[offset + 9] = Float.floatToRawIntBits(normal.z);
        }

        // ----- 实现 IDynamicBakedModel 必要方法 -----
        @Override
        public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource rand, @NotNull ModelData data, @Nullable RenderType renderType) {
            return quads;
        }

        @Override
        public boolean useAmbientOcclusion() { return true; }
        @Override
        public boolean isGui3d() { return true; }
        @Override
        public boolean usesBlockLight() { return true; }
        @Override
        public boolean isCustomRenderer() { return false; }
        @Override
        public @NotNull TextureAtlasSprite getParticleIcon() {
            return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(MissingTextureAtlasSprite.getLocation());
        }
        @Override
        public @NotNull net.minecraft.client.renderer.block.model.ItemOverrides getOverrides() { return net.minecraft.client.renderer.block.model.ItemOverrides.EMPTY; }
    }
}
