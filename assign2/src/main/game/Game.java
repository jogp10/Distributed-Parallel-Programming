package main.game;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Game {
    private final int id;
    private final int secretNumber;
    private final List<Player> players;
    private static final int MAX_RANGE = 100;
    private static final int MIN_RANGE = 1;

    private static ExecutorService threadPoolPlayers;

    private int[] distances;
    private int[] guesses;
    private boolean isOver;

    public Game(int id, List<Player> players) {
        this.id = id;
        this.secretNumber = generateSecretNumber();
        this.players = players;
        this.distances = new int[players.size()];
        this.guesses = new int[players.size()];
        this.isOver = false;
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
        return isOver;
    }

    public void addPlayer(Player player) {
        players.add(player);
    }

    public void removePlayer(Player player) {
        players.remove(player);
    }

    public void start(ExecutorService executorService) {
        threadPoolPlayers = executorService;

        messageToPlayers("Game started! Guess a number between " + getMinRange() + " and " + getMaxRange());

        for (Player player : players) {
            threadPoolPlayers.execute(() -> {
                while (!isOver && !player.getGuessed()) {
                    player.receiveMessage("Make a guess: ");
                    int guess = player.makeGuess();
                    guess(player, guess);

                    if (allPlayersGuessed()) {
                        isOver = true;
                    }
                }
            });
        }
        gameOver();
    }

    private void messageToPlayers(String s) {
        for (Player player : players) {
            player.receiveMessage(s);
        }
    }

    private void messageToPlayer(Player p, String s) {
        p.receiveMessage(s);
    }

    public boolean allPlayersGuessed() {
        for (Player player : players) {
            if (!player.getGuessed()) {
                return false;
            }
        }
        return true;
    }

    public void madeGuess(Player player, int distance) {
        player.setGuessed(true);
        for (int i=0; i<players.size(); i++) {
            Player p = players.get(i);
            if (player.getId() == p.getId()) {
                this.distances[i] = distance;
            }
        }
    }

    public void guess(Player player, int guess) {
        int distance = Math.abs(getSecretNumber() - guess);
        player.setGuessed(true);
        player.updateScore(MAX_RANGE / 2 - distance);

        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            if (player.getId() == p.getId()) {
                this.distances[i] = distance;
                this.guesses[i] = guess;
            }
        }
    }

    public int getDistance(Player player) {
        for (int i=0; i<players.size(); i++) {
            if(players.get(i).getId() == player.getId()) {
                return this.distances[i];
            }
        }
        return -1;
    }

    public void gameOver() {
        messageToPlayers("All players have made a guess! The secret number was " + getSecretNumber());
        // Notify players that the game is over
        for (int i=0; i< players.size(); i++) {
            Player player = players.get(i);
            if (guesses[i] == getSecretNumber()) {
                messageToPlayer(player, "You guessed the secret number!");
                messageToPlayers("Player " + player.getUsername() + " guessed the secret number!");
            }
            messageToPlayer(player, "Your guess was " + getDistance(player) + " away from the secret number");
            player.notifyGameOver();
        }
    }

    public int getMaxRange() {
        return MAX_RANGE;
    }

    public int getMinRange() {
        return MIN_RANGE;
    }
}
