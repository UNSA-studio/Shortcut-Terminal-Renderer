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
import net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer;
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
                // 核心：借鉴MCglTF，通过getModel()获取真正的模型接口
                GltfModel model = reader.readWithoutReferences(is).getModel();
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
            // 遍历所有网格
            for (MeshModel mesh : model.getMeshes()) {
                for (MeshPrimitiveModel primitive : mesh.getMeshPrimitives()) {
                    // 三角形模式的值是 4
                    if (primitive.getMode() != 4) {
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

            // 获取数据缓冲区
            FloatBuffer positions = posAccessor.getBuffer().asFloatBuffer();
            FloatBuffer normals = normalAccessor != null ? normalAccessor.getBuffer().asFloatBuffer() : null;
            FloatBuffer texCoords = uvAccessor != null ? uvAccessor.getBuffer().asFloatBuffer() : null;

            AccessorModel indicesAccessor = primitive.getIndices();
            int vertexCount = indicesAccessor != null ? indicesAccessor.getCount() : posAccessor.getCount();
            ByteBuffer indexBuffer = indicesAccessor != null ? indicesAccessor.getBuffer() : null;
            int indexComponentType = indicesAccessor != null ? indicesAccessor.getComponentType() : 0;

            // 使用默认纹理
            TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                    .apply(MissingTextureAtlasSprite.getLocation());

            List<BakedQuad> quadList = new ArrayList<>();
            for (int i = 0; i < vertexCount; i += 3) {
                int[] indices = new int[3];
                if (indexBuffer != null) {
                    indices[0] = readIndex(indexBuffer, indexComponentType, i);
                    indices[1] = readIndex(indexBuffer, indexComponentType, i + 1);
                    indices[2] = readIndex(indexBuffer, indexComponentType, i + 2);
                } else {
                    indices[0] = i; indices[1] = i + 1; indices[2] = i + 2;
                }
                
                // 使用QuadBakingVertexConsumer简化顶点数据组装
                QuadBakingVertexConsumer baker = new QuadBakingVertexConsumer(q -> quadList.add(q));
                baker.setSprite(sprite);
                baker.setDirection(Direction.NORTH);
                
                for (int j = 0; j < 3; j++) {
                    int idx = indices[j];
                    Vector3f pos = new Vector3f(positions.get(idx * 3), positions.get(idx * 3 + 1), positions.get(idx * 3 + 2));
                    Vector3f normal = normals != null ? new Vector3f(normals.get(idx * 3), normals.get(idx * 3 + 1), normals.get(idx * 3 + 2)) : new Vector3f(0, 1, 0);
                    float u = texCoords != null ? texCoords.get(idx * 2) : 0;
                    float v = texCoords != null ? texCoords.get(idx * 2 + 1) : 0;
                    
                    baker.vertex(pos.x, pos.y, pos.z)
                         .color(255, 255, 255, 255)
                         .uv(sprite.getU(u), sprite.getV(v))
                         .uv2(0xF000F0) // 默认光照值
                         .normal(normal.x, normal.y, normal.z)
                         .endVertex();
                }
                // 生成四边形
                // QuadBakingVertexConsumer会自动处理
            }
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
