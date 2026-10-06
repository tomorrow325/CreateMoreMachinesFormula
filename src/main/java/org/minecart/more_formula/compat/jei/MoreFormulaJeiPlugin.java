package org.minecart.more_formula.compat.jei;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.compat.jei.category.ItemApplicationCategory;
import com.tterrag.registrate.util.entry.BlockEntry;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.loading.FMLLoader;
import net.yxiao233.createmoremachines.api.registry.BuiltInAdvancedMachineTypes;
import org.jetbrains.annotations.NotNull;
import org.minecart.more_formula.Config;
import org.minecart.more_formula.More_formula;
import org.minecart.more_formula.compat.jei.category.TieredCrushingCategory;
import org.minecart.more_formula.compat.jei.category.TieredCuttingCategory;
import org.minecart.more_formula.compat.jei.category.TieredDeployingCategory;
import org.minecart.more_formula.compat.jei.category.TieredMillingCategory;
import org.minecart.more_formula.compat.jei.category.TieredMixingCategory;
import org.minecart.more_formula.compat.jei.category.TieredPackingCategory;
import org.minecart.more_formula.compat.jei.category.TieredPressingCategory;
import org.minecart.more_formula.compat.jei.category.TieredSequencedAssemblyCategory;
import org.minecart.more_formula.compat.jei.category.TieredSpoutCategory;
import org.minecart.more_formula.compat.jei.category.sequenced.TieredMachineContext;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

@JeiPlugin
public class MoreFormulaJeiPlugin implements IModPlugin {
    @SuppressWarnings("rawtypes")
    private interface CatFactory {
        CreateRecipeCategory<?> create(CreateRecipeCategory.Info<?> info, BlockEntry<? extends Block> machine,
                                        BlockEntry<? extends Block> basin, String tierName);
    }

    @SuppressWarnings("rawtypes")
    private record Kind(AllRecipeTypes type, String categoryPath, String vanillaCategoryPath, int bgWidth, int bgHeight,
                        BuiltInAdvancedMachineTypes.AdvancedMachineType<?>[] catalystTypes, CatFactory factory) {

        /** {@code vanillaCategoryPath} 与 {@code categoryPath} 相同的分类（除锯切外都是）。 */
        Kind(AllRecipeTypes type, String categoryPath, int bgWidth, int bgHeight,
             BuiltInAdvancedMachineTypes.AdvancedMachineType<?>[] catalystTypes, CatFactory factory) {
            this(type, categoryPath, categoryPath, bgWidth, bgHeight, catalystTypes, factory);
        }
    }

    /**
     * 门槛的取值范围是封闭的（1..4 四级 + -1 创造级），因此分级分类的“全集”也是封闭的。
     *
     * <p>按全集建分类、而不是只按「当前已知等级」建，原因：
     * {@code registerCategories} 的调用时机由 JEI 决定（进入世界、以及客户端资源重载），
     * 而等级是 KubeJS 脚本执行时才写进 {@link Config} 的 —— 两者的先后顺序并不受本模组控制。
     * 只要出现「JEI 建分类时某等级还没被脚本注册」的情况，那个等级就永远没有分类。
     * 建全集后这个时序问题就不存在了：分类始终都在，运行时再按当前门槛表隐藏/取消隐藏
     * （见 {@link #onRuntimeAvailable}），并把晚到的等级所需配方补交上去。
     */
    private static final int[] ALL_TIERS = {Config.CREATIVE_TIER, 1, 2, 3, 4};

    private final List<CreateRecipeCategory<?>> tieredCategories = new ArrayList<>();
    /**
     * tier → (配方类型 categoryPath → 该等级该类型的分类)。
     * 用 categoryPath 作二级键，运行时的隐藏/补配方都能精确定位，不需要做字符串猜测。
     */
    private final Map<Integer, Map<String, CreateRecipeCategory<?>>> categoriesByTier = new LinkedHashMap<>();
    /** 在 registerRecipes 阶段真正提交过配方的等级（避免运行时重复 addRecipes 造成重复条目）。 */
    private final Set<Integer> tiersRegisteredWithRecipes = new LinkedHashSet<>();
    private List<Kind> kinds;

    /** CMMM（可选依赖）解析出的分级机器条目；{@code null} = 不在场或桥接失败。 */
    private record CMMMMachines(BuiltInAdvancedMachineTypes.AdvancedMachineType<?> crushingWheel,
                                BuiltInAdvancedMachineTypes.AdvancedMachineType<?> mechanicalSaw) {
    }

    private static final String CMMM_MODID = "createmoremoremachines";
    private static final String CMMM_JEI_BRIDGE =
            "org.minecart.more_formula.compat.createmoremoremachines.CMMMJeiBridge";
    private static volatile boolean cmmmBridgeResolved;
    private static CMMMMachines cmmmMachines;
    private static boolean cmmmBridgeWarned;

    @Override
    public @NotNull ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(More_formula.MODID, "jei");
    }

    private List<Kind> kinds() {
        if (kinds == null) {
            // 先收集后条件追加：CMMM（可选依赖）缺席时，下面的集合与历史上只认 CMM 的版本逐项相同。
            CMMMMachines cmmm = cmmmBridge();
            List<Kind> collected = new ArrayList<>();
            collected.add(new Kind(AllRecipeTypes.PRESSING, "pressing", 177, 70,
                    machines(BuiltInAdvancedMachineTypes.PRESS),
                    (info, machine, basin, tierName) -> new TieredPressingCategory(castInfo(info), state(machine), basinState(basin))));
            collected.add(new Kind(AllRecipeTypes.MIXING, "mixing", 177, 103,
                    machines(BuiltInAdvancedMachineTypes.MIXER, BuiltInAdvancedMachineTypes.BASIN),
                    (info, machine, basin, tierName) -> new TieredMixingCategory(castInfo(info), state(machine), basinState(basin), headPartial(tierName))));
            collected.add(new Kind(AllRecipeTypes.COMPACTING, "packing", 177, 103,
                    machines(BuiltInAdvancedMachineTypes.PRESS, BuiltInAdvancedMachineTypes.BASIN),
                    (info, machine, basin, tierName) -> new TieredPackingCategory(castInfo(info), state(machine), basinState(basin))));
            collected.add(new Kind(AllRecipeTypes.FILLING, "spout_filling", 177, 70,
                    machines(BuiltInAdvancedMachineTypes.SPOUT),
                    (info, machine, basin, tierName) -> new TieredSpoutCategory(castInfo(info), state(machine), spoutPartials(tierName), depotState(tierName))));
            collected.add(new Kind(AllRecipeTypes.DEPLOYING, "deploying", 177, 70,
                    machines(BuiltInAdvancedMachineTypes.DEPLOYER),
                    (info, machine, basin, tierName) -> new TieredDeployingCategory(castInfo(info), state(machine), depotState(tierName))));
            collected.add(new Kind(AllRecipeTypes.ITEM_APPLICATION, "item_application", 177, 60,
                    machines(BuiltInAdvancedMachineTypes.DEPLOYER),
                    (info, machine, basin, tierName) -> new ItemApplicationCategory(castInfo(info))));
            collected.add(new Kind(AllRecipeTypes.SEQUENCED_ASSEMBLY, "sequenced_assembly", 180, 115,
                    // 序列装配常含锯切工序：CMMM 在场时分级锯是真实执行者，追加为催化剂。
                    cmmm == null
                            ? machines(BuiltInAdvancedMachineTypes.PRESS, BuiltInAdvancedMachineTypes.SPOUT, BuiltInAdvancedMachineTypes.DEPLOYER)
                            : machines(BuiltInAdvancedMachineTypes.PRESS, BuiltInAdvancedMachineTypes.SPOUT, BuiltInAdvancedMachineTypes.DEPLOYER,
                            cmmm.mechanicalSaw()),
                    (info, machine, basin, tierName) -> new TieredSequencedAssemblyCategory(castInfo(info), sequencedContext(tierName))));
            if (cmmm != null) {
                // 三个分级分类只在 CMMM 的分级机器真实存在时才建；
                // milling 由分级破碎轮控制器执行（CMMM 无分级磨石），催化剂同为分级破碎轮。
                collected.add(new Kind(AllRecipeTypes.CRUSHING, "crushing", 177, 100,
                        machines(cmmm.crushingWheel()),
                        (info, machine, basin, tierName) -> new TieredCrushingCategory(castInfo(info), state(machine))));
                collected.add(new Kind(AllRecipeTypes.MILLING, "milling", 177, 53,
                        machines(cmmm.crushingWheel()),
                        (info, machine, basin, tierName) -> new TieredMillingCategory(castInfo(info), state(machine))));
                collected.add(new Kind(AllRecipeTypes.CUTTING, "cutting", "sawing", 177, 70,
                        machines(cmmm.mechanicalSaw()),
                        (info, machine, basin, tierName) -> new TieredCuttingCategory(castInfo(info), state(machine))));
            }
            kinds = List.copyOf(collected);
        }
        return kinds;
    }

    @Override
    public void registerCategories(@NotNull IRecipeCategoryRegistration registration) {
        tieredCategories.clear();
        categoriesByTier.clear();
        tiersRegisteredWithRecipes.clear();

        Set<Integer> known = new LinkedHashSet<>(Config.getKnownTiers());
        if (known.isEmpty()) {
            // 这个整合包没配任何门槛 —— 不往 JEI 里塞一堆空分类。
            return;
        }

        // 建「全集」，这样脚本稍后新增的等级也有现成分类可用。
        Set<Integer> tiers = new LinkedHashSet<>(ALL_TIERS.length + known.size());
        for (int tier : ALL_TIERS) {
            tiers.add(tier);
        }
        tiers.addAll(known);

        for (int tier : tiers) {
            Map<String, CreateRecipeCategory<?>> perTier = new LinkedHashMap<>();
            for (Kind kind : kinds()) {
                BlockEntry<? extends Block> machine = machine(kind.type(), tier);
                if (machine == null) {
                    continue;
                }
                CreateRecipeCategory<?> category = build(kind, tier, machine, basin(tier));
                if (category != null) {
                    perTier.put(kind.categoryPath(), category);
                    tieredCategories.add(category);
                }
            }
            if (!perTier.isEmpty()) {
                categoriesByTier.put(tier, perTier);
            }
        }
        registration.addRecipeCategories(tieredCategories.toArray(new IRecipeCategory[0]));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private CreateRecipeCategory<?> build(Kind kind, int tier, BlockEntry<? extends Block> machine, BlockEntry<? extends Block> basin) {
        ResourceLocation uid = ResourceLocation.fromNamespaceAndPath(More_formula.MODID,
                "tiered/" + kind.categoryPath() + "_" + tier);

        Component title = Component.translatable("more_formula.jei.tiered_title",
                Component.translatable("more_formula.tier." + tier),
                // 锯切的语言键是 create.recipe.sawing（Create 没有 create.recipe.cutting），其余同名。
                Component.translatable("create.recipe." + kind.vanillaCategoryPath()));

        Item iconItem = machine.get().asItem();
        CreateRecipeCategory.Info info = new CreateRecipeCategory.Info(
                RecipeType.createRecipeHolderType(uid),
                title,
                new com.simibubi.create.compat.jei.EmptyBackground(kind.bgWidth(), kind.bgHeight()),
                new com.simibubi.create.compat.jei.ItemIcon(() -> new ItemStack(iconItem)),
                () -> gatedHolders(kind.type(), tier),
                unlockedCatalysts(kind.catalystTypes(), tier)
        );
        return kind.factory().create(info, machine, basin, tierName(tier));
    }

    @Override
    public void registerRecipes(@NotNull IRecipeRegistration registration) {
        for (CreateRecipeCategory<?> category : tieredCategories) {
            category.registerRecipes(registration);
        }
        // 建分类时就已经有配方的等级，运行时不重复提交。
        List<Integer> known = Config.getKnownTiers();
        for (int tier : categoriesByTier.keySet()) {
            if (known.contains(tier)) {
                tiersRegisteredWithRecipes.add(tier);
            }
        }
    }

    @Override
    public void registerRecipeCatalysts(@NotNull IRecipeCatalystRegistration registration) {
        tieredCategories.forEach(category -> category.registerCatalysts(registration));
    }

    /**
     * 每次 JEI 就绪都会调用这里，因此它是「让显示跟上当前门槛表」的时机。
     *
     * <p>做三件事：
     * <ol>
     *   <li>把原版 Create 分类里已被设门槛的配方隐藏掉（避免同一个配方在两处出现）；</li>
     *   <li>按当前门槛表隐藏/取消隐藏分级分类 —— 分类是按全集建的，因此某个等级
     *       「在 JEI 建分类之后才被脚本注册」也能在这里被点亮；</li>
     *   <li>为这类晚到的等级补交配方（JEI 没有清空配方的 API，
     *       所以只对尚未提交过的等级补齐，绝不重复提交）。</li>
     * </ol>
     *
     * <p>已知限制：本模组不做 JEI 配方的增量同步。若某等级的配方是在
     * {@code registerRecipes} 之后才被改动（例如 {@code /reload} 改了同一个已注册等级的配方），
     * 这里只负责「补新等级」，不会替换已提交的配方列表；JEI 自身在其启动流程中重建配方。
     */
    @Override
    public void onRuntimeAvailable(@NotNull IJeiRuntime runtime) {
        Level level = Minecraft.getInstance().level;
        if (level == null || tieredCategories.isEmpty()) {
            return;
        }
        IRecipeManager recipeManager = runtime.getRecipeManager();
        List<Integer> known = Config.getKnownTiers();

        // 1) 原版分类里被分级的配方 → 隐藏
        for (Kind kind : kinds()) {
            Collection<?> gated = gatedAboveTier(level, kind.type());
            if (gated.isEmpty()) {
                continue;
            }
            // 锯切的原版 JEI 分类 id 是 create:sawing（create.recipe.cutting 不存在），
            // 其余分类与 categoryPath 同名，因此统一走 vanillaCategoryPath。
            RecipeType<?> vanillaCategory = RecipeType.createRecipeHolderType(
                    ResourceLocation.fromNamespaceAndPath("create", kind.vanillaCategoryPath()));
            if (!categoryExists(runtime, vanillaCategory)) {
                continue;
            }
            hideAll(recipeManager, vanillaCategory, gated);
        }

        // 2) 分级分类按当前门槛表隐藏 / 取消隐藏
        for (Map.Entry<Integer, Map<String, CreateRecipeCategory<?>>> entry : categoriesByTier.entrySet()) {
            boolean inUse = known.contains(entry.getKey());
            for (CreateRecipeCategory<?> category : entry.getValue().values()) {
                RecipeType<?> type = category.getRecipeType();
                if (inUse) {
                    recipeManager.unhideRecipeCategory(type);
                } else {
                    recipeManager.hideRecipeCategory(type);
                }
            }
        }

        // 3) 启动时还没有、之后才出现的等级 → 补交配方
        for (Map.Entry<Integer, Map<String, CreateRecipeCategory<?>>> entry : categoriesByTier.entrySet()) {
            int tier = entry.getKey();
            if (!known.contains(tier) || tiersRegisteredWithRecipes.contains(tier)) {
                continue;
            }
            for (Kind kind : kinds()) {
                CreateRecipeCategory<?> category = entry.getValue().get(kind.categoryPath());
                if (category == null) {
                    continue;
                }
                List<RecipeHolder<?>> holders = gatedHolders(kind.type(), tier);
                if (!holders.isEmpty()) {
                    addHolders(recipeManager, category.getRecipeType(), holders);
                }
            }
        }
        tiersRegisteredWithRecipes.addAll(known);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addHolders(IRecipeManager manager, RecipeType type, List<RecipeHolder<?>> holders) {
        manager.addRecipes(type, holders);
    }

    private static boolean categoryExists(IJeiRuntime runtime, RecipeType<?> jeiType) {
        return runtime.getRecipeManager()
                .createRecipeCategoryLookup()
                .limitTypes(List.of(jeiType))
                .includeHidden()
                .get()
                .findAny()
                .isPresent();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Collection<?> gatedAboveTier(Level level, AllRecipeTypes type) {
        // 注意这里的 RecipeType 是 Minecraft 的（mezz.jei.api.recipe.RecipeType 在文件上是同名 import），
        // 所以用全限定名区分。
        net.minecraft.world.item.crafting.RecipeType vanillaType = type.getType();
        List holders = level.getRecipeManager().getAllRecipesFor(vanillaType);
        return ((List<RecipeHolder<?>>) holders).stream()
                .filter(holder -> Config.getRequiredTier(holder.id()) != 0)
                .toList();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<RecipeHolder<?>> gatedHolders(AllRecipeTypes type, int tier) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return List.of();
        }
        net.minecraft.world.item.crafting.RecipeType vanillaType = type.getType();
        List holders = level.getRecipeManager().getAllRecipesFor(vanillaType);
        return ((List<RecipeHolder<?>>) holders).stream()
                .filter(holder -> Config.getRequiredTier(holder.id()) == tier)
                .toList();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void hideAll(IRecipeManager manager, RecipeType jeiType, Collection recipes) {
        manager.hideRecipes(jeiType, recipes);
    }

    @SuppressWarnings("unchecked")
    private static <T> T castInfo(Object info) {
        return (T) info;
    }

    private static BlockState state(BlockEntry<? extends Block> entry) {
        return entry == null ? null : entry.get().defaultBlockState();
    }

    private static BlockState basinState(BlockEntry<? extends Block> entry) {
        return entry != null ? entry.get().defaultBlockState() : AllBlocks.BASIN.getDefaultState();
    }

    private static BlockState depotState(String tierName) {
        return safeState(entry(BuiltInAdvancedMachineTypes.DEPOT, tierName), AllBlocks.DEPOT.getDefaultState());
    }

    private static BlockState safeState(BlockEntry<? extends Block> entry, BlockState fallback) {
        return entry == null ? fallback : entry.get().defaultBlockState();
    }

    private static PartialModel headPartial(String tierName) {
        if (entry(BuiltInAdvancedMachineTypes.MIXER, tierName) == null) {
            return AllPartialModels.MECHANICAL_MIXER_HEAD;
        }
        return PartialModel.of(ResourceLocation.fromNamespaceAndPath("createmoremachines",
                "block/mechanical_mixer/head/" + tierName + "_mechanical_mixer_head"));
    }

    private static PartialModel[] spoutPartials(String tierName) {
        if (entry(BuiltInAdvancedMachineTypes.SPOUT, tierName) == null) {
            return new PartialModel[]{AllPartialModels.SPOUT_TOP, AllPartialModels.SPOUT_MIDDLE, AllPartialModels.SPOUT_BOTTOM};
        }
        return new PartialModel[]{
                PartialModel.of(ResourceLocation.fromNamespaceAndPath("createmoremachines", "block/spout/top/" + tierName + "_spout_top")),
                PartialModel.of(ResourceLocation.fromNamespaceAndPath("createmoremachines", "block/spout/middle/" + tierName + "_spout_middle")),
                PartialModel.of(ResourceLocation.fromNamespaceAndPath("createmoremachines", "block/spout/bottom/" + tierName + "_spout_bottom"))
        };
    }

    private static TieredMachineContext sequencedContext(String tierName) {
        // CMMM 的分级轮/锯与 CMM 共享 CMMTier 实例，entry() 同键查找（createmoremachines:<tier>）
        // 即可拿到 CMMM 注册的方块；该档未注册时 entry 为 null → 回退原版渲染。
        CMMMMachines cmmm = cmmmBridge();
        return new TieredMachineContext(
                safeState(entry(BuiltInAdvancedMachineTypes.PRESS, tierName), AllBlocks.MECHANICAL_PRESS.getDefaultState()),
                safeState(entry(BuiltInAdvancedMachineTypes.SPOUT, tierName), AllBlocks.SPOUT.getDefaultState()),
                spoutPartials(tierName),
                safeState(entry(BuiltInAdvancedMachineTypes.DEPLOYER, tierName), AllBlocks.DEPLOYER.getDefaultState()),
                depotState(tierName),
                basinState(entry(BuiltInAdvancedMachineTypes.BASIN, tierName)),
                cmmm == null ? null : safeState(entry(cmmm.mechanicalSaw(), tierName), null)
        );
    }

    /**
     * CMMM（可选依赖）的分级破碎轮/分级锯注册表条目。
     * 返回 {@code null} 表示「CMMM 不在场，或桥接类加载/自检失败」—— 两种情况都按
     * 「没有分级机器」处理：不建 crushing/milling/cutting 分类、序列装配催化剂不追加锯、
     * 锯切工序回退原版渲染。
     *
     * <p>为什么反射而不是直接 import：本类随 JEI 常驻加载，任何对 CMMM 类型的直接引用
     * 都会在未安装 CMMM 时 NoClassDefFoundError（对 CMM 本体的引用则是安全的 —— 必需依赖）。
     * 因此先用 {@code FMLLoader.getLoadingModList()} 判在场（与 mixin 配置插件同一写法），
     * 才 {@code Class.forName} 加载 {@code CMMMJeiBridge}（全模组唯一直接引用 CMMM
     * 注册表条目的地方），并对两个方法各做一次非空自检；失败只 warn 一次并永久按缺席处理
     * （镜像 {@code TierHelper} 的 warn-once 写法）。
     */
    private static CMMMMachines cmmmBridge() {
        if (!cmmmBridgeResolved) {
            cmmmBridgeResolved = true;
            cmmmMachines = resolveCMMMBridge();
        }
        return cmmmMachines;
    }

    private static CMMMMachines resolveCMMMBridge() {
        try {
            if (FMLLoader.getLoadingModList() == null
                    || FMLLoader.getLoadingModList().getModFileById(CMMM_MODID) == null) {
                return null;
            }
            Class<?> bridge = Class.forName(CMMM_JEI_BRIDGE, true, MoreFormulaJeiPlugin.class.getClassLoader());
            Method crushingWheel = bridge.getMethod("crushingWheel");
            Method mechanicalSaw = bridge.getMethod("mechanicalSaw");
            Object wheel = crushingWheel.invoke(null);
            Object saw = mechanicalSaw.invoke(null);
            if (!(wheel instanceof BuiltInAdvancedMachineTypes.AdvancedMachineType<?> crushing)
                    || !(saw instanceof BuiltInAdvancedMachineTypes.AdvancedMachineType<?> sawMachine)) {
                throw new IllegalStateException("CMMMJeiBridge returned a null machine entry");
            }
            return new CMMMMachines(crushing, sawMachine);
        } catch (Throwable t) {
            if (!cmmmBridgeWarned) {
                cmmmBridgeWarned = true;
                More_formula.LOGGER.warn("more_formula: failed to load the CreateMoreMoreMachines JEI bridge,"
                        + " tiered crushing/milling/cutting categories stay hidden", t);
            }
            return null;
        }
    }

    private static BlockEntry<? extends Block> basin(int tier) {
        return entry(BuiltInAdvancedMachineTypes.BASIN, tierName(tier));
    }

    private static BlockEntry<? extends Block> machine(AllRecipeTypes type, int tier) {
        CMMMMachines cmmm = cmmmBridge();
        BuiltInAdvancedMachineTypes.AdvancedMachineType<?> advancedType = switch (type) {
            case PRESSING, COMPACTING, SEQUENCED_ASSEMBLY -> BuiltInAdvancedMachineTypes.PRESS;
            case MIXING -> BuiltInAdvancedMachineTypes.MIXER;
            case FILLING -> BuiltInAdvancedMachineTypes.SPOUT;
            case DEPLOYING, ITEM_APPLICATION -> BuiltInAdvancedMachineTypes.DEPLOYER;
            // 分级破碎轮/分级锯由可选依赖 CMMM 提供（milling 由分级破碎轮控制器执行，
            // 见 TieredMillingCategory）；CMMM 缺席时为 null = 不建该分类。
            case CRUSHING, MILLING -> cmmm == null ? null : cmmm.crushingWheel();
            case CUTTING -> cmmm == null ? null : cmmm.mechanicalSaw();
            default -> null;
        };
        if (advancedType == null) {
            return null;
        }
        return entry(advancedType, tierName(tier));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockEntry<? extends Block> entry(BuiltInAdvancedMachineTypes.AdvancedMachineType machineType, String tierName) {
        Object entry = machineType.getAdvancedMechanicals()
                .get(ResourceLocation.fromNamespaceAndPath("createmoremachines", tierName));
        return entry instanceof BlockEntry<?> blockEntry ? blockEntry : null;
    }

    private static String tierName(int tier) {
        return switch (tier) {
            case -1 -> "creative";
            case 1 -> "brass";
            case 2 -> "netherite";
            case 3 -> "end";
            case 4 -> "beyond";
            default -> "";
        };
    }

    @SafeVarargs
    private static BuiltInAdvancedMachineTypes.AdvancedMachineType<?>[] machines(BuiltInAdvancedMachineTypes.AdvancedMachineType<?>... machineTypes) {
        return machineTypes;
    }

    private static List<Supplier<? extends ItemStack>> unlockedCatalysts(BuiltInAdvancedMachineTypes.AdvancedMachineType<?>[] machineTypes, int requiredTier) {
        List<Supplier<? extends ItemStack>> list = new ArrayList<>();
        if (requiredTier == Config.CREATIVE_TIER) {
            addMachinesOfTier(machineTypes, "creative", list);
            return list;
        }
        for (int tier = requiredTier; tier <= 4; tier++) {
            addMachinesOfTier(machineTypes, tierName(tier), list);
        }
        addMachinesOfTier(machineTypes, "creative", list);
        return list;
    }

    private static void addMachinesOfTier(BuiltInAdvancedMachineTypes.AdvancedMachineType<?>[] machineTypes, String tierName, List<Supplier<? extends ItemStack>> out) {
        if (tierName.isEmpty()) {
            return;
        }
        for (BuiltInAdvancedMachineTypes.AdvancedMachineType<?> machineType : machineTypes) {
            BlockEntry<? extends Block> entry = entry(machineType, tierName);
            if (entry == null) {
                continue;
            }
            Item item = entry.get().asItem();
            if (item == Items.AIR) {
                continue;
            }
            out.add(() -> new ItemStack(item));
        }
    }
}
