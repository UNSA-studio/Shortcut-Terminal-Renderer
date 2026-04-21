package unsa.str.com.strenderer.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import unsa.str.com.strenderer.api.STRendererAPI;
import unsa.str.com.strenderer.client.OBJModelLoader;
import unsa.str.com.strenderer.client.gltf.GltfModelLoader;

public class CustomItemRenderer {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static boolean renderItem(ItemStack stack, ItemDisplayContext context,
                                     PoseStack poseStack, MultiBufferSource buffer,
                                     int light, int overlay) {
        ResourceLocation itemId = stack.getItemHolder().getKey().location();
        STRendererAPI.ModelData data = STRendererAPI.getInstance().getModelForItem(itemId);
        if (data == null) return false;

        try {
            poseStack.pushPose();
            applyTransform(poseStack, context);
            
            float scale = 1.0f;
            BakedModel model = null;
            
            if (data.getType() == STRendererAPI.ModelType.GLTF) {
                model = GltfModelLoader.loadModel(data.getModelPath());
                scale = 0.5f;
            } else if (data.getType() == STRendererAPI.ModelType.OBJ) {
                model = OBJModelLoader.loadModel(data.getModelPath());
                scale = 0.5f;
            }
            poseStack.scale(scale, scale, scale);
            
            if (model != null) {
                Minecraft.getInstance().getItemRenderer().renderModelLists(
                        model, stack, light, overlay, poseStack,
                        buffer.getBuffer(net.minecraft.client.renderer.RenderType.solid())
                );
                return true;
            } else {
                LOGGER.warn("Model not loaded for item {}: {}", itemId, data.getModelPath());
                return false;
            }
        } catch (Exception e) {
            LOGGER.error("Render failed for {}", itemId, e);
            return false;
        } finally {
            poseStack.popPose();
        }
    }

    private static void applyTransform(PoseStack poseStack, ItemDisplayContext context) {
        switch (context) {
            case GUI:
                poseStack.translate(0.5, 0.5, 0);
                poseStack.scale(1.0f, 1.0f, 1.0f);
                break;
            case GROUND:
                poseStack.translate(0.5, 0, 0.5);
                poseStack.scale(0.5f, 0.5f, 0.5f);
                break;
            case FIXED:
                poseStack.scale(1.0f, 1.0f, 1.0f);
                break;
            case THIRD_PERSON_RIGHT_HAND:
            case THIRD_PERSON_LEFT_HAND:
                poseStack.translate(0.5, 0.5, 0.5);
                poseStack.scale(0.6f, 0.6f, 0.6f);
                break;
            case FIRST_PERSON_RIGHT_HAND:
            case FIRST_PERSON_LEFT_HAND:
                poseStack.translate(0.5, 0.5, 0.5);
                poseStack.scale(0.5f, 0.5f, 0.5f);
                break;
            default:
                break;
        }
    }
}
