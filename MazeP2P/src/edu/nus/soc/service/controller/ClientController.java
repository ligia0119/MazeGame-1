package edu.nus.soc.service.controller;

import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;

import edu.nus.soc.model.Maze;
import edu.nus.soc.model.Movement;
import edu.nus.soc.model.Peer;
import edu.nus.soc.model.Player;
import edu.nus.soc.model.Position;
import edu.nus.soc.service.CallBackService;
import edu.nus.soc.service.PlayerService;
import edu.nus.soc.service.impl.CallBackServiceImpl;
import edu.nus.soc.util.Util;

public class ClientController {
	private static Maze				maze = Maze.get();
	private static Player			player;
	private static PlayerService	primaryService = null;
	private static PlayerService	secondaryService = null;

	private static CallBackService	callbackService;	//callback service to be called by game server
	
	public static PlayerService getPrimaryService() {
		return primaryService;
	}

	public static void setPrimaryService(PlayerService primaryService) {
		ClientController.primaryService = primaryService;
	}
	
	public static PlayerService getSecondaryService() {
		return secondaryService;
	}

	public static void setSecondaryService(PlayerService secondaryService) {
		ClientController.secondaryService = secondaryService;
	}

	public ClientController() throws RemoteException, MalformedURLException, NotBoundException {
		callbackService = new CallBackServiceImpl();
		setPrimaryService((PlayerService) Naming.lookup(Util.getRMIStringByIpPort(Util.defaultIp, Util.defaultPort)));
	}
	
	public Maze move(Movement move) {
		Peer peer = new Peer();
		if (player == null ) {
			System.out.println("The game hasn't started, please wait...");
			return null;
		} else if (null == move) {
			System.out.println("Sorry, your input is invalid, please try again.");
			return null;
		} else if (false == Util.isMovable(player.getPos(), move, Maze.get().getSize()) ) {
			System.out.println("Sorry, you have reached the boundary, please try again.");
			System.out.printf("player position :%d,%d  maze.size:%d\n",player.getPos().getX(),player.getPos().getY(),Maze.get().getSize());
			return null;
		}
		Position currentPos = new Position(player.getPos().getX(), player.getPos().getY());
		System.out.println("111111111111111111");
		try {
			maze = primaryService.move(player.getId(), move, peer);
		} catch (RemoteException e) {
			System.out.println("try secondary service.");
			try {
				maze = secondaryService.move(player.getId(), move, peer);
			} catch (RemoteException e1) {
				e1.printStackTrace();
				return  null;
			}
		}
		if (null == maze) {
			System.out.println("Sorry, move failed.");
		}
		Maze.get().setMaze(maze);
		if (maze.getPlayers().get(player.getId()).getPos().equals(currentPos) && move != Movement.NOMOVE) {
			System.out.println("Sorry the cell is occupied by another player, please try again.");
		}
		System.out.println("333333333333333333");
		
		Peer.get().setNodeList(maze.peer.getNodeList());
		System.out.printf("NodeList size:%d, %d\n",maze.peer.getNodeList().size(),Peer.get().getNodeList().size());
		//updatePlayerService();
		
		return maze;
	}
	
	public Player joinGame() {
		try {
			return primaryService.joinGame(callbackService);
		} catch (RemoteException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	public boolean quitGame() {
		if (null == player) {
			System.out.println("The game hasn't started, you cannot quit at this time...");
			return false;
		}
		try {
			return primaryService.quitGame(player.getId());
		} catch (RemoteException e) {
			e.printStackTrace();
			return false;
		}
	}

	public Player getPlayer() {
		return player;
	}

	public void setPlayer(Player player) {
		this.player = player;
	}

	public void update(Maze maze) {
		player = maze.getPlayers().get(player.getId());
		setMaze(maze);
	}

	public static Maze getMaze() {
		return maze;
	}

	public static void setMaze(Maze maze) {
		ClientController.maze = maze;
	}
	
	public static void updatePlayerService() {
		if (0 == Peer.get().getNodeList().size()) {
			return;
		}
		try {
			System.out.println("lookup new primary service: " + Peer.get().getNodeList().get(0).getIp() + ":"+Peer.get().getNodeList().get(0).getPort());
			setPrimaryService((PlayerService) Naming.lookup(Util.getRMIStringByNode(Peer.get().getNodeList().get(0))));
		} catch (MalformedURLException | RemoteException | NotBoundException e) {
			e.printStackTrace();
		}
		if (Peer.get().getNodeList().size() >= 2) {
			try {
				System.out.println("lookup new secondary service: " + Peer.get().getNodeList().get(1).getIp() + ":"+Peer.get().getNodeList().get(1).getPort());
				setSecondaryService((PlayerService) Naming.lookup(Util.getRMIStringByNode(Peer.get().getNodeList().get(1))));
			} catch (MalformedURLException | RemoteException | NotBoundException e) {
				e.printStackTrace();
			}
		} else {
			try {
				System.out.println("lookup new secondary service: " + Peer.get().getNodeList().get(0).getIp() + ":"+Peer.get().getNodeList().get(0).getPort());
				setSecondaryService((PlayerService) Naming.lookup(Util.getRMIStringByNode(Peer.get().getNodeList().get(0))));
			} catch (MalformedURLException | RemoteException | NotBoundException e) {
				e.printStackTrace();
			}
		}
	}

}
