package logisticspipes.proxy.buildcraft.robots;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import logisticspipes.config.Configs;
import logisticspipes.interfaces.routing.IFilter;
import logisticspipes.interfaces.routing.ISpecialPipedConnection;
import logisticspipes.pipes.basic.LogisticsTileGenericPipe;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.proxy.buildcraft.robots.boards.LogisticsRoutingBoardRobot;
import logisticspipes.proxy.specialconnection.SpecialPipeConnection.ConnectionInformation;
import logisticspipes.routing.PipeRoutingConnectionType;
import logisticspipes.routing.pathfinder.IPipeInformationProvider;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.tuples.LPPosition;
import logisticspipes.utils.tuples.Pair;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import buildcraft.api.core.IZone;
import buildcraft.api.robots.EntityRobotBase;
import buildcraft.api.transport.pluggable.PipePluggable;
import buildcraft.robots.DockingStation;
import buildcraft.robots.RobotStationPluggable;
import buildcraft.transport.TileGenericPipe;

public class LPRobotConnectionControl implements ISpecialPipedConnection {
	
	public static class RobotConnection {
		public final Set<Pair<LPPosition, ForgeDirection>> localConnectedRobots = new HashSet<Pair<LPPosition, ForgeDirection>>();
	}
	
	public static class RobotConnectionFilter implements IFilter {
		private LogisticsRoutingBoardRobot connectionFrom;
		private LogisticsRoutingBoardRobot connectionTo;
		
		private RobotConnectionFilter(LogisticsRoutingBoardRobot from, LogisticsRoutingBoardRobot to) {
			this.connectionFrom = from;
			this.connectionTo = to;
		}
		
		@Override
		public boolean isBlocked() {	
			return true;
		}

		@Override
		public boolean isFilteredItem(ItemIdentifier item) {
			if(!connectionFrom.isAcceptsItems()) {
				return true;
			}
			if(connectionFrom.getCurrentTarget() != null) {
				if(connectionFrom.getCurrentTarget().getValue2() != connectionTo) {
					return true;
				}
			}
			if(connectionTo.getCurrentTarget() != null) {
				if(connectionTo.getCurrentTarget().getValue2() != connectionFrom) {
					return true;
				}
			}
			if(connectionFrom.getLinkedStationPosition().center().moveForward(connectionFrom.robot.getLinkedStation().side(), 0.5).distanceTo(new LPPosition(connectionFrom.robot)) > 0.05) {// Not at station
				return true;
			}
			if(connectionTo.getLinkedStationPosition().center().moveForward(connectionTo.robot.getLinkedStation().side(), 0.5).distanceTo(new LPPosition(connectionTo.robot)) > 0.05) {// Not at station
				return true;
			}
			if(connectionFrom.robot.getZoneToWork() != null && !connectionFrom.robot.getZoneToWork().contains(connectionTo.robot.posX, connectionTo.robot.posY, connectionTo.robot.posZ)) {
				return true;
			}
			if(connectionTo.robot.getZoneToWork() != null && !connectionTo.robot.getZoneToWork().contains(connectionFrom.robot.posX, connectionFrom.robot.posY, connectionFrom.robot.posZ)) {
				return true;
			}	
			return false;
		}

		@Override
		public boolean blockProvider() {
			return false;
		}

		@Override
		public boolean blockCrafting() {
			return false;
		}

		@Override
		public boolean blockRouting() {
			return false;
		}

		@Override
		public boolean blockPower() {
			return true;
		}

		@Override
		public LPPosition getLPPosition() {
			return connectionFrom.getLinkedStationPosition();
		}

		@Override
		public boolean equals(Object obj) {
			if(!(obj instanceof RobotConnectionFilter)) return false;
			return ((RobotConnectionFilter)obj).connectionFrom == connectionFrom && ((RobotConnectionFilter)obj).connectionTo == connectionTo;
		}
	}
	
	private final Map<World, Set<Pair<LPPosition, ForgeDirection>>> globalAvailableRobots = new WeakHashMap<World, Set<Pair<LPPosition, ForgeDirection>>>();
	
	public void addRobot(World world, LPPosition pos, ForgeDirection dir) {
		if(globalAvailableRobots.get(world) == null) {
			globalAvailableRobots.put(world, new HashSet<Pair<LPPosition,ForgeDirection>>());
		}
		globalAvailableRobots.get(world).add(new Pair<LPPosition, ForgeDirection>(pos, dir));
		checkAll(world);
	}
	
	//TODO: Call this somewhere...
	public void removeRobot(World world, LPPosition pos, ForgeDirection dir) {
		if(globalAvailableRobots.containsKey(world)) {
			globalAvailableRobots.get(world).remove(new Pair<LPPosition, ForgeDirection>(pos, dir));
		}
		checkAll(world);
	}
	
	public void checkAll(World world) {
		for(Pair<LPPosition, ForgeDirection> canidatePos: globalAvailableRobots.get(world)) {
			TileEntity connectedPipeTile = canidatePos.getValue1().getTileEntity(world);
			if(!(connectedPipeTile instanceof LogisticsTileGenericPipe)) continue;
			LogisticsTileGenericPipe connectedPipe = (LogisticsTileGenericPipe) connectedPipeTile;
			if(!connectedPipe.isRoutingPipe()) continue;
			PipePluggable connectedPluggable = ((TileGenericPipe)connectedPipe.tilePart.getOriginal()).getPipePluggable(canidatePos.getValue2());
			if(!(connectedPluggable instanceof RobotStationPluggable)) continue;
			DockingStation connectedStation = ((RobotStationPluggable)connectedPluggable).getStation();
			if(!connectedStation.isTaken()) continue;
			EntityRobotBase connectedRobot = connectedStation.robotTaking();
			if(connectedRobot == null) continue;
			if(!(connectedRobot.getBoard() instanceof LogisticsRoutingBoardRobot)) continue;
			LogisticsRoutingBoardRobot lpBoard = ((LogisticsRoutingBoardRobot)connectedRobot.getBoard());
			if(isModified(lpBoard)) {
				connectedPipe.getRoutingPipe().triggerConnectionCheck();
			}
		}
	}
	
	public boolean isModified(LogisticsRoutingBoardRobot board) {
		Set<Pair<LPPosition, ForgeDirection>> localConnectedRobots = new HashSet<Pair<LPPosition, ForgeDirection>>();
		LPPosition sourceRobotPosition = board.getLinkedStationPosition().center().moveForward(board.robot.getLinkedStation().side(), 0.5);
		IZone zone = board.robot.getZoneToWork();
		for(Pair<LPPosition, ForgeDirection> canidatePos: globalAvailableRobots.get(board.robot.worldObj)) {
			LPPosition canidateRobotPosition = canidatePos.getValue1().copy().center().moveForward(canidatePos.getValue2(), 0.5);
			double distance = canidateRobotPosition.distanceTo(sourceRobotPosition);
			boolean isPartOfZone;
			if(zone != null) {
				isPartOfZone = zone.contains(canidateRobotPosition.getXD(), canidateRobotPosition.getYD(), canidateRobotPosition.getZD());
			} else {
				isPartOfZone = distance < Configs.MAX_ROBOT_DISTANCE;
			}
			if(isPartOfZone) {
				localConnectedRobots.add(canidatePos);
			}
		}
		if(board.getConnectionDetails().localConnectedRobots.equals(localConnectedRobots)) {
			return false;
		} else {
			board.getConnectionDetails().localConnectedRobots.clear();
			board.getConnectionDetails().localConnectedRobots.addAll(localConnectedRobots);
			return true;
		}
	}
	
	@Override
	public boolean init() {
		return true;
	}
	
	@Override
	public boolean isType(IPipeInformationProvider startPipe) {
		if(!(startPipe instanceof LogisticsTileGenericPipe)) return false;
		LogisticsTileGenericPipe pipe = (LogisticsTileGenericPipe) startPipe;
		return pipe.isRoutingPipe();
	}

