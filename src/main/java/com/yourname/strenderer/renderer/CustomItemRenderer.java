package com.yourname.strenderer.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.yourname.strenderer.api.STRendererAPI;
import com.yourname.strenderer.client.GLTFModelLoader;
import com.yourname.strenderer.client.OBJModelLoader;
import de.javagl.jgltf.model.GltfModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

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
            if (data.getType() == STRendererAPI.ModelType.GLTF) {
                GltfModel model = GLTFModelLoader.loadModel(data.getModelPath());
                if (model != null) GLTFModelLoader.renderModel(model, poseStack, buffer, light, overlay);
            } else if (data.getType() == STRendererAPI.ModelType.OBJ) {
                BakedModel model = OBJModelLoader.loadModel(data.getModelPath());
                if (model != null) {
                    Minecraft.getInstance().getItemRenderer().renderModelLists(
                            model, ItemStack.EMPTY, light, overlay, poseStack,
                            buffer.getBuffer(net.minecraft.client.renderer.RenderType.solid()));
                }
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
            case GUI: poseStack.scale(0.5f, 0.5f, 0.5f); break;
            case GROUND: poseStack.scale(0.5f, 0.5f, 0.5f); break;
            case THIRD_PERSON_RIGHT_HAND:
            case THIRD_PERSON_LEFT_HAND: poseStack.scale(0.5f, 0.5f, 0.5f); break;
            case FIRST_PERSON_RIGHT_HAND:
            case FIRST_PERSON_LEFT_HAND: poseStack.scale(0.4f, 0.4f, 0.4f); break;
            default: break;
        }
    }
}
