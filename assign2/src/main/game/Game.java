package main.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Game {
    private final int id;
    private final int secretNumber;
    private final List<Player> players;
    private boolean isOver;

    public Game(int id, int secretNumber) {
        this.id = id;
        this.secretNumber = secretNumber;
        this.players = new ArrayList<>();
        this.isOver = false;
    }

    public int getId() {
        return id;
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

    public void start() {
        while (!isOver) {
            for (Player player : players) {
                if (isOver) {
                    break;
                }
                int guess = player.makeGuess();
                if (guess == secretNumber) {
                    player.incrementScore(100);
                    isOver = true;
                } else {
                    int distance = Math.abs(guess - secretNumber);
                    int points = 100 - distance;
                    player.incrementScore(points);
                }
            }
        }
        // Notify players that the game is over
        for (Player player : players) {
            player.notifyGameOver();
        }
    }
}
