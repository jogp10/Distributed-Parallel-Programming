package main.server;


import main.game.Player;
import main.game.Game;
import main.utils.Helper;
import main.utils.MessageType;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private static final int PORT = 12345;
    private static final int MAX_PLAYERS = 2;
    private static final int MAX_GAMES = 10;
    private static final int BUFFER_SIZE = 1024;

    private static int playerCount = 0;
    private static List<Player> waitQueue = new ArrayList<>();
    private static List<Player> unauthenticatedPlayers = new ArrayList<>();
    private static List<Game> activeGames = new ArrayList<>();
    private static final ExecutorService threadPool = Executors.newFixedThreadPool(MAX_GAMES);
    private static int gameCount = 0;

    public static void main(String[] args) throws IOException {
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.socket().bind(new InetSocketAddress(PORT));
        Selector selector = Selector.open();
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        System.out.println("Server is up and running");

        while (true) {
            selector.select();
            Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
            while (keyIterator.hasNext()) {
                SelectionKey key = keyIterator.next();
                keyIterator.remove();

                if (key.isAcceptable()) {
                    SocketChannel clientSocketChannel = ((ServerSocketChannel) key.channel()).accept();
                    clientSocketChannel.configureBlocking(false);
                    clientSocketChannel.register(selector, SelectionKey.OP_READ);
                    Player player = new Player(playerCount++, clientSocketChannel);
                    unauthenticatedPlayers.add(player);
                    sendMessageToPlayer(player, MessageType.AUTHENTICATION_REQUEST.toHeader());
                } else if (key.isReadable()) {
                    SocketChannel clientSocketChannel = (SocketChannel) key.channel();
                    ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
                    int bytesRead = -1;
                    try{
                        bytesRead = clientSocketChannel.read(buffer);
                    }
                    catch (SocketException e) {
                        // Handle the "Connection reset" exception
                        System.err.println("Connection reset by peer: " + e.getMessage());
                        removePlayer(clientSocketChannel);
//                        clientSocketChannel.close();
                    }
                    catch (IOException e) {
                        // Handle other I/O exceptions
                        e.printStackTrace();
                    }

                    if (bytesRead == -1) {
                        // main.client.Client has disconnected
                        removePlayer(clientSocketChannel);
                    } else {
                        buffer.flip();
                        String message = new String(buffer.array(), 0, bytesRead);
                        handleMessage(clientSocketChannel, message);
                    }
                }
            }

            // Start a new game if enough players are in the wait queue
            if (waitQueue.size() >= MAX_PLAYERS && activeGames.size() < MAX_GAMES) {
                //matchmaking
                List<Player> players = new ArrayList<>();
                for (int i = 0; i < MAX_PLAYERS; i++) {
                    Player player = waitQueue.remove(0);
                    players.add(player);
                }

                threadPool.submit(() -> {
                    Game game = new Game(getAndIncrementGameCount(), players);
                    activeGames.add(game);
                    sendMessageToPlayers(game, "Game started! Guess a number between " + game.getMinRange() + " and " + game.getMaxRange());
                });
            }
        }
    }


    private static void handleMessage(SocketChannel clientSocketChannel, String message, MessageType messageType){

    }
    private static void handleMessage(SocketChannel clientSocketChannel, String message) {

        switch (Helper.parseMessageType(message)) {
            case GAME_GUESS -> handleGuessMessage(clientSocketChannel, Helper.parseMessage(message));
            case AUTHENTICATION_ATTEMPT -> handleAuthentication(clientSocketChannel, Helper.parseMessage(message));
            case DEFAULT ->
                // Handle invalid message format or unsupported type
                    System.err.println("Invalid message format or unsupported type: " + message);
        }


    }

    private static void handleGuessMessage(SocketChannel clientSocketChannel, String message) {
        // Find the player associated with this client socket channel
        Player player = getPlayer(clientSocketChannel);
        if (player == null) {
            return;
        }

        // Find the game that the player is currently in
        Game game = getGame(player);
        if (game == null) {
            return;
        }

        // Process the message (i.e., guess)
        int guess;
        try {
            guess = Integer.parseInt(message.trim());
        }
        catch (NumberFormatException e) {
            sendMessageToPlayer(player, "Invalid guess");
            return;
        }

        // Mark the player as having made a guess
        game.guess(player, guess);

        // End the game if all players have made a guess
        if(game.allPlayersGuessed()){
            sendMessageToPlayers(game, "All players have made a guess! The secret number was " + game.getSecretNumber());

            if (guess == game.getSecretNumber()) {
                sendMessageToPlayer(player, "You guessed the secret number!");
                sendMessageToPlayers(game, "Player " + player.getUsername() + " guessed the secret number!");
            } else {
                for (Player p : game.getPlayers()) {
                    sendMessageToPlayer(p, "Your guess was " + game.getDistance(p) + " away from the secret number");
                }
            }

            endGame(game);
        } else {
            sendMessageToPlayer(player, "Waiting for other players to guess...");
        }
    }

    private static void endGame(Game game) {
        activeGames.remove(game);
        game.gameOver();

        for (Player p : game.getPlayers()) {
            sendMessageToPlayer(p, "Your score is " + p.getScore());
            waitQueue.add(p);
        }
    }

    private static void handleAuthentication(SocketChannel clientSocketChannel, String parseMessage) {
        //for now just send back the message, change later
        sendMessage(clientSocketChannel, "<AUTHENTICATION_SUCCESSFUL>");
        Player player = getUnauthenticatedPlayer(clientSocketChannel);
        waitQueue.add(player);
        unauthenticatedPlayers.remove(player);
    }

    private static void removePlayer(SocketChannel clientSocketChannel) {
        // Remove the player from the wait queue or active game
        Player player = getPlayer(clientSocketChannel);
        if (player != null) {
            waitQueue.remove(player);
            Game game = getGame(player);
            if (game != null) {
                game.removePlayer(player);
                sendMessageToPlayers(game, "Player " + player.getUsername() + " has left the game");
                if (game.getPlayers().isEmpty()) {
                    activeGames.remove(game);
                }
            }
        }
    }

    private static Player getPlayer(SocketChannel clientSocketChannel) {
        for (Player player : waitQueue) {
            if (player.getSocketChannel() == clientSocketChannel) {
                return player;
            }
        }
        for (Game game : activeGames) {
            for (Player player : game.getPlayers()) {
                if (player.getSocketChannel() == clientSocketChannel) {
                    return player;
                }
            }
        }
        return null;
    }
 private static Player getUnauthenticatedPlayer(SocketChannel clientSocketChannel) {
        for (Player player : unauthenticatedPlayers) {
            if (player.getSocketChannel() == clientSocketChannel) {
                return player;
            }
        }
        return null;
    }

    private static Game getGame(Player player) {
        for (Game game : activeGames) {
            if (game.getPlayers().contains(player)) {
                return game;
            }
        }
        return null;
    }

    private static void sendMessageToPlayers(Game game, String message) {
        for (Player player : game.getPlayers()) {
            sendMessage(player.getSocketChannel(), message);
        }
    }

    private static void sendMessage(SocketChannel clientSocketChannel, String message) {
        try {
            message += "\t";
            ByteBuffer buffer = ByteBuffer.wrap(message.getBytes());
            //append \t to the end of the message
            clientSocketChannel.write(buffer);
            while (buffer.hasRemaining()) {
                clientSocketChannel.write(buffer);
            }

            // Clear the buffer after all data has been written
            buffer.clear();
        } catch (IOException e) {
            removePlayer(clientSocketChannel);
        }
    }

    private static void sendMessageToPlayer(Player player, String message) {
        sendMessage(player.getSocketChannel(), message);
    }


    //function that inrements the game count and returns the value
    // it must be synchronized because it is called from multiple threads
    private static synchronized int getAndIncrementGameCount() {
        return ++gameCount;
    }
}