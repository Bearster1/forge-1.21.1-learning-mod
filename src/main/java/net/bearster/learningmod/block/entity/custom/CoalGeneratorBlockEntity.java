package net.bearster.learningmod.block.entity.custom;


import net.bearster.learningmod.block.entity.ModBlockEntities;
import net.bearster.learningmod.block.entity.energy.ModEnergyStorage;
import net.bearster.learningmod.screen.custom.CoalGeneratorMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CoalGeneratorBlockEntity extends BlockEntity implements MenuProvider {
    public final ItemStackHandler itemHandler = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if(!level.isClientSide()) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
        }
    };

    private LazyOptional<IItemHandler> lazyItemHandler = LazyOptional.empty();
    private LazyOptional<IEnergyStorage> lazyEnergyHandler = LazyOptional.empty();

    private static final int INPUT_SLOT = 0;

    protected final ContainerData data;
    private int burnProgress = 160;
    private int maxBurnProgress = 160;
    private boolean isBurning = false;

    private static final int ENERGY_TRANSFER_AMOUNT = 320;

    private final ModEnergyStorage ENERGY_STORAGE = createEnergyStorage();
    private ModEnergyStorage createEnergyStorage() {
        return new ModEnergyStorage(64000, 320) {
            @Override
            public void onEnergyChanged() {
                setChanged();
                getLevel().sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
        };
    }

    public IEnergyStorage getEnergyStorage() {
        return ENERGY_STORAGE;
    }

    public CoalGeneratorBlockEntity(BlockPos pPos, BlockState pBlockState) {
        super(ModBlockEntities.COAL_GENERATOR_BE.get(), pPos, pBlockState);
        this.data = new ContainerData() {
            @Override
            public int get(int pIndex) {
                return switch (pIndex) {
                    case 0 -> CoalGeneratorBlockEntity.this.burnProgress;
                    case 1 -> CoalGeneratorBlockEntity.this.maxBurnProgress;
                    default -> 0;
                };
            }

            @Override
            public void set(int pIndex, int pValue) {
                switch (pIndex) {
                    case 0 -> CoalGeneratorBlockEntity.this.burnProgress = pValue;
                    case 1 -> CoalGeneratorBlockEntity.this.maxBurnProgress = pValue;
                }
            }

            @Override
            public int getCount() {
                return 2;
            }
        };
    }

    @Override
    public void onLoad() {
        super.onLoad();
        lazyItemHandler = LazyOptional.of(() -> itemHandler);
        lazyEnergyHandler = LazyOptional.of(() -> ENERGY_STORAGE);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        lazyItemHandler.invalidate();
        lazyEnergyHandler.invalidate();
    }

    @Override
    public Component getDisplayName() {
        return Component.literal("Coal Generator");
    }

    @Override
    protected void saveAdditional(CompoundTag pTag, HolderLookup.Provider pRegistries) {
        pTag.put("coal_generator.inventory", itemHandler.serializeNBT(pRegistries));
        pTag.putInt("coal_generator.progress", burnProgress);
        pTag.putInt("coal_generator.max_progress", maxBurnProgress);

        pTag.putInt("coal_generator.energy", ENERGY_STORAGE.getEnergyStored());

        super.saveAdditional(pTag, pRegistries);
    }

    @Override
    protected void loadAdditional(CompoundTag pTag, HolderLookup.Provider pRegistries) {
        super.loadAdditional(pTag, pRegistries);
        itemHandler.deserializeNBT(pRegistries, pTag.getCompound("coal_generator.inventory"));
        ENERGY_STORAGE.setEnergy(pTag.getInt("coal_generator.energy"));

        burnProgress = pTag.getInt("coal_generator.burn_progress");
        maxBurnProgress = pTag.getInt("coal_generator.max_burn_progress");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int i, Inventory inventory, Player player) {
        return new CoalGeneratorMenu(i, inventory, this, this.data);
    }

    public void drops() {
        SimpleContainer inventory = new SimpleContainer(itemHandler.getSlots());
        for (int i = 0; i < itemHandler.getSlots(); i++) {
            inventory.setItem(i, itemHandler.getStackInSlot(i));
        }

        Containers.dropContents(this.level, this.worldPosition, inventory);
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if(cap == ForgeCapabilities.ITEM_HANDLER) {
            if(side == null) {
                return lazyItemHandler.cast();
            }
        }

        if(cap == ForgeCapabilities.ENERGY) {
            return lazyEnergyHandler.cast();
        }

        return super.getCapability(cap, side);
    }


    public void tick(Level level, BlockPos blockPos, BlockState blockState) {
        if(hasFuelItemInSlot()) {
            if(!isBurningFuel()) {
                startBurning();
            }
        }

        if(isBurningFuel()) {
            increaseBurnTimer();
            if(currentFuelDoneBurning()) {
                resetBurning();
            }
            fillUpOnEnergy();
        }

        pushEnergyToNeighbourAbove();
    }

    private void pushEnergyToNeighbourAbove() {
        if(this.level.getBlockEntity(this.worldPosition.above()) != null) {
            this.level.getBlockEntity(this.worldPosition.above()).getCapability(ForgeCapabilities.ENERGY).ifPresent(energy -> {
                if(energy.canReceive()) {
                    int maxAmountToReceive = energy.receiveEnergy(320, true);

                    int extractedEnergy = this.ENERGY_STORAGE.extractEnergy(maxAmountToReceive, false);
                    energy.receiveEnergy(extractedEnergy, false);
                }
            });
        }
    }

    private void fillUpOnEnergy() {
        this.ENERGY_STORAGE.receiveEnergy(320, false);
    }

    private boolean hasFuelItemInSlot() {
        return this.itemHandler.getStackInSlot(INPUT_SLOT).is(Items.COAL);
    }

    private void startBurning() {
        this.itemHandler.extractItem(INPUT_SLOT, 1, false);
        isBurning = true;
    }

    private boolean isBurningFuel() {
        return isBurning;
    }

    private boolean currentFuelDoneBurning() {
        return this.burnProgress <= 0;
    }

    private void resetBurning() {
        isBurning = false;
        this.burnProgress = 160;
    }

    private void increaseBurnTimer() {
        this.burnProgress--;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider pRegistries) {
        return saveWithoutMetadata(pRegistries);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider pRegistries) {
        super.onDataPacket(net, pkt, pRegistries);
    }
}
