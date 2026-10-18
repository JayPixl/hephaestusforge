package com.titammods.common.blockentities.multiblock;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.model.data.ModelProperty;

public interface IDisplayFluidListener {
    ModelProperty<FluidStack> DISPLAY_FLUID = new ModelProperty<>();

    void notifyDisplayFluidUpdated(FluidStack fluid);
    FluidStack getDisplayFluid();
}