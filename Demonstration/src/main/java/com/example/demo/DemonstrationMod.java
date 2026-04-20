package com.example.demo;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.slf4j.Logger;
import unsa.str.com.strenderer.api.STRendererAPI;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(DemonstrationMod.MODID)
public class DemonstrationMod {
    public static final String MODID = "demo";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredItem<Item> CUSTOM_ITEM = ITEMS.register("custom_item",
            () -> new Item(new Item.Properties()));

    public DemonstrationMod(IEventBus modEventBus, ModContainer modContainer) {
        ITEMS.register(modEventBus);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);
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
}
