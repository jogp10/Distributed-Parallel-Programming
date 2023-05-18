package main.game;

import main.server.Server;
import main.utils.MessageType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import main.utils.ConcurrentHashMap;

import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Game implements Runnable {
    private final int id;
    private final int secretNumber;
    private final List<Player> players;
    private static final int MAX_RANGE = 100;
    private static final int MIN_RANGE = 1;
    private final ConcurrentHashMap<Player, Integer> playerGuesses = new ConcurrentHashMap<Player, Integer>();
    private final Lock guessLock = new ReentrantLock();
    private final Condition guessReceived = guessLock.newCondition();
    private final ExecutorService threadPoolPlayers;


    public Game(int id, List<Player> players, ExecutorService executorService) {
        this.id = id;
        this.secretNumber = generateSecretNumber();
        this.players = players;
        this.threadPoolPlayers = executorService;
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

    public void setPlayerGuess(Player player, int guess) {
        playerGuesses.put(player, guess);
    }

    public void addPlayer(Player player) {
        players.add(player);
    }

    public void removePlayer(Player player) {
        players.remove(player);
    }

    public boolean allPlayersGuessed() {
        for (Player player : players) {
            if (!playerGuessed(player)) {
                return false;
            }
        }
        return true;
    }

    public boolean playerGuessed(Player player) {
        return playerGuesses.containsKey(player);
    }

    public void guess(Player player, int guess) {
        playerGuesses.put(player, guess);
        int distance = getDistance(player);
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

        List<Callable<Void>> tasks = new ArrayList<>();
        for (Player player : players) {
            Callable<Void> task = () -> {
                while (!allPlayersGuessed() && !playerGuessed(player)) {
                    waitForGuess(player);

                    if (playerGuessed(player)) {
                        System.out.println("Player " + player.getUsername() + " has guessed.");;
                        int guess = playerGuesses.get(player);
                        guess(player, guess);

                        if (!allPlayersGuessed()) {
                            Server.sendMessageToPlayer(player, "Waiting for other players to guess...");
                        }
                    }
                }
                return null;
            };
            tasks.add(task);
        }

        try {
            List<Future<Void>> futures = threadPoolPlayers.invokeAll(tasks);
            for (Future<Void> future : futures) {
                future.get(); // This blocks and waits for the task to complete
            }

            // All Players have guessed.
            System.out.println("All Players have guessed.");
            Server.sendMessageToPlayers(this, "All players have guessed! The secret number was " + getSecretNumber());

            for (Player p: players) {
                int distance = getDistance(p);
                if (distance == 0) {
                    Server.sendMessageToPlayer(p, "You guessed the secret number!");
                    Server.sendMessageToPlayers(this, "Player " + p.getUsername() + " guessed the secret number!");
                }
                Server.sendMessageToPlayer(p, "Your guess was " + distance + " away from the secret number");
                Server.sendMessageToPlayer(p, "Your score is " + p.getScore());
            }

        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    private void waitForGuess(Player player) {
        guessLock.lock();
        try {
            while (!playerGuessed(player)) {
                guessReceived.await();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            guessLock.unlock();
        }
    }

    public void signalGuessReceived() {
        guessLock.lock();
        try {
            guessReceived.signalAll();
        } finally {
            guessLock.unlock();
        }
    }

    public ExecutorService getThreadPoolPlayers() {
        return threadPoolPlayers;
    }
}