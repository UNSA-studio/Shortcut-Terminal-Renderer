package unsa.str.com.strenderer.client;

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
    private static final Map<ResourceLocation, BakedModel> CACHE = new ConcurrentHashMap<>();

    @Nullable
    public static BakedModel loadModel(ResourceLocation location) {
        return CACHE.computeIfAbsent(location, loc -> {
            try {
                ResourceManager manager = Minecraft.getInstance().getResourceManager();
                Resource resource = manager.getResource(loc).orElseThrow();
                try (InputStream is = resource.open()) {
                    GltfModelReader reader = new GltfModelReader();
                    // 直接读取，返回 GltfModel
                    GltfModel gltfModel = reader.readWithoutReferences(is);
                    return bakeModel(gltfModel);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to load glTF model {}", loc, e);
                return null;
            }
        });
    }

    private static BakedModel bakeModel(GltfModel gltfModel) {
        List<BakedQuad> allQuads = new ArrayList<>();

        // 方式1：通过 getNodes() 遍历（教程中的正确方式）
        for (NodeModel node : gltfModel.getNodes()) {
            MeshModel mesh = node.getMesh();
            if (mesh != null) {
                allQuads.addAll(bakeMesh(mesh));
            }
        }

        // 方式2：如果方式1没有获取到任何网格，尝试直接获取 meshes（兼容某些版本）
        if (allQuads.isEmpty()) {
            try {
                // 反射尝试 getMeshes()，如果存在则使用
                java.lang.reflect.Method method = gltfModel.getClass().getMethod("getMeshes");
                @SuppressWarnings("unchecked")
                List<MeshModel> meshes = (List<MeshModel>) method.invoke(gltfModel);
                for (MeshModel mesh : meshes) {
                    allQuads.addAll(bakeMesh(mesh));
                }
            } catch (Exception ignored) {
                // 方法不存在，忽略
            }
        }

        LOGGER.debug("Baked {} quads for glTF model", allQuads.size());
        return new GlTFBakedModel(allQuads);
    }

    private static List<BakedQuad> bakeMesh(MeshModel mesh) {
        List<BakedQuad> quads = new ArrayList<>();
        for (MeshPrimitiveModel primitive : mesh.getMeshPrimitives()) {
            if (primitive.getMode() != 4) { // 4 = TRIANGLES
                continue;
            }
            quads.addAll(bakePrimitive(primitive));
        }
        return quads;
    }

    private static List<BakedQuad> bakePrimitive(MeshPrimitiveModel primitive) {
        Map<String, AccessorModel> attributes = primitive.getAttributes();
        AccessorModel posAccessor = attributes.get("POSITION");
        AccessorModel normalAccessor = attributes.get("NORMAL");
        AccessorModel uvAccessor = attributes.get("TEXCOORD_0");
        if (posAccessor == null) return Collections.emptyList();

        FloatBuffer positions = asFloatBuffer(posAccessor);
        FloatBuffer normals = normalAccessor != null ? asFloatBuffer(normalAccessor) : null;
        FloatBuffer texCoords = uvAccessor != null ? asFloatBuffer(uvAccessor) : null;

        AccessorModel indicesAccessor = primitive.getIndices();
        int vertexCount = indicesAccessor != null ? indicesAccessor.getCount() : posAccessor.getCount();
        ByteBuffer indexBuffer = indicesAccessor != null ? indicesAccessor.getBuffer() : null;
        int indexComponentType = indicesAccessor != null ? indicesAccessor.getComponentType() : 0;

        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(MissingTextureAtlasSprite.getLocation());

        List<BakedQuad> quadList = new ArrayList<>();
        QuadBakingVertexConsumer baker = new QuadBakingVertexConsumer();
        baker.setSprite(sprite);
        baker.setDirection(Direction.NORTH);

        for (int i = 0; i < vertexCount; i += 3) {
            int i0 = getIndex(indexBuffer, indexComponentType, i);
            int i1 = getIndex(indexBuffer, indexComponentType, i + 1);
            int i2 = getIndex(indexBuffer, indexComponentType, i + 2);

            Vector3f p0 = getVec3(positions, i0);
            Vector3f p1 = getVec3(positions, i1);
            Vector3f p2 = getVec3(positions, i2);

            Vector3f n0 = getVec3(normals, i0);
            Vector3f n1 = getVec3(normals, i1);
            Vector3f n2 = getVec3(normals, i2);

            float u0 = texCoords != null ? texCoords.get(i0 * 2) : 0;
            float v0 = texCoords != null ? texCoords.get(i0 * 2 + 1) : 0;
            float u1 = texCoords != null ? texCoords.get(i1 * 2) : 0;
            float v1 = texCoords != null ? texCoords.get(i1 * 2 + 1) : 0;
            float u2 = texCoords != null ? texCoords.get(i2 * 2) : 0;
            float v2 = texCoords != null ? texCoords.get(i2 * 2 + 1) : 0;

            putVertex(baker, p0, n0, u0, v0, sprite);
            putVertex(baker, p1, n1, u1, v1, sprite);
            putVertex(baker, p2, n2, u2, v2, sprite);
        }
        return quadList;
    }

    private static FloatBuffer asFloatBuffer(AccessorModel accessor) {
        if (accessor == null) return null;
        return accessor.getBuffer().asFloatBuffer();
    }

    private static int getIndex(ByteBuffer buffer, int componentType, int offset) {
        if (buffer == null) return offset;
        switch (componentType) {
            case 5121: return buffer.get(offset) & 0xFF;
            case 5123: return buffer.getShort(offset * 2) & 0xFFFF;
            case 5125: return buffer.getInt(offset * 4);
            default: return 0;
        }
    }

    private static Vector3f getVec3(FloatBuffer buffer, int index) {
        if (buffer == null) return new Vector3f(0, 1, 0);
        return new Vector3f(buffer.get(index * 3), buffer.get(index * 3 + 1), buffer.get(index * 3 + 2));
    }

    private static void putVertex(QuadBakingVertexConsumer baker, Vector3f pos, Vector3f normal, float u, float v, TextureAtlasSprite sprite) {
        baker.vertex(pos.x, pos.y, pos.z)
                .color(1.0f, 1.0f, 1.0f, 1.0f)
                .uv(sprite.getU(u), sprite.getV(v))
                .uv2(0xF000F0)
                .normal(normal.x, normal.y, normal.z)
                .endVertex();
    }

    private static class GlTFBakedModel implements IDynamicBakedModel {
        private final List<BakedQuad> quads;

        public GlTFBakedModel(List<BakedQuad> quads) {
            this.quads = quads;
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
