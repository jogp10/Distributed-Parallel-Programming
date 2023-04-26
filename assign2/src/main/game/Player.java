package main.game;

import java.nio.channels.*;
import java.util.Objects;

public class Player {
    private final int id;
    private int score;
    private String username;


    boolean guessed = false;
    private SocketChannel socketChannel;

    public Player(int id, SocketChannel socketChannel) {
        this.id = id;
        this.score = 0;
        this.socketChannel = socketChannel;
    }

    public Player(int id, int score, SocketChannel socketChannel) {
        this.id = id;
        this.score = score;
        this.socketChannel = socketChannel;
    }

    public Player(int id, String username, SocketChannel socketChannel) {
        this.id = id;
        this.username = username;
        this.score = 0;
        this.socketChannel = socketChannel;
    }

    public Player(int id, String username, int score, SocketChannel socketChannel) {
        this.id = id;
        this.username = username;
        this.score = score;
        this.socketChannel = socketChannel;
    }

    public int getId() {
        return id;
    }
    public int getScore() {
        return score;
    }

    public SocketChannel getSocketChannel() {
        return socketChannel;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public void incrementScore(int points) {
        if (this.score < -points) this.score = 0;
        this.score += points;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Player other = (Player) obj;
        if (this.id != other.id) {
            return false;
        }
        if (this.socketChannel != other.socketChannel) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 59 * hash + this.id;
        hash = 59 * hash + Objects.hashCode(this.socketChannel);
        return hash;
    }

    public void setGuessed(boolean b) {
        guessed = b;
    }

    public boolean getGuessed() {
        return guessed;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }

    public void notifyGameOver() {
        // TODO Auto-generated method stub
    }

    public int makeGuess() {
        // TODO Auto-generated method stub
        return 0;
    }
}

