package main.game;

import main.server.Server;
import main.utils.MessageType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

public class Game implements Runnable {
    private final int id;
    private final int secretNumber;
    private final List<Player> players;
    private static final int MAX_RANGE = 100;
    private static final int MIN_RANGE = 1;
    private static ExecutorService threadPoolPlayers;
    private final Map<Player, Integer> playerGuesses = new ConcurrentHashMap<>();

    public Game(int id, List<Player> players, ExecutorService executorService) {
        this.id = id;
        this.secretNumber = generateSecretNumber();
        this.players = players;
        threadPoolPlayers = executorService;
    }

    public int getId() {
        return id;
    }

    private static int generateSecretNumber() {
        return new Random().nextInt(MAX_RANGE - MIN_RANGE + 1) + MIN_RANGE;
    }

    public int getSecretNumber() {
        return secretNumber;
    }

    public List<Player> getPlayers() {
        return players;
    }

    public boolean isOver() {
        return allPlayersGuessed();
    }

    public void addPlayer(Player player) {
        players.add(player);
    }

    public void removePlayer(Player player) {
        players.remove(player);
    }

    public boolean allPlayersGuessed() {
        for (Player player : players) {
            if (!player.getGuessed()) {
                return false;
            }
        }
        return true;
    }

    public void guess(Player player, int guess) {
        playerGuesses.put(player, guess);
        int distance = getDistance(player);
        player.setGuessed(true);
        player.updateScore(distance != 0 ? MAX_RANGE / 2 - distance : 100);
    }

    public int getDistance(Player player) {
        return Math.abs(playerGuesses.getOrDefault(player, -1) - getSecretNumber());
    }

    public int getMaxRange() {
        return MAX_RANGE;
    }

    public int getMinRange() {
        return MIN_RANGE;
    }

    @Override
    public void run() {
        Server.sendMessageToPlayers(this, MessageType.GAME_GUESS_REQUEST.toHeader() + "Game started! Guess a number between " + getMinRange() + " and " + getMaxRange());

        for (Player player : players) {
            threadPoolPlayers.execute(() -> {
                while (!allPlayersGuessed() && !player.getGuessed()) {
                    int guess = player.makeGuess();
                    guess(player, guess);
                }
            });
        }
        while (!allPlayersGuessed()) {};
    }
}
