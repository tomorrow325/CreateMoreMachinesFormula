package org.minecart.more_formula;

import com.mojang.logging.LogUtils;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.minecart.more_formula.compat.mekanicalcreate.MekanicalCreateSpeedConfig;
import org.slf4j.Logger;

@Mod(More_formula.MODID)
public class More_formula {
    public static final String MODID = "more_formula";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);

    public static final DeferredItem<Item> INCOMPLETE_TEST_PACKAGE =
            ITEMS.registerSimpleItem("incomplete_test_package");

    public More_formula(IEventBus modEventBus, ModContainer modContainer) {
        ITEMS.register(modEventBus);
        // 「运行等级」附件的注册（提供 NBT 持久化与客户端同步）。
        MachinePriority.register(modEventBus);
        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER,
                MekanicalCreateSpeedConfig.SPEC);
    }
}
