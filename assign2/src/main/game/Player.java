package main.game;

import main.utils.Helper;

import java.nio.channels.*;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

public class Player {
    private final int id;
    private int score;
    private String username;
    private String sessionToken;
    private int gamesPlayed;
    boolean guessed = false;
    boolean absent = false;
    private SocketChannel socketChannel;
    private Timer waitTimer;
    private int waitingTime = 0;
    private boolean inQueue = false;

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

    public void setSocketChannel(SocketChannel socketChannel) {
        this.socketChannel = socketChannel;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public void incrementScore(int points) {
        this.score += points;
        if (this.score < 0) this.score = 0;
    }

    public void setGamesPlayed(int gamesPlayed) {
        this.gamesPlayed = gamesPlayed;
    }

    public int getGamesPlayed() {
        return this.gamesPlayed;
    }

    public void setAbsent(boolean b) {
        absent = b;
    }

    public boolean getAbsent() {
        return absent;
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
        return this.socketChannel == other.socketChannel;
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

    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public void startWaitTimer() {
        waitTimer = new Timer();
        inQueue = true;
        waitTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                waitingTime++;
                //System.out.println("Waiting time: " + waitingTime + " seconds");
            }
        }, 0, 1000); // 1000 milliseconds = 1 second
    }

    public void stopWaitTimer() {
        if (waitTimer != null) {
            waitTimer.cancel();
            waitTimer = null;
        }
        inQueue = false;
        waitingTime = 0;
    }

    public int getWaitingTime() {
        return waitingTime;
    }

    public boolean isInQueue() {
        return inQueue;
    }


    public void notifyGameOver() {
        // TODO Auto-generated method stub
        this.setGuessed(false);
    }

    public int makeGuess() {
        // TODO Auto-generated method stub
        this.setGuessed(true);
        return 0;
    }


}

