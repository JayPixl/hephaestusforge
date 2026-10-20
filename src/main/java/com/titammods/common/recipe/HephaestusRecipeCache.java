package com.titammods.common.recipe;

import com.titammods.setup.ModRecipes;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class HephaestusRecipeCache {

    private static final int MAX_ENTRIES = 4096;

    private record StackKey(Item item, DataComponentPatch patch) {}

    private record CastKey(StackKey cast, Identifier fluid) {}

    private static final StackKey EMPTY_KEY = new StackKey(Items.AIR, DataComponentPatch.EMPTY);

    private static final Map<StackKey, Optional<ModRecipes.MeltingRecipe>> MELTING = new ConcurrentHashMap<>();
    private static final Map<CastKey, List<ModRecipes.CastingTableRecipe>> TABLE = new ConcurrentHashMap<>();
    private static final Map<CastKey, List<ModRecipes.CastingBasinRecipe>> BASIN = new ConcurrentHashMap<>();
    private static volatile @Nullable Map<Fluid, Integer> fuelTemperatures;
    private static @Nullable Object recipeMap;

    private HephaestusRecipeCache() {}

    private static StackKey key(ItemStack stack) {
        return stack.isEmpty() ? EMPTY_KEY : new StackKey(stack.getItem(), stack.getComponentsPatch());
    }

    private static <K, V> void limit(Map<K, V> map) {
        if (map.size() > MAX_ENTRIES) map.clear();
    }

    private static synchronized @Nullable ServerLevel validate(@Nullable Level level) {
        if (!(level instanceof ServerLevel sl)) return null;
        Object current = sl.getServer().getRecipeManager().recipeMap();
        if (current != recipeMap) {
            recipeMap = current;
            MELTING.clear();
            TABLE.clear();
            BASIN.clear();
            fuelTemperatures = null;
        }
        return sl;
    }

    public static ModRecipes.@Nullable MeltingRecipe melting(@Nullable Level level, ItemStack stack) {
        ServerLevel sl = validate(level);
        if (sl == null || stack.isEmpty()) return null;
        StackKey key = key(stack);
        Optional<ModRecipes.MeltingRecipe> cached = MELTING.get(key);
        if (cached == null) {
            ModRecipes.MeltingRecipe found = null;
            for (var holder : sl.getServer().getRecipeManager().recipeMap().byType(ModRecipes.MELTING_TYPE.get())) {
                if (holder.value().input().test(stack)) {
                    found = holder.value();
                    break;
                }
            }
            cached = Optional.ofNullable(found);
            limit(MELTING);
            MELTING.put(key, cached);
        }
        return cached.orElse(null);
    }

    public static int fuelTemperature(@Nullable Level level, FluidStack fluid) {
        ServerLevel sl = validate(level);
        if (sl == null || fluid.isEmpty()) return 0;
        Map<Fluid, Integer> temps = fuelTemperatures;
        if (temps == null) {
            temps = new HashMap<>();
            for (var holder : sl.getServer().getRecipeManager().recipeMap().byType(ModRecipes.MELTING_TYPE.get())) {
                ModRecipes.MeltingRecipe recipe = holder.value();
                FluidStack fuel = recipe.fuel();
                if (fuel.isEmpty()) continue;
                temps.merge(fuel.getFluid(), recipe.temperature(), Math::max);
            }
            fuelTemperatures = temps;
        }
        int max = 0;
        for (var entry : temps.entrySet()) {
            if (entry.getKey().isSame(fluid.getFluid())) max = Math.max(max, entry.getValue());
        }
        return max;
    }

    private static List<ModRecipes.CastingTableRecipe> tableCandidates(ServerLevel sl, ItemStack mold, Fluid fluid) {
        Identifier fid = BuiltInRegistries.FLUID.getKey(fluid);
        CastKey key = new CastKey(key(mold), fid);
        List<ModRecipes.CastingTableRecipe> cached = TABLE.get(key);
        if (cached == null) {
            List<ModRecipes.CastingTableRecipe> found = new ArrayList<>();
            for (var holder : sl.getServer().getRecipeManager().recipeMap().byType(ModRecipes.CASTING_TABLE_TYPE.get())) {
                ModRecipes.CastingTableRecipe recipe = holder.value();
                boolean moldMatches = recipe.cast().isEmpty() ? mold.isEmpty() : recipe.cast().get().test(mold);
                if (moldMatches && recipe.fluidId().equals(fid)) found.add(recipe);
            }
            cached = List.copyOf(found);
            limit(TABLE);
            TABLE.put(key, cached);
        }
        return cached;
    }

    public static ModRecipes.@Nullable CastingTableRecipe table(@Nullable Level level, ItemStack mold, Fluid fluid) {
        ServerLevel sl = validate(level);
        if (sl == null) return null;
        List<ModRecipes.CastingTableRecipe> candidates = tableCandidates(sl, mold, fluid);
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    public static ModRecipes.@Nullable CastingTableRecipe table(@Nullable Level level, ItemStack mold, Fluid fluid, int amount) {
        ServerLevel sl = validate(level);
        if (sl == null) return null;
        for (ModRecipes.CastingTableRecipe recipe : tableCandidates(sl, mold, fluid)) {
            if (amount >= recipe.fluidAmount()) return recipe;
        }
        return null;
    }

    private static List<ModRecipes.CastingBasinRecipe> basinCandidates(ServerLevel sl, ItemStack cast, Fluid fluid) {
        Identifier fid = BuiltInRegistries.FLUID.getKey(fluid);
        CastKey key = new CastKey(key(cast), fid);
        List<ModRecipes.CastingBasinRecipe> cached = BASIN.get(key);
        if (cached == null) {
            List<ModRecipes.CastingBasinRecipe> found = new ArrayList<>();
            for (var holder : sl.getServer().getRecipeManager().recipeMap().byType(ModRecipes.CASTING_BASIN_TYPE.get())) {
                ModRecipes.CastingBasinRecipe recipe = holder.value();
                if (recipe.matchesCast(cast) && recipe.fluidId().equals(fid)) found.add(recipe);
            }
            cached = List.copyOf(found);
            limit(BASIN);
            BASIN.put(key, cached);
        }
        return cached;
    }

    public static ModRecipes.@Nullable CastingBasinRecipe basin(@Nullable Level level, ItemStack cast, Fluid fluid) {
        ServerLevel sl = validate(level);
        if (sl == null) return null;
        List<ModRecipes.CastingBasinRecipe> candidates = basinCandidates(sl, cast, fluid);
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    public static ModRecipes.@Nullable CastingBasinRecipe basin(@Nullable Level level, ItemStack cast, Fluid fluid, int amount) {
        ServerLevel sl = validate(level);
        if (sl == null) return null;
        for (ModRecipes.CastingBasinRecipe recipe : basinCandidates(sl, cast, fluid)) {
            if (amount >= recipe.fluidAmount()) return recipe;
        }
        return null;
    }
}