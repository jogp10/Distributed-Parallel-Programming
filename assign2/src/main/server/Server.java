package main.server;


import main.game.Player;
import main.game.Game;
import main.utils.Helper;
import main.utils.MessageType;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static main.utils.Helper.MESSAGE_TERMINATOR;

import static java.lang.Math.abs;


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
    private static double ratio = 10;

    private static final Lock lock = new ReentrantLock();


    public static void main(String[] args) throws IOException {
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.socket().bind(new InetSocketAddress(PORT));
        Selector selector = Selector.open();
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        System.out.println("Server is up and running");

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                updateAllPlayers();
            }
        });

        new Thread(() -> {
            while (true) {
                // Start a new game if enough players are in the wait queue
                if (waitQueue.size() >= MAX_PLAYERS) {

                    List<List<Integer>> eligibleGame  = new ArrayList<>();
                    //matchmaking
                    Iterator<Player> iterator = waitQueue.iterator();
                    while (iterator.hasNext()) {
                        Player player = iterator.next();
                        if (player.getAbsent()) {
                            iterator.remove();
                            removePlayerNotInQueue(player); //maybe this is not even needed
                            continue;
                        }
                        List<Integer> eligPlayers = findEligibleOpponents(player, waitQueue);
                        eligibleGame.add(eligPlayers);
                    }


                    //Intersect all the lists
                    List<Integer> intersect = eligibleGame.get(0);
                    for(int i = 1; i < eligibleGame.size(); i++) {
                        intersect.retainAll(eligibleGame.get(i));
                    }
                    //Create a new game with the intersected players
                    if(intersect.size()>= MAX_PLAYERS && activeGames.size() < MAX_GAMES) {
                        System.out.println("Creating a new game with players: " + intersect);
                        List<Player> players = new ArrayList<>();
                        for (int i = 0; i < MAX_PLAYERS; i++) {
                            Player player = waitQueue.remove(0);
                            players.add(player);
                            player.stopWaitTimer();
                        }

                        threadPool.submit(() -> {
                            Game game = new Game(getAndIncrementGameCount(), players);
                            activeGames.add(game);
                            sendMessageToPlayers(game, "Game started! Guess a number between " + game.getMinRange() + " and " + game.getMaxRange());
                        });
                    }
                }

                    // Sleep for some time to avoid high CPU usage
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // Handle the exception
                }
            }
        }).start();


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
                        key.cancel();
                        Player player = getPlayer(clientSocketChannel);
                        //todo change from queue  to everyplace

                        if(player != null) {
                            if (player.isInQueue()) {
                                suspendPlayer(player);
                            }
                            else {
                                updatePlayerEntry(player);
                                removePlayerNotInQueue(player);
                            }
                            String username = player.getUsername();
                            System.err.println("Player " + username + " disconnected");
                        }

                    }
                    catch (IOException e) {
                        // Handle other I/O exceptions
                        e.printStackTrace();
                    }

                    if (bytesRead != -1) {
                        buffer.flip();
                        String message = new String(buffer.array(), 0, bytesRead);
                        handleMessage(clientSocketChannel, message);
                    }
                }

                /*
                List<Player> players = new ArrayList<>();
                for (int i = 0; i < MAX_PLAYERS; i++) {
                    Player player = waitQueue.remove(0);
                    players.add(player);
                }

                Game game = new Game(getAndIncrementGameCount(), generateSecretNumber(), players);
                activeGames.add(game);
                sendMessageToPlayers(game, "Game started! Guess a number between " + MIN_RANGE + " and " + MAX_RANGE);*/
            }
        }
    }


    private static void handleMessage(SocketChannel clientSocketChannel, String message) {

        switch (Helper.parseMessageType(message)) {
            case GAME_GUESS -> handleGuessMessage(clientSocketChannel, Helper.parseMessage(message));
            case AUTHENTICATION_ATTEMPT -> handleAuthentication(clientSocketChannel, Helper.parseMessage(message));
            case AUTHENTICATION_ATTEMPT_TOKEN -> handleAuthenticationToken(clientSocketChannel, Helper.parseMessage(message));
            case DEFAULT ->
                // Handle invalid message format or unsupported type
                    System.err.println("Invalid message format or unsupported type: " + message);
        }


    }

    private static void handleAuthenticationToken(SocketChannel clientSocketChannel, String parseMessage) {
        String[] tokens = parseMessage.split(";");
        String sessionToken = tokens[0];

        //iterate waitQueue and find the player with the sessionToken
        for(Player player : waitQueue) {
            if(player.getSessionToken().equals(sessionToken)) {
                if(player.getAbsent()) {
                    unsuspendPlayer(player);
                    player.setSocketChannel(clientSocketChannel);
                    //add player to authenticatedPlayers
                    //send message to player
                    sendMessageToPlayer(player, MessageType.AUTHENTICATION_SUCCESSFUL.toHeader() + "Successfully rejoined the wait queue.");
                    return;
                }
                else {
                    sendMessage(clientSocketChannel, MessageType.AUTHENTICATION_FAILURE.toHeader() + "Player is already in the wait queue.");
                    return;
                }

            }
        }

        //in case the player was not found in the waitQueue
        sendMessage(clientSocketChannel, MessageType.AUTHENTICATION_FAILURE.toHeader() + "Invalid session token.");
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
            p.startWaitTimer();
            waitQueue.add(p);
        }
    }

    private static void handleAuthentication(SocketChannel clientSocketChannel, String parseMessage) {
        //split message on ';'
        String[] tokens = parseMessage.split(";");
        String username = tokens[0];
        String password = tokens[1];
        String csvFile = "users.csv";

        //check that the player is not currently loggedIn
        if(isLoggedIn(username)){
            sendMessage(clientSocketChannel, MessageType.INFO.toHeader() + "The user is already logged in.");
            sendMessage(clientSocketChannel, MessageType.AUTHENTICATION_FAILURE.toHeader() + "Already logged in!");
            return;
        }

        boolean loginFinished = readFileToLookupPlayer(username , password, clientSocketChannel);

        if (!loginFinished) {
            writeNewPlayerToFile(username, password, clientSocketChannel);
        }

        /*
        try (BufferedReader reader = new BufferedReader(new FileReader(csvFile))) {
            String line;
            boolean headerLine = true;
            while ((line = reader.readLine()) != null) {
                if(headerLine){
                    headerLine = false;
                    continue;
                }
                String[] fields = line.split(",");
                String csvUsername = fields[0];
                String csvPassword = fields[1];
                int csvScore = Integer.parseInt(fields[2]);
                int csvGamesPlayed = Integer.parseInt(fields[3]);

                if (csvUsername.equals(username)) {
                    if (Helper.verifyPassword(password, csvPassword)) {
                        sendMessage(clientSocketChannel, MessageType.INFO.toHeader() + "Successfully logged in.");
                        sendMessage(clientSocketChannel, MessageType.AUTHENTICATION_SUCCESSFUL.toHeader());

                        Player player = getUnauthenticatedPlayer(clientSocketChannel);
                        assert player != null;
                        player.setUsername(username);
                        player.setScore(csvScore);
                        player.setGamesPlayed(csvGamesPlayed);
                        String newSessionToken = Helper.generateSessionToken();
                        player.setSessionToken(newSessionToken);
                        sendMessageToPlayer(player, MessageType.INFO.toHeader() + "Here is your session token: " + newSessionToken
                                + "\nPlease use this token to reconnect to the server.");

                        waitQueue.add(player);
                        player.startWaitTimer();
                        unauthenticatedPlayers.remove(player);

                    }
                    else {
                        // Password incorrect
                        sendMessage(clientSocketChannel, MessageType.INFO.toHeader() + "Incorrect password. Please try again.");
                        sendMessage(clientSocketChannel, MessageType.AUTHENTICATION_FAILURE.toHeader());
                    }
                    return;
                }
            }
        }
        catch (IOException e) {
            System.err.println("Error reading CSV file: " + e.getMessage());
            sendMessage(clientSocketChannel, MessageType.AUTHENTICATION_FAILURE.toHeader());
            return;
        }



        //if username was not in the database
        // Add new user to CSV
        try (FileWriter writer = new FileWriter(csvFile, true)) {

            String hashedPassword = Helper.hashPassword(password);
            String newSessionToken = Helper.generateSessionToken();

            String newLine = username + "," + hashedPassword + "," + 0 + "," + 0 + "," + newSessionToken + "\n";
            writer.write(newLine);

            Player player = getUnauthenticatedPlayer(clientSocketChannel);
            assert player != null;
            player.setUsername(username);
            player.setScore(0);
            player.setGamesPlayed(0);
            player.setSessionToken(newSessionToken);
            waitQueue.add(player);
            player.startWaitTimer();
            unauthenticatedPlayers.remove(player);

            sendMessage(clientSocketChannel, MessageType.INFO.toHeader() + "Successfully registered as a new user.");
            sendMessage(clientSocketChannel, MessageType.AUTHENTICATION_SUCCESSFUL.toHeader());

        } catch (IOException e) {
            System.err.println("Error writing to CSV file: " + e.getMessage());
            sendMessage(clientSocketChannel, MessageType.AUTHENTICATION_FAILURE.toHeader());
        }

        */
    }

    private static void removePlayer(SocketChannel clientSocketChannel) {
        // Remove the player from the wait queue or active game
        Player player = getPlayer(clientSocketChannel);
        removePlayer(player);
    }
    private static void removePlayer(Player player) {
        // Remove the player from the wait queue or active game
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

    private static void removePlayerNotInQueue(Player player){
        if (player != null) {
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

    private static void suspendPlayer(Player player){
        player.setAbsent(true);
    }

    private static void unsuspendPlayer(Player player){
        player.setAbsent(false);
    }

    private static boolean isLoggedIn(String username){
        for (Player player: waitQueue){
            if (player.getUsername().equalsIgnoreCase(username)) {
                return true;
            }
        }
        for (Game game: activeGames){
            for (Player player: game.getPlayers()){
                if (player.getUsername().equalsIgnoreCase(username)){
                    return true;
                }
            }
        }
        return false;
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

    private static Player getPlayerFromQueue(SocketChannel clientSocketChannel) {
        for (Player player : waitQueue) {
            if (player.getSocketChannel() == clientSocketChannel) {
                return player;
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
            message += MESSAGE_TERMINATOR;
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

    public static List<Integer> findEligibleOpponents(Player player, List<Player> Players){
        List<Integer> eligibleOpponents = new ArrayList<>();
        for(int i = 0; i < Players.size(); i++){
            if(abs(Players.get(i).getScore() - player.getScore()) <= (ratio * player.getWaitingTime())){
                eligibleOpponents.add(i);
            }
        }
        return eligibleOpponents;
    }

    public static boolean readFileToLookupPlayer(String username, String password, SocketChannel clientSocketChannel) {
        // Acquire the lock for reading
        lock.lock();
        String csvFile = "users.csv";
        boolean loginSuccessful = false;
        try (BufferedReader reader = new BufferedReader(new FileReader(csvFile))) {
            String line;
            boolean headerLine = true;
            while ((line = reader.readLine()) != null) {
                if(headerLine){
                    headerLine = false;
                    continue;
                }
                String[] fields = line.split(",");
                String csvUsername = fields[0];
                String csvPassword = fields[1];
                int csvScore = Integer.parseInt(fields[2]);
                int csvGamesPlayed = Integer.parseInt(fields[3]);

                if (csvUsername.equals(username)) {
                    if (Helper.verifyPassword(password, csvPassword)) {
                        sendMessage(clientSocketChannel, MessageType.INFO.toHeader() + "Successfully logged in.");
                        sendMessage(clientSocketChannel, MessageType.AUTHENTICATION_SUCCESSFUL.toHeader());

                        Player player = getUnauthenticatedPlayer(clientSocketChannel);
                        assert player != null;
                        player.setUsername(username);
                        player.setScore(csvScore);
                        player.setGamesPlayed(csvGamesPlayed);
                        String newSessionToken = Helper.generateSessionToken();
                        player.setSessionToken(newSessionToken);
                        sendMessageToPlayer(player, MessageType.INFO.toHeader() + "Here is your session token: " + newSessionToken
                                + "\nPlease use this token to reconnect to the server.");

                        waitQueue.add(player);
                        player.startWaitTimer();
                        unauthenticatedPlayers.remove(player);
                        loginSuccessful = true;

                    }
                    else {
                        // Password incorrect
                        sendMessage(clientSocketChannel, MessageType.INFO.toHeader() + "Incorrect password. Please try again.");
                        sendMessage(clientSocketChannel, MessageType.AUTHENTICATION_FAILURE.toHeader());
                        loginSuccessful = false;
                    }
                }
            }        }
        catch (IOException e) {
            System.err.println("Error reading CSV file: " + e.getMessage());
            sendMessage(clientSocketChannel, MessageType.AUTHENTICATION_FAILURE.toHeader());
            loginSuccessful = false;
        } finally {
            lock.unlock();
        }
        return loginSuccessful;
    }

    public static void writeNewPlayerToFile(String username, String password, SocketChannel clientSocketChannel) {
        // Acquire the lock for writing
        lock.lock();
        String csvFile = "users.csv";
        try (FileWriter writer = new FileWriter(csvFile, true)) {
            String hashedPassword = Helper.hashPassword(password);
            String newSessionToken = Helper.generateSessionToken();

            String newLine = username + "," + hashedPassword + "," + 0 + "," + 0 + "\n";
            writer.write(newLine);

            Player player = getUnauthenticatedPlayer(clientSocketChannel);
            assert player != null;
            player.setUsername(username);
            player.setScore(0);
            player.setGamesPlayed(0);
            player.setSessionToken(newSessionToken);
            waitQueue.add(player);
            player.startWaitTimer();
            unauthenticatedPlayers.remove(player);

            sendMessage(clientSocketChannel, MessageType.INFO.toHeader() + "Successfully registered as a new user.");
            sendMessage(clientSocketChannel, MessageType.AUTHENTICATION_SUCCESSFUL.toHeader());
        } catch (IOException e) {
            System.err.println("Error writing to CSV file: " + e.getMessage());
            sendMessage(clientSocketChannel, MessageType.AUTHENTICATION_FAILURE.toHeader());
        } finally {
            // Release the lock after writing
            lock.unlock();
        }
    }

    //function that updates the file, uses the lock. the function receives a player and updates the file with the new score and games played

    public static void updatePlayerEntry(Player player){
        if (!player.getUpdated()){
            return;
        }
        player.setUpdated(false);
        lock.lock();
        String csvFile = "users.csv";
        try (BufferedReader reader = new BufferedReader(new FileReader(csvFile))) {
            String line;
            boolean headerLine = true;
            while ((line = reader.readLine()) != null) {
                if (headerLine){
                    headerLine = false;
                    continue;
                }
                String[] fields = line.split(",");
                String csvUsername = fields[0];
                String csvPassword = fields[1];

                if (csvUsername.equals(player.getUsername())) {
                    String newLine = csvUsername + "," + csvPassword + "," + player.getScore() + "," + player.getGamesPlayed();
                    Path path = Path.of(csvFile);
                    String fileContent = Files.readString(path);
                    fileContent = fileContent.replace(line, newLine);
                    Files.writeString(path, fileContent);
                    break;
                }
            }
        }
        catch (IOException e) {
            System.err.println("Error reading CSV file: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    //function that creates a set of all of the players that are in the waitQueue or are in the games and that have the player.wasUpdated() flag set to true
    //the function then updates the file with the new score and games played for each player in the set

    public static void updateAllPlayers(){
        Set<Player> playersToUpdate = new HashSet<>();
        for(Player player : waitQueue){
            if(player.getUpdated()){
                playersToUpdate.add(player);
            }
        }
        for(Game game : activeGames){
            for (Player player : game.getPlayers()){
                if(player.getUpdated()){
                    playersToUpdate.add(player);
                }
            }
        }
        for(Player player : playersToUpdate){
            updatePlayerEntry(player);
        }
    }
}