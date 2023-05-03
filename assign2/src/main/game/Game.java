package main.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Game {
    private final int id;
    private final int secretNumber;
    private final List<Player> players;

    private int[] distances;
    private boolean isOver;

    public Game(int id, int secretNumber, List<Player> players) {
        this.id = id;
        this.secretNumber = secretNumber;
        this.players = players;
        this.distances = new int[players.size()];
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
                } else {
                    int distance = Math.abs(guess - secretNumber);
                    int points = 50 - distance;
                    player.incrementScore(points);
                }

                if (allPlayersGuessed()) {
                    isOver = true;
                }
            }
        }
        gameOver();
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

    public int getDistance(Player player) {
        for (int i=0; i<players.size(); i++) {
            if(players.get(i).getId() == player.getId()) {
                return this.distances[i];
            }
        }
        return -1;
    }

    public void gameOver() {
        // Notify players that the game is over
        for (Player player : players) {
            player.notifyGameOver();
        }
    }
}
