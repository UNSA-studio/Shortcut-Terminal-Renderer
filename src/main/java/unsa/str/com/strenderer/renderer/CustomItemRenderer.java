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
import unsa.str.com.strenderer.client.GLTFModelLoader;
import unsa.str.com.strenderer.client.OBJModelLoader;

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
            
            // 根据模型类型决定缩放比例
            float scale = 1.0f;
            if (data.getType() == STRendererAPI.ModelType.GLTF) {
                // glTF模型通常需要缩小（如果太大）或放大（如果太小）
                scale = 0.5f; // 可根据实际模型大小调整
            } else if (data.getType() == STRendererAPI.ModelType.OBJ) {
                scale = 0.5f;
            }
            poseStack.scale(scale, scale, scale);
            
            BakedModel model = null;
            if (data.getType() == STRendererAPI.ModelType.GLTF) {
                model = GLTFModelLoader.loadModel(data.getModelPath());
                if (model != null) {
                    LOGGER.debug("GLTF model loaded successfully: {}", data.getModelPath());
                } else {
                    LOGGER.warn("GLTF model failed to load: {}", data.getModelPath());
                }
            } else if (data.getType() == STRendererAPI.ModelType.OBJ) {
                model = OBJModelLoader.loadModel(data.getModelPath());
                if (model != null) {
                    LOGGER.debug("OBJ model loaded successfully: {}", data.getModelPath());
                } else {
                    LOGGER.warn("OBJ model failed to load: {}", data.getModelPath());
                }
            }

            if (model != null) {
                Minecraft.getInstance().getItemRenderer().renderModelLists(
                        model, stack, light, overlay, poseStack,
                        buffer.getBuffer(net.minecraft.client.renderer.RenderType.solid())
                );
            } else {
                LOGGER.warn("Model not loaded for item {}: {}", itemId, data.getModelPath());
            }
            poseStack.popPose();
            return true;
        } catch (Exception e) {
            LOGGER.error("Render failed for {}", itemId, e);
            poseStack.popPose();
            return false;
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
