package com.titammods.client.model;

import com.mojang.serialization.MapCodec;
import com.titammods.TitamMods;
import com.titammods.common.blockentities.multiblock.IDisplayFluidListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.dispatch.SingleVariant;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.SimpleModelWrapper;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;
import net.neoforged.neoforge.client.model.block.CustomUnbakedBlockStateModel;
import net.neoforged.neoforge.client.model.quad.MutableQuad;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class FluidTextureModel {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(TitamMods.MODID, "fluid_texture");

    private static final Identifier FLUID_PLACEHOLDER = Identifier.fromNamespaceAndPath(TitamMods.MODID, "block/black");

    private static final Direction[] SIDES = Arrays.copyOfRange(Direction.values(), 0, 7);

    private FluidTextureModel() {}

    public record Unbaked(SingleVariant.Unbaked wrapped) implements CustomUnbakedBlockStateModel {
        public static final MapCodec<Unbaked> CODEC =
                SingleVariant.Unbaked.MAP_CODEC.xmap(Unbaked::new, Unbaked::wrapped);

        @Override
        public BlockStateModel bake(ModelBaker baker) {
            return new Baked(this.wrapped.bake(baker));
        }

        @Override
        public void resolveDependencies(Resolver resolver) {
            this.wrapped.resolveDependencies(resolver);
        }

        @Override
        public MapCodec<Unbaked> codec() {
            return CODEC;
        }
    }

    private record CacheKey(BlockStateModelPart part, Fluid fluid) {}

    private static final class Baked extends DelegateBlockStateModel {
        private final Map<CacheKey, BlockStateModelPart> cache = new ConcurrentHashMap<>();

        private Baked(BlockStateModel delegate) {
            super(delegate);
        }

        @Override
        public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state,
                                 RandomSource random, List<BlockStateModelPart> parts) {
            FluidStack fluid = level.getModelData(pos).get(IDisplayFluidListener.DISPLAY_FLUID);
            if (fluid == null || fluid.isEmpty()) {
                super.collectParts(level, pos, state, random, parts);
                return;
            }

            List<BlockStateModelPart> original = new ArrayList<>();
            super.collectParts(level, pos, state, random, original);
            for (BlockStateModelPart part : original) {
                parts.add(this.cache.computeIfAbsent(new CacheKey(part, fluid.getFluid()),
                        key -> retexture(part, fluid)));
            }
        }
    }

    private static BlockStateModelPart retexture(BlockStateModelPart part, FluidStack fluid) {
        var fluidModel = Minecraft.getInstance().getModelManager()
                .getFluidStateModelSet().get(fluid.getFluid().defaultFluidState());
        TextureAtlasSprite sprite = fluidModel.stillMaterial().sprite();
        var tintSource = fluidModel.fluidTintSource();
        int color = (tintSource != null ? tintSource.colorAsStack(fluid) : -1) | 0xFF000000;
        int light = fluid.getFluidType().getLightLevel(fluid);

        QuadCollection.Builder builder = new QuadCollection.Builder();
        for (Direction side : SIDES) {
            for (BakedQuad quad : part.getQuads(side)) {
                BakedQuad result = quad;
                if (quad.materialInfo().sprite().contents().name().equals(FLUID_PLACEHOLDER)) {
                    MutableQuad mutable = new MutableQuad().setFrom(quad);
                    mutable.setSpriteAndMoveUv(sprite, mutable.requiredChunkLayer(), mutable.requiredItemRenderType())
                            .setColor(color)
                            .setLightEmission(Math.max(light, mutable.lightEmission()));
                    result = mutable.toBakedQuad();
                }
                if (side == null) builder.addUnculledFace(result);
                else builder.addCulledFace(side, result);
            }
        }
        return new SimpleModelWrapper(builder.build(), part.useAmbientOcclusion(), part.particleMaterial());
    }
}