package com.example.demo;

import com.example.demo.client.TerminalBEWLR;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.slf4j.Logger;
import unsa.str.com.strenderer.api.STRendererAPI;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(DemonstrationMod.MODID)
public class DemonstrationMod {
    public static final String MODID = "demo";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredItem<Item> CUSTOM_ITEM = ITEMS.register("custom_item",
            () -> new CustomTerminalItem(new Item.Properties()));

    public DemonstrationMod(IEventBus modEventBus, ModContainer modContainer) {
        ITEMS.register(modEventBus);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);
        modEventBus.addListener(this::addCreativeTab);
        modEventBus.addListener(this::registerClientExtensions);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Demonstration Mod common setup.");
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("Demonstration Mod client setup. Registering model...");

        STRendererAPI.getInstance().registerGLTFModel(
                ResourceLocation.fromNamespaceAndPath(MODID, "models/terminal.gltf"),
                MODID + ":models/terminal.gltf"
        );

        STRendererAPI.getInstance().bindItemToModel(
                ResourceLocation.fromNamespaceAndPath(MODID, "custom_item"),
                ResourceLocation.fromNamespaceAndPath(MODID, "models/terminal.gltf")
        );

        LOGGER.info("Model bound to custom_item successfully.");
    }

    private void addCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(CUSTOM_ITEM);
        }
    }

    private void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return TerminalBEWLR.INSTANCE;
            }
        }, CUSTOM_ITEM.get());
    }

    public static class CustomTerminalItem extends Item {
        public CustomTerminalItem(Properties properties) {
            super(properties);
        }
    }
}
