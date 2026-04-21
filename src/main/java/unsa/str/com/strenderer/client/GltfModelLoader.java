package unsa.str.com.strenderer.client;

import com.mojang.logging.LogUtils;
import de.javagl.jgltf.model.*;
import de.javagl.jgltf.model.io.GltfModelReader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.neoforge.client.model.IModelBuilder;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GltfModelLoader {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ResourceLocation, BakedModel> CACHE = new ConcurrentHashMap<>();

    public static BakedModel load(ResourceLocation location, IGeometryBakingContext context) {
        return CACHE.computeIfAbsent(location, loc -> {
            try {
                ResourceManager manager = Minecraft.getInstance().getResourceManager();
                Resource resource = manager.getResource(loc).orElseThrow();
                try (InputStream is = resource.open()) {
                    GltfModelReader reader = new GltfModelReader();
                    GltfModel gltfModel = reader.readWithoutReferences(is);
                    return bakeModel(gltfModel, context);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to load glTF model {}", loc, e);
                return null;
            }
        });
    }

    private static BakedModel bakeModel(GltfModel gltfModel, IGeometryBakingContext context) {
        IModelBuilder<?> builder = IModelBuilder.of(context.useAmbientOcclusion(), context.useBlockLight(),
                context.isGui3d(), context.getTransforms(), context.getItemOverrides());

        TextureAtlasSprite missingSprite = Minecraft.getInstance().getTextureAtlas(ResourceLocation.withDefaultNamespace("textures/atlas/blocks.png"))
                .apply(ResourceLocation.withDefaultNamespace("missingno"));

        for (MeshModel mesh : gltfModel.getMeshes()) {
            for (MeshPrimitiveModel primitive : mesh.getMeshPrimitives()) {
                if (primitive.getMode() != 4) continue; // TRIANGLES only

                List<BakedQuad> quads = bakePrimitive(primitive, missingSprite);
                for (BakedQuad quad : quads) {
                    builder.addUnculledFace(quad);
                }
            }
        }

        return builder.build();
    }

    private static List<BakedQuad> bakePrimitive(MeshPrimitiveModel primitive, TextureAtlasSprite sprite) {
        Map<String, AccessorModel> attributes = primitive.getAttributes();
        AccessorModel posAccessor = attributes.get("POSITION");
        AccessorModel normalAccessor = attributes.get("NORMAL");
        AccessorModel uvAccessor = attributes.get("TEXCOORD_0");
        if (posAccessor == null) return Collections.emptyList();

        FloatBuffer positions = asFloatBuffer(posAccessor);
        FloatBuffer normals = asFloatBuffer(normalAccessor);
        FloatBuffer texCoords = asFloatBuffer(uvAccessor);

        AccessorModel indicesAccessor = primitive.getIndices();
        int vertexCount = indicesAccessor != null ? indicesAccessor.getCount() : posAccessor.getCount();
        ByteBuffer indexBuffer = indicesAccessor != null ? indicesAccessor.getBuffer() : null;
        int indexComponentType = indicesAccessor != null ? indicesAccessor.getComponentType() : 0;

        List<BakedQuad> quads = new ArrayList<>();
        QuadBakingVertexConsumer quadBaker = new QuadBakingVertexConsumer(quads::add);
        quadBaker.setSprite(sprite);

        for (int i = 0; i < vertexCount; i += 3) {
            int i0 = getIndex(indexBuffer, indexComponentType, i);
            int i1 = getIndex(indexBuffer, indexComponentType, i + 1);
            int i2 = getIndex(indexBuffer, indexComponentType, i + 2);

            Vector3f v0 = getVec3(positions, i0);
            Vector3f v1 = getVec3(positions, i1);
            Vector3f v2 = getVec3(positions, i2);

            Vector3f n0 = getVec3(normals, i0);
            Vector3f n1 = getVec3(normals, i1);
            Vector3f n2 = getVec3(normals, i2);

            float u0 = texCoords != null ? texCoords.get(i0 * 2) : 0;
            float v0 = texCoords != null ? texCoords.get(i0 * 2 + 1) : 0;
            float u1 = texCoords != null ? texCoords.get(i1 * 2) : 0;
            float v1 = texCoords != null ? texCoords.get(i1 * 2 + 1) : 0;
            float u2 = texCoords != null ? texCoords.get(i2 * 2) : 0;
            float v2 = texCoords != null ? texCoords.get(i2 * 2 + 1) : 0;

            putVertex(quadBaker, v0, n0, u0, v0, sprite);
            putVertex(quadBaker, v1, n1, u1, v1, sprite);
            putVertex(quadBaker, v2, n2, u2, v2, sprite);
        }
        return quads;
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
}
