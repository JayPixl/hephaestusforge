package com.titammods.common.blockentities;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@SuppressWarnings("removal")
public class TransactionalItemHandler extends SnapshotJournal<List<ItemStack>> implements ResourceHandler<ItemResource> {
    private final Supplier<@Nullable IItemHandlerModifiable> handler;
    private final boolean allowInsert;
    private final boolean allowExtract;

    public TransactionalItemHandler(Supplier<@Nullable IItemHandlerModifiable> handler, boolean allowInsert, boolean allowExtract) {
        this.handler = handler;
        this.allowInsert = allowInsert;
        this.allowExtract = allowExtract;
    }

    @Override
    protected List<ItemStack> createSnapshot() {
        IItemHandlerModifiable h = handler.get();
        List<ItemStack> copy = new ArrayList<>();
        if (h != null) {
            for (int i = 0; i < h.getSlots(); i++) copy.add(h.getStackInSlot(i).copy());
        }
        return copy;
    }

    @Override
    protected void revertToSnapshot(List<ItemStack> snapshot) {
        IItemHandlerModifiable h = handler.get();
        if (h == null || h.getSlots() != snapshot.size()) return;
        for (int i = 0; i < snapshot.size(); i++) h.setStackInSlot(i, snapshot.get(i));
    }

    @Override
    public int size() {
        IItemHandlerModifiable h = handler.get();
        return h == null ? 0 : h.getSlots();
    }

    @Override
    public ItemResource getResource(int index) {
        IItemHandlerModifiable h = handler.get();
        if (h == null || index < 0 || index >= h.getSlots()) return ItemResource.EMPTY;
        return ItemResource.of(h.getStackInSlot(index));
    }

    @Override
    public long getAmountAsLong(int index) {
        IItemHandlerModifiable h = handler.get();
        if (h == null || index < 0 || index >= h.getSlots()) return 0;
        return h.getStackInSlot(index).getCount();
    }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        IItemHandlerModifiable h = handler.get();
        if (h == null || index < 0 || index >= h.getSlots()) return 0;
        int limit = h.getSlotLimit(index);
        return resource.isEmpty() ? limit : Math.min(limit, resource.getMaxStackSize());
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        IItemHandlerModifiable h = handler.get();
        if (h == null || index < 0 || index >= h.getSlots()) return false;
        return resource.isEmpty() || h.isItemValid(index, resource.toStack(1));
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        IItemHandlerModifiable h = handler.get();
        if (!allowInsert || amount == 0 || h == null || index < 0 || index >= h.getSlots()) return 0;
        int inserted = amount - h.insertItem(index, resource.toStack(amount), true).getCount();
        if (inserted <= 0) return 0;
        updateSnapshots(transaction);
        h.insertItem(index, resource.toStack(inserted), false);
        return inserted;
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        IItemHandlerModifiable h = handler.get();
        if (!allowExtract || amount == 0 || h == null || index < 0 || index >= h.getSlots()) return 0;
        if (!resource.matches(h.getStackInSlot(index))) return 0;
        int extracted = h.extractItem(index, amount, true).getCount();
        if (extracted <= 0) return 0;
        updateSnapshots(transaction);
        h.extractItem(index, extracted, false);
        return extracted;
    }
}