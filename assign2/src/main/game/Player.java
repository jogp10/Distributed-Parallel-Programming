package main.game;

import java.nio.channels.*;
import java.util.Objects;

public class Player {
    private int id;
    private int score;

    boolean guessed = false;
    private SocketChannel socketChannel;

    public Player(int id, SocketChannel socketChannel) {
        this.id = id;
        this.score = 0;
        this.socketChannel = socketChannel;
    }

    public int getId() {
        return id;}
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

    public void notifyGameOver() {
        // TODO Auto-generated method stub
    }

    public int makeGuess() {
        // TODO Auto-generated method stub
        return 0;
    }
}

