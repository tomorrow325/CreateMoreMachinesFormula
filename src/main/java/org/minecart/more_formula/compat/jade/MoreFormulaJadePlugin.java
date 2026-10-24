package org.minecart.more_formula.compat.jade;

import net.yxiao233.createmoremachines.api.content.crushing_wheel.CMMCrushingWheelBlock;
import net.yxiao233.createmoremachines.api.content.crushing_wheel.CMMCrushingWheelControllerBlock;
import net.yxiao233.createmoremachines.api.content.mechanical.deployer.CMMDeployerBlock;
import net.yxiao233.createmoremachines.api.content.mechanical.mixer.CMMMechanicalMixerBlock;
import net.yxiao233.createmoremachines.api.content.mechanical.press.CMMMechanicalPressBlock;
import net.yxiao233.createmoremachines.api.content.mechanical.saw.CMMMechanicalSawBlock;
import net.yxiao233.createmoremachines.api.content.spout.CMMSpoutBlock;
import net.minecraft.world.level.block.Block;
import org.minecart.more_formula.util.GoggleLines;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.IWailaPlugin;

/**
 * Jade（可选依赖）集成入口：把「机械等级 / 运行等级」两行加进 Jade 的方块信息 HUD。
 *
 * <h2>缺席时为什么不炸</h2>
 * 本类只被 Jade 自己实例化（启动时扫描 {@code @WailaPlugin} 注解）——Jade 不在场的
 * 运行环境里没有任何代码路径会加载本类。因此与 {@code compat/handmade}、
 * {@code compat/mekanicalcreate} 不同，这里<b>既不需要 mixin 配置也不需要
 * MixinConfigPlugin</b>；对应的纪律是：Jade 的任何 API 类型只允许出现在本包内。
 *
 * <h2>显示规则与护目镜同源</h2>
 * 内容、名单、破碎轮「轮子 → 控制器」反查全部来自 {@link GoggleLines#buildLines}
 * 的共享出口，与工程师护目镜逐字一致；不需要戴护目镜。
 */
@WailaPlugin(MoreFormulaJadePlugin.PLUGIN_ID)
public class MoreFormulaJadePlugin implements IWailaPlugin {

    public static final String PLUGIN_ID = "more_formula";

    /**
     * 按方块类注册而非注册到 {@code Block.class} 基类：回调只落在受支持机器上，
     * 不给 Jade 的每帧探测加无谓负担。
     *
     * <p>注册破碎轮「控制器」方块类是防御性的——它的碰撞/轮廓为空、准星通常
     * 命中不到；万一被命中（第三方测距/透视类工具），显示的正是控制器自身的等级。
     */
    @Override
    public void registerClient(IWailaClientRegistration registration) {
        MoreFormulaJadeProvider provider = new MoreFormulaJadeProvider();
        register(registration, provider, CMMMechanicalPressBlock.class);
        register(registration, provider, CMMMechanicalMixerBlock.class);
        register(registration, provider, CMMDeployerBlock.class);
        register(registration, provider, CMMSpoutBlock.class);
        register(registration, provider, CMMMechanicalSawBlock.class);
        register(registration, provider, CMMCrushingWheelBlock.class);
        register(registration, provider, CMMCrushingWheelControllerBlock.class);
    }

    private void register(IWailaClientRegistration registration, IBlockComponentProvider provider,
                          Class<? extends Block> blockClass) {
        registration.registerBlockComponent(provider, blockClass);
    }
}
