package logisticspipes.pipes;

import logisticspipes.interfaces.routing.IRequestItems;
import logisticspipes.logic.LogicFluidSupplier;
import logisticspipes.logisticspipes.IRoutedItem;
import logisticspipes.modules.LogisticsModule;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.textures.Textures;
import logisticspipes.textures.Textures.TextureType;
import logisticspipes.transport.PipeTransportLogistics;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.ForgeDirection;
import net.minecraftforge.fluids.IFluidHandler;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidContainerRegistry;
import buildcraft.transport.TravelingItem;
import buildcraft.transport.IItemTravelingHook;
import buildcraft.transport.PipeTransportItems;
import buildcraft.transport.TileGenericPipe;

public class PipeItemsFluidSupplier extends CoreRoutedPipe implements IRequestItems, IItemTravelingHook{

	private boolean _lastRequestFailed = false;
	
	public PipeItemsFluidSupplier(int itemID) {
		super(new PipeTransportLogistics() {

			@Override
			public boolean canPipeConnect(TileEntity tile, ForgeDirection dir) {
				if(super.canPipeConnect(tile, dir)) return true;
				if(tile instanceof TileGenericPipe) return false;
				if (tile instanceof IFluidTank) {
					IFluidTank liq = (IFluidTank) tile;
					//TODO: check this change
					//					if (liq.getTanks(ForgeDirection.UNKNOWN) != null && liq.getTanks(ForgeDirection.UNKNOWN).length > 0)
 					if (liq.getCapacity() > 0)
						return true;
				}
				return false;
			}
		}, new LogicFluidSupplier(), itemID);
		((PipeTransportItems) transport).travelHook = this;
		((LogicFluidSupplier) logic)._power = this;
	}

	@Override
	public TextureType getCenterTexture() {
		return Textures.LOGISTICSPIPE_LIQUIDSUPPLIER_TEXTURE;
	}

	/* TRIGGER INTERFACE */
	public boolean isRequestFailed(){
		return _lastRequestFailed;
	}

	public void setRequestFailed(boolean value){
		_lastRequestFailed = value;
	}

	@Override
	public LogisticsModule getLogisticsModule() {
		return null;
	}

	@Override
	public ItemSendMode getItemSendMode() {
		return ItemSendMode.Fast;
	}


	/* IItemTravelingHook */

	@Override
	public void endReached(PipeTransportItems pipe, TravelingItem data, TileEntity tile) {
		((PipeTransportLogistics)pipe).markChunkModified(tile);
		if (!(tile instanceof IFluidHandler)) return;
		if (tile instanceof TileGenericPipe) return;
		IFluidHandler container = (IFluidHandler) tile;
		//container.getFluidSlots()[0].getFluidQty();
		if (data == null) return;
		if (data.getItemStack() == null) return;
		FluidStack liquidId = FluidContainerRegistry.getFluidForFilledItem(data.getItemStack());
		if (liquidId == null) return;
		ForgeDirection orientation = data.output.getOpposite();
		if(getUpgradeManager().hasSneakyUpgrade()) {
			orientation = getUpgradeManager().getSneakyOrientation();
		}
		while (data.getItemStack().stackSize > 0 && container.fill(orientation, liquidId, false) == liquidId.amount && this.useEnergy(5)) {
			container.fill(orientation, liquidId, true);
			data.getItemStack().stackSize--;
			if (data.getItemStack().itemID >= 0 && data.getItemStack().itemID < Item.itemsList.length){
				Item item = Item.itemsList[data.getItemStack().itemID];
				if (item.hasContainerItem()){
					Item containerItem = item.getContainerItem();
					IRoutedItem itemToSend = SimpleServiceLocator.buildCraftProxy.CreateRoutedItem(new ItemStack(containerItem, 1), this.getWorld());
					this.queueRoutedItem(itemToSend, data.output);
				}
			}
		}
		if (data.getItemStack().stackSize < 1){
			((PipeTransportItems)this.transport).scheduleRemoval(data);
		}
	}

	@Override
	public void drop(PipeTransportItems pipe, TravelingItem data) {}

	@Override
	public void centerReached(PipeTransportItems pipe, TravelingItem data) {}
	
	@Override
	public boolean hasGenericInterests() {
		return true;
	}

}