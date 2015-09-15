package edu.nus.soc.service.impl;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import edu.nus.soc.model.Maze;
import edu.nus.soc.model.Movement;
import edu.nus.soc.model.Player;
import edu.nus.soc.model.Position;
import edu.nus.soc.service.CallBackService;
import edu.nus.soc.service.PlayerService;

public class PlayerServiceImpl extends UnicastRemoteObject implements PlayerService{
	
	private static boolean gameStarted = false;
	private static Timer timer = new Timer(true);
	private static Map<Integer,CallBackService> callbackMap = new HashMap<Integer, CallBackService>();
	private static TimerTask startGame = new TimerTask() {

		@Override
		public void run() {
			// TODO Auto-generated method stub
			System.out.println("Timer function starts...");
			//execute all callback methods, notify clients game starts.
			for (Integer key : callbackMap.keySet()) {
				System.out.println("callback key: " + key);
				try {
					callbackMap.get(key).startGame();
				} catch (RemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			setGameStarted(true);
		}
		
	};
	
	public PlayerServiceImpl() throws RemoteException {
		super();
		// TODO Auto-generated constructor stub
	}

	@Override
	public Player joinGame(CallBackService client) throws RemoteException{
		if (true == isGameStarted()) {
			return null;
		}

		int currentId = Maze.get().getCurrentId();
		
		Player player = new Player(currentId);
		Map<Integer, Player> players = Maze.get().getPlayers();
		
		/**
		 * first player join, start timer
		 */
		if (0 == players.size()) {
			//execute callback functions to notify clients the start of Game.
			timer.schedule(startGame, 1000 * 20);
		}

		Position playerPos = Util.getRandomPos(Maze.get().getSize());
		player.setPos(playerPos);
		
		players.put(currentId, player);
		Maze.get().setCurrentId(currentId + 1);
		
		/**
		 * store client callback in hash map.
		 */
		callbackMap.put(currentId, client);
		
		currentId = Maze.get().getCurrentId();
		//for debug
		System.out.println("currentId:"+currentId);
		System.out.println("Recieved client's request to join into a game.");
		
		return player;
	}

	@Override
	public boolean quitGame(int playerId) throws RemoteException{
		if (false == isGameStarted()) {
			return false;
		}
		Map<Integer, Player> players = Maze.get().getPlayers();
		players.remove(playerId);
		Maze.get().setPlayers(players);
		System.out.println("Player "+ playerId +" quited the game");
		
		return true;
	}

	@Override
	public Maze move(Player player, Movement m) throws RemoteException{
		if (false == isGameStarted()) {
			return null;
		}
		Position currentPos = player.getPos();
		int x = currentPos.getX();
		int y = currentPos.getY();
		switch (m) {
		case N:
			currentPos.setY(y-1);
			break;
		case S:
			currentPos.setY(y+1);
			break;
		case E:
			currentPos.setX(x+1);
			break;
		case W:
			currentPos.setX(x-1);
			break;
		case NOMOVE:
			break;
		}
		player.setPos(currentPos);
		collectTreasures(player);
		System.out.println("Player moved!");
		return Maze.get();
	}
	
	private static void collectTreasures(Player player) {
		Position currentPos = player.getPos();
		Maze maze = Maze.get();
		Map<Position, Integer> treasureMap = maze.getTreasureMap();
		if (treasureMap.containsKey(currentPos)) {
			int cellTreasure = treasureMap.get(currentPos);
			int treasureNum = cellTreasure + player.getTreasureNum();
			player.setTreasureNum(treasureNum);
			System.out.println("Player "+ player.getId() +"collected "+
					cellTreasure +" treasures");
			treasureMap.put(currentPos, 0);
			maze.setTreasureMap(treasureMap);
		}
		Map<Integer, Player> players = maze.getPlayers();
		players.put(player.getId(), player);
		maze.setPlayers(players);
	}

	public static boolean isGameStarted() {
		return gameStarted;
	}

	public static void setGameStarted(boolean gameStarted) {
		PlayerServiceImpl.gameStarted = gameStarted;
	}
	
}
