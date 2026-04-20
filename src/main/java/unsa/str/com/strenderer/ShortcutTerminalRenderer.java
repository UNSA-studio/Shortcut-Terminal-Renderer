package unsa.str.com.strenderer;

import com.mojang.logging.LogUtils;
import unsa.str.com.strenderer.api.STRendererAPI;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(ShortcutTerminalRenderer.MODID)
public class ShortcutTerminalRenderer {
    public static final String MODID = "strenderer";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ShortcutTerminalRenderer(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Shortcut Terminal Renderer is initializing...");
        STRendererAPI.setInstance(new unsa.str.com.strenderer.api.STRendererAPIImpl());
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);
        NeoForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Shortcut Terminal Renderer common setup completed.");
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("Shortcut Terminal Renderer client setup completed.");
    }
}
