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
import unsa.str.com.strenderer.client.GltfModelLoader;
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

            BakedModel model = null;
            if (data.getType() == STRendererAPI.ModelType.GLTF) {
                ResourceLocation modelLocation = ResourceLocation.parse(data.getModelPath());
                model = GltfModelLoader.loadModel(modelLocation);
            } else if (data.getType() == STRendererAPI.ModelType.OBJ) {
                model = OBJModelLoader.loadModel(data.getModelPath());
            }

            if (model != null) {
                poseStack.scale(0.5f, 0.5f, 0.5f);
                Minecraft.getInstance().getItemRenderer().renderModelLists(
                        model, stack, light, overlay, poseStack,
                        buffer.getBuffer(net.minecraft.client.renderer.RenderType.solid())
                );
            } else {
                LOGGER.warn("Failed to load model for item {}", itemId);
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
                break;
            case GROUND:
                poseStack.translate(0.5, 0, 0.5);
                break;
            case THIRD_PERSON_RIGHT_HAND:
            case THIRD_PERSON_LEFT_HAND:
                poseStack.translate(0.5, 0.5, 0.5);
                break;
            case FIRST_PERSON_RIGHT_HAND:
            case FIRST_PERSON_LEFT_HAND:
                poseStack.translate(0.5, 0.5, 0.5);
                break;
            default:
                break;
        }
    }
}
