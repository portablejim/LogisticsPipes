package logisticspipes.pipes.upgrades.power;

import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.pipes.upgrades.IPipeUpgrade;

public class RFPowerSupplierUpgrade implements IPipeUpgrade {
	@Override
	public boolean needsUpdate() {
		return true;
	}
	
	@Override
	public boolean isAllowed(CoreRoutedPipe pipe) {
		return true;
	}
}
