package main.server;


import main.game.Player;
import main.game.Game;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;

public class Server {
    private static final int PORT = 12345;
    private static final int MAX_PLAYERS = 4;
    private static final int MIN_RANGE = 1;
    private static final int MAX_RANGE = 100;
    private static final int BUFFER_SIZE = 1024;

    private static int playerCount = 0;
    private static List<Player> waitQueue = new ArrayList<>();
    private static List<Game> activeGames = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.socket().bind(new InetSocketAddress(PORT));
        Selector selector = Selector.open();
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

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
                    waitQueue.add(player);
                } else if (key.isReadable()) {
                    SocketChannel clientSocketChannel = (SocketChannel) key.channel();
                    ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
                    int bytesRead = clientSocketChannel.read(buffer);
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
            if (waitQueue.size() >= MAX_PLAYERS) {
                int secretNumber = generateSecretNumber();
                List<Player> players = new ArrayList<>();
                for (int i = 0; i < MAX_PLAYERS; i++) {
                    Player player = waitQueue.remove(0);
                    players.add(player);
                }
                Game game = new Game(secretNumber, generateSecretNumber(), players);
                activeGames.add(game);
                sendMessageToPlayers(game, "main.game.Game started! Guess a number between " + MIN_RANGE + " and " + MAX_RANGE);
            }
        }
    }

    private static int generateSecretNumber() {
        return new Random().nextInt(MAX_RANGE - MIN_RANGE + 1) + MIN_RANGE;
    }

    private static void handleMessage(SocketChannel clientSocketChannel, String message) {
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
        int guess = Integer.parseInt(message.trim());
        int distance = Math.abs(game.getSecretNumber() - guess);
        player.setScore(player.getScore() + (MAX_RANGE - distance));
        sendMessageToPlayers(game, "main.game.Player " + player.getId() + " guessed " + guess + " and was " + distance + " away from the secret number");
        if (guess == game.getSecretNumber()) {
            sendMessageToPlayers(game, "main.game.Player " + player.getId() + " guessed the secret number!");
            activeGames.remove(game);
        } else {
            player.setGuessed(true);
        }
        // End the game if all players have made a guess
        //if (game.isAllPlayersGuessed()) {
        if(true){
            sendMessageToPlayers(game, "All players have made a guess! The secret number was " + game.getSecretNumber());
            activeGames.remove(game);
        }
    }

    private static void removePlayer(SocketChannel clientSocketChannel) {
        // Remove the player from the wait queue or active game
        Player player = getPlayer(clientSocketChannel);
        if (player != null) {
            waitQueue.remove(player);
            Game game = getGame(player);
            if (game != null) {
                game.removePlayer(player);
                sendMessageToPlayers(game, "main.game.Player " + player.getId() + " has left the game");
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
            ByteBuffer buffer = ByteBuffer.wrap(message.getBytes());
            clientSocketChannel.write(buffer);
        } catch (IOException e) {
            removePlayer(clientSocketChannel);
        }
    }
}