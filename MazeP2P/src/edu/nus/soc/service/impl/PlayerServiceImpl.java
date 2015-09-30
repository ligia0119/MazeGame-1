package edu.nus.soc.service.impl;

import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.server.RemoteServer;
import java.rmi.server.ServerNotActiveException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import edu.nus.soc.model.Maze;
import edu.nus.soc.model.Movement;
import edu.nus.soc.model.Node;
import edu.nus.soc.model.Peer;
import edu.nus.soc.model.Player;
import edu.nus.soc.model.Position;
import edu.nus.soc.service.CallBackService;
import edu.nus.soc.service.PlayerService;
import edu.nus.soc.service.controller.ClientController;
import edu.nus.soc.service.controller.ServerController;
import edu.nus.soc.util.Util;

public class PlayerServiceImpl extends UnicastRemoteObject implements PlayerService{

	private final ServerController serverController = ServerController.getController();
	
	private static Maze maze = Maze.get();
		
	public PlayerServiceImpl() throws RemoteException {
		super();
	}

	private static void collectTreasures(Player player) {
		
		Position currentPos = player.getPos();
		Map<Position, Integer> treasureMap = maze.getTreasureMap();
		
		if (treasureMap.containsKey(currentPos)) {			//collect treasure
			int cellTreasure = treasureMap.get(currentPos);
			int treasureNum = cellTreasure + player.getTreasureNum();
			player.setTreasureNum(treasureNum);
			
			System.out.println("Player "+ player.getId() +"collected "+
					cellTreasure +" treasures");
			Maze.get().setTreasureNum(Maze.get().getTreasureNum() - cellTreasure);
			
			treasureMap.put(currentPos, 0);
			maze.setTreasureMap(treasureMap);
		}
		
		Map<Integer, Player> players = maze.getPlayers();
		players.put(player.getId(), player);
		maze.setPlayers(players);
	}
	
	@Override
	public Player joinGame(CallBackService client) throws RemoteException{
		if (true == Maze.get().isGameStarted()) {
			return null;
		}
		synchronized (Util.joinLock) {
			Player player = serverController.handleJoinRequest(client);
			//create new Node in nodeMap
			String ipaddr = null;
			try {
				ipaddr = RemoteServer.getClientHost();
			} catch (ServerNotActiveException e) {
				e.printStackTrace();
			}
			return player;
		}
	}

	@Override
	public boolean quitGame(int playerId) throws RemoteException{
		if (false == Maze.get().isGameStarted()) {
			return false;
		}
		
		synchronized (Util.quitLock) {
			Map<Integer, Player> players = maze.getPlayers();
			players.remove(playerId);
			maze.setPlayers(players);
			
			//when a player quits game actively
			//the server does not hold its callback service
			serverController.removeCallbackService(playerId);
			System.out.println("Player "+ playerId +" quited the game");
			
			return true;
		}
	}

	//Modify game rule
	//Once a player occupies a cell, the others can not occupy the cell
	@Override
	public Maze move(Integer playerId, Movement m, Peer peer) throws RemoteException{
		System.out.println("Server recieved client's move request.");
		if (false == Maze.get().isGameStarted()) {
			return null;
		}
		
		synchronized (Util.moveLock) {
			Peer.get().setNodeList(removeCrashPlayer(Peer.get().getNodeList()));
			Player player = Maze.get().getPlayers().get(playerId);
			Position currentPos = player.getPos();
			int x = currentPos.getX();
			int y = currentPos.getY();
			Position targetPos;
			
			switch (m) {
			case N:
				targetPos = new Position(x, y-1);
				if(!isCellOccupied(targetPos, playerId)) {
					currentPos.setY(y-1);
				}
				break;
			case S:
				targetPos = new Position(x, y+1);
				if(!isCellOccupied(targetPos, playerId)) {
					currentPos.setY(y+1);
				}
				break;
			case E:
				targetPos = new Position(x+1, y);
				if(!isCellOccupied(targetPos, playerId)) {
					currentPos.setX(x+1);
				}
				break;
			case W:
				targetPos = new Position(x-1, y);
				if(!isCellOccupied(targetPos, playerId)) {
					currentPos.setX(x-1);
				}
				break;
			case NOMOVE:
				break;
			}
			player.setPos(currentPos);
			collectTreasures(player);
			
			
			//as primary server, we should synchronize maze info to secondary server
			if (Peer.get().isPrimaryServer()) {
				System.out.println("######I am the primary server, synchronize secondary server.");
				Maze.get().peer.setNodeList(Peer.get().getNodeList());
				if (Peer.get().getNodeList().size() >= 2) {
					try {
						if (Naming.lookup(Util.getRMIStringByNode(Peer.get().getNodeList().get(1))) != null) {
							ClientController.getSecondaryService().synchronizeMaze(Maze.get());
						} 
					} catch (MalformedURLException | NotBoundException | RemoteException e) {
						// find a new secondary server
						System.out.println("######secondary server has crashed, select a new secondary server.");
						Peer.get().getNodeList().remove(1);
						while (Peer.get().getNodeList().size() >= 2) {
							try {
								if (Naming.lookup(Util.getRMIStringByNode(Peer.get().getNodeList().get(1))) != null) {
									ClientController.updatePlayerService();
									ClientController.getSecondaryService().notifySelectedAsServer(Maze.get());
									break;
								} 
							} catch (MalformedURLException | NotBoundException | RemoteException e1) {
								// TODO Auto-generated catch block
								Peer.get().getNodeList().remove(1);
							}
						}
					}
				}
			}

			//as secondary server, when movement is requested, the primary server is down at this time.
			//we should level up secondary server as primary server and select another secondary server.
			if (Peer.get().isSecondaryServer()) {
				System.out.println("######I am the secondary server, primary server has crashed, level"
						+ "up myself and select another secondary server.");
				ServerController.levelUpToPrimaryServer();
				Maze.get().peer.setNodeList(Peer.get().getNodeList());
				//select a new secondary server
				if (Peer.get().getNodeList().size() >= 2) {
					while (Peer.get().getNodeList().size() >= 2) {
						try {
							if (Naming.lookup(Util.getRMIStringByNode(Peer.get().getNodeList().get(1))) != null) {
								//find active node
								break;
							}
						} catch (MalformedURLException | NotBoundException | RemoteException e) {
							Peer.get().getNodeList().remove(1);
						}
					}
				} else {
					System.out.println("There is only one Peer remains in the system, no secondary"
							+ "server is needed");
				}
				ClientController.updatePlayerService();
				if (Peer.get().getNodeList().size() >= 2) {
					ClientController.getSecondaryService().notifySelectedAsServer(Maze.get());
				}
			}
		
			System.out.println("Player " + player.getId() + " moved!");
			maze.peer.setNodeList(Peer.get().getNodeList());
			return maze;
		}
	}
	
	private static List<Node> removeCrashPlayer(List<Node> nodeList) {
		Iterator<Node> nodeIter = nodeList.iterator();
		while (nodeIter.hasNext()) {
			Node node = nodeIter.next();
			try {
				PlayerService playerService = (PlayerService) Naming.lookup(Util.getRMIStringByNode(node));
			} catch (MalformedURLException | RemoteException | NotBoundException e) {
				int playerId = node.getPlayerId();
				nodeIter.remove();
				Map<Integer, Player> playerMap = maze.getPlayers();
				playerMap.remove(playerId);
				System.out.println("Current players number is " + maze.getPlayers().size());
			}
		}
		return nodeList;
	}

	@Override
	public void synchronizeMaze(Maze maze) throws RemoteException {
		System.out.println("<secondary>maze info synchronized from primary server.");
		Maze.get().setMaze(maze);
		Peer.get().setNodeList(maze.peer.getNodeList());
		//System.out.println("nodeList size:" + Peer.get().getNodeList().size());
		ClientController.updatePlayerService();
	}

	@Override
	public void notifySelectedAsServer(Maze maze) throws RemoteException {
		System.out.println("this peer is selected as secondary server, update info.");
		Peer.get().setPrimaryServer(false);
		Peer.get().setSecondaryServer(true);
		Peer.get().setNodeList(maze.peer.getNodeList());
		ClientController.updatePlayerService();
	}

	private static boolean isCellOccupied(Position targetPos, int playerId) {
		Map<Integer, Player> players = Maze.get().getPlayers();
		for (Player p : players.values()) {
			Position pos = p.getPos();
			if (pos.equals(targetPos) && p.getId() != playerId) {
				return true;
			}
		}
		return false;
	}
}
