package unsa.str.com.strenderer.renderer;

import unsa.str.com.strenderer.ShortcutTerminalRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderItemEvent;

@EventBusSubscriber(modid = ShortcutTerminalRenderer.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public class RenderHooks {
    @SubscribeEvent
    public static void onRenderItemPre(RenderItemEvent.Pre event) {
        boolean custom = CustomItemRenderer.renderItem(
                event.getItemStack(), event.getDisplayContext(), event.getPoseStack(),
                event.getMultiBufferSource(), event.getPackedLight(), event.getPackedOverlay());
        if (custom) event.setCanceled(true);
    }
}
