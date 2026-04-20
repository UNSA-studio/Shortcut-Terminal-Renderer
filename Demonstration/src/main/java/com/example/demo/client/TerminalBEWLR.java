package com.example.demo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import unsa.str.com.strenderer.renderer.CustomItemRenderer;

@OnlyIn(Dist.CLIENT)
public class TerminalBEWLR extends BlockEntityWithoutLevelRenderer {

    public static final TerminalBEWLR INSTANCE = new TerminalBEWLR();

    private TerminalBEWLR() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext,
                             PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (CustomItemRenderer.renderItem(stack, displayContext, poseStack, buffer, packedLight, packedOverlay)) {
            return;
        }
    }
}
