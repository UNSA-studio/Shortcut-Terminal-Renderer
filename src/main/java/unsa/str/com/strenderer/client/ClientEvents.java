import net.minecraft.client.Minecraft;
package unsa.str.com.strenderer.client;

import unsa.str.com.strenderer.ShortcutTerminalRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = ShortcutTerminalRenderer.MODID, value = Dist.CLIENT)
public class ClientEvents {
    private static boolean modelsRegistered = false;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!modelsRegistered && Minecraft.getInstance().level != null) {
            modelsRegistered = true;
            ShortcutTerminalRenderer.LOGGER.info("Client world loaded, models ready for registration");
        }
    }

    @SubscribeEvent
    public static void onRenderFrame(RenderFrameEvent.Post event) {}

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {}
}
// 修复缺少的 import