	@Override
	public List<ConnectionInformation> getConnections(IPipeInformationProvider startPipe, EnumSet<PipeRoutingConnectionType> connection, ForgeDirection side) {
		List<ConnectionInformation> list = new ArrayList<ConnectionInformation>();
		LogisticsTileGenericPipe pipe = (LogisticsTileGenericPipe) startPipe;
		LPPosition pos = new LPPosition(startPipe);
		pos.center();
		for(ForgeDirection dir:ForgeDirection.VALID_DIRECTIONS) {
			PipePluggable pluggable = ((TileGenericPipe)pipe.tilePart.getOriginal()).getPipePluggable(dir);
			if(!(pluggable instanceof RobotStationPluggable)) continue;
			DockingStation station = ((RobotStationPluggable)pluggable).getStation();
			if(!station.isTaken()) continue;
			EntityRobotBase robot = station.robotTaking();
			if(robot == null) continue;
			if(!(robot.getBoard() instanceof LogisticsRoutingBoardRobot)) continue;
			if(robot.isDead) continue;
			LPPosition robotPos = ((LogisticsRoutingBoardRobot)robot.getBoard()).getLinkedStationPosition().center().moveForward(dir, 0.5);
			
			for(Pair<LPPosition, ForgeDirection> canidatePos: ((LogisticsRoutingBoardRobot)robot.getBoard()).getConnectionDetails().localConnectedRobots) {
				if(canidatePos.getValue1().equals(new LPPosition(startPipe))) continue;
				TileEntity connectedPipeTile = canidatePos.getValue1().getTileEntity(pipe.getWorldObj());
				if(!(connectedPipeTile instanceof LogisticsTileGenericPipe)) continue;
				LogisticsTileGenericPipe connectedPipe = (LogisticsTileGenericPipe) connectedPipeTile;
				if(!connectedPipe.isRoutingPipe()) continue;
				IPipeInformationProvider connectedPipeInfo = SimpleServiceLocator.pipeInformaitonManager.getInformationProviderFor(connectedPipe);
				PipePluggable connectedPluggable = ((TileGenericPipe)connectedPipe.tilePart.getOriginal()).getPipePluggable(canidatePos.getValue2());
				if(!(connectedPluggable instanceof RobotStationPluggable)) continue;
				DockingStation connectedStation = ((RobotStationPluggable)connectedPluggable).getStation();
				if(!connectedStation.isTaken()) continue;
				EntityRobotBase connectedRobot = connectedStation.robotTaking();
				if(connectedRobot == null) continue;
				if(!(connectedRobot.getBoard() instanceof LogisticsRoutingBoardRobot)) continue;
				if(connectedRobot.isDead) continue;
				if(connectedRobot.getZoneToWork() != null && !connectedRobot.getZoneToWork().contains(robotPos.getXD(), robotPos.getYD(), robotPos.getZD())) continue;
				EnumSet<PipeRoutingConnectionType> newCon = connection.clone();
				newCon.removeAll(EnumSet.of(PipeRoutingConnectionType.canPowerFrom, PipeRoutingConnectionType.canPowerSubSystemFrom));
				double distance = canidatePos.getValue1().copy().center().moveForward(canidatePos.getValue2(), 0.5).distanceTo(robotPos);
				ConnectionInformation conInfo = new ConnectionInformation(connectedPipeInfo, newCon, canidatePos.getValue2().getOpposite(), dir, (distance * 3) + 21);
				conInfo.getFilters().add(new RobotConnectionFilter((LogisticsRoutingBoardRobot)robot.getBoard(), (LogisticsRoutingBoardRobot)connectedRobot.getBoard()));
				list.add(conInfo);
			}
		}
		return list;
	}
	
	public void cleanup() {
		globalAvailableRobots.clear();
	}

	public static final LPRobotConnectionControl instance = new LPRobotConnectionControl();
	private LPRobotConnectionControl() {}

}
