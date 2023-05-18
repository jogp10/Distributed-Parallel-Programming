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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static main.utils.Helper.MESSAGE_TERMINATOR;

import static java.lang.Math.abs;

import static main.utils.MessageType.*;

import static main.utils.Helper.findFirst;



public class Server {
    private static final int PORT = 12345;
    private static final int MAX_PLAYERS = 2;
    private static final int MAX_GAMES = 10;
    private static final int BUFFER_SIZE = 1024;

    private static int playerCount = 0;
    private static List<Player> normalQueue = new ArrayList<>();
    private static List<Player> rankedQueue = new ArrayList<>();
    private static List<Player> unauthenticatedPlayers = new ArrayList<>();
    private static List<Game> activeGames = new ArrayList<>();
    private static Lock gamesLock = new ReentrantLock();
    private static final ExecutorService threadPool = Executors.newFixedThreadPool(MAX_GAMES);
    private static final List<ExecutorService> threadPoolPlayers = new ArrayList<>(Collections.nCopies(MAX_GAMES, Executors.newFixedThreadPool(MAX_PLAYERS)));
    private static final List<Boolean> threadPoolPlayersAvailability = new ArrayList<>(Collections.nCopies(MAX_GAMES, true));
    private static int gameCount = 0;
    private static double ratio = 10;

    //todo change name
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
                if (rankedQueue.size() >= MAX_PLAYERS) {

                    List<List<Integer>> eligibleGame  = new ArrayList<>();
                    //matchmaking
                    Iterator<Player> iterator = rankedQueue.iterator();
                    while (iterator.hasNext()) {
                        Player player = iterator.next();
                        if (player.getAbsent()) {
                            iterator.remove();
                            //todo change, if player is absent, must be in a queue
                            removePlayerNotInQueue(player); //maybe this is not even needed
                            continue;
                        }
                        List<Integer> eligPlayers = findEligibleOpponents(player, rankedQueue);
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
                        int numPlayers = 0;
                        for (int i = intersect.size() - 1; i >= 0; i--) {
                            System.out.println("Removing player " + intersect.get(i) + " from the normal queue");
                            Player player = rankedQueue.remove(i);
                            players.add(player);
                            player.notifyGameStart();
                            numPlayers++;
                            if (numPlayers >= MAX_PLAYERS) {
                                break;
                            }
                        }

                        startGame(players);
                    }
                }
                //todo take absent players into account
                if (normalQueue.size() >= MAX_PLAYERS && activeGames.size() < MAX_GAMES) {
                    System.out.println("Creating a new game with players: ");
                    normalQueue.subList(0, MAX_PLAYERS).forEach(e -> System.out.print(e.getUsername() + " "));
                    System.out.println();

                    List<Player> players = new ArrayList<>();
                    for (int i = 0; i < MAX_PLAYERS; i++) {
                        Player player = normalQueue.remove(0);
                        players.add(player);
                        player.stopWaitTimer();
                        player.setInGame(true);
                    }

                    startGame(players);
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
                    try {
                        bytesRead = clientSocketChannel.read(buffer);
                    }
                    catch (SocketException e) {
                        // TODO: Remover completamente se estiver num jogo, suspenso se em queue
                        // Exceção não ocorreu ( quando ligação é terminada, bytesRead = -1 )
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
                        else {
                            System.err.println("Must have been the wind...");
                            // todo do not leave this here
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
                    } else {
                        // TODO
                    }
                }
            }
        }
    }

    private static void startGame(List<Player> players) {
        Game game = new Game(getAndIncrementGameCount(), players, getAvailableThreadPoolPlayer());
        gamesLock.lock();
        activeGames.add(game);
        gamesLock.unlock();

        Future<?> future = threadPool.submit(game);

        try {
            future.get();
            System.out.println("Game" + game.getId() + " finished");
            endGame(game);
        } catch (InterruptedException e) {
            // Handle interruption
            e.printStackTrace();
        } catch (ExecutionException e) {
            // Handle exception thrown by the task
            e.printStackTrace();
        }
    }

    private static ExecutorService getAvailableThreadPoolPlayer() {
        int availableThreadPoolIndex = -1;
        synchronized (threadPoolPlayersAvailability) {
            availableThreadPoolIndex = findFirst(threadPoolPlayersAvailability,  true);
            if (availableThreadPoolIndex == -1) {
                System.out.println("No available thread pool");
                return null;
            }
            threadPoolPlayersAvailability.set(availableThreadPoolIndex, false);
        }
        return threadPoolPlayers.get(availableThreadPoolIndex);
    }


    public static void handleMessage(SocketChannel clientSocketChannel, String message) {
        
        switch (Helper.parseMessageType(message)) {
            case GAME_GUESS -> handleGuessMessage(clientSocketChannel, Helper.parseMessage(message));
            case AUTHENTICATION_ATTEMPT -> handleAuthentication(clientSocketChannel, Helper.parseMessage(message));
            case AUTHENTICATION_ATTEMPT_TOKEN -> handleAuthenticationToken(clientSocketChannel, Helper.parseMessage(message));
            case GAME_MODE_RESPONSE -> handleGameModeResponse(clientSocketChannel, Helper.parseMessage(message));
            case DEFAULT ->
                // Handle invalid message format or unsupported type
                    System.err.println("Invalid message format or unsupported type: " + message);
        }


    }

    private static void handleGameModeResponse(SocketChannel clientSocketChannel, String parseMessage) {
        if(parseMessage.equals("1")) {
            Player player = getUnauthenticatedPlayer(clientSocketChannel);
            unauthenticatedPlayers.remove(player);
            if(player != null) {
                normalQueue.add(player);
                sendMessageToPlayer(player, MessageType.GAME_MODE_RESPONSE.toHeader() + "You have selected simple mode.");
            }
        }
        else if(parseMessage.equals("2")) {
            Player player = getUnauthenticatedPlayer(clientSocketChannel);
            unauthenticatedPlayers.remove(player);
            if(player != null) {
                rankedQueue.add(player);
                sendMessageToPlayer(player, MessageType.GAME_MODE_RESPONSE.toHeader() + "You have selected ranked mode.");
            }
        }
        else {
            sendMessage(clientSocketChannel, MessageType.GAME_MODE_REQUEST.toHeader() + "Invalid game mode.");
        }

    }

    private static void handleAuthenticationToken(SocketChannel clientSocketChannel, String parseMessage) {
        String[] tokens = parseMessage.split(";");
        String sessionToken = tokens[0];

        //iterate waitQueue and find the player with the sessionToken
        for(Player player : normalQueue) {
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
        for(Player player : rankedQueue) {
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

    public static void handleGuessMessage(SocketChannel clientSocketChannel, String message) {
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
            if (guess < game.getMinRange() || guess > game.getMaxRange()) {
                sendMessageToPlayer(player, "Guess out of range, try again between " + game.getMinRange() + " and " + game.getMaxRange());
                return;
            }
        }
        catch (NumberFormatException e) {
            sendMessageToPlayer(player, "Invalid guess");
            return;
        }

        notifyGuessReceived(game, player, guess);
    }

    private static void notifyGuessReceived(Game game, Player player, int guess) {
        game.setPlayerGuess(player, guess);
        game.signalGuessReceived();
    }

    private static void endGame(Game game) {
        gamesLock.lock();
        activeGames.remove(game);
        int threadPoolIndex = threadPoolPlayers.indexOf(game.getThreadPoolPlayers());
        threadPoolPlayersAvailability.set(threadPoolIndex, true);
        gamesLock.unlock();

        for (Player p : game.getPlayers()) {
            p.notifyGameOver();
            normalQueue.add(p);
            p.startWaitTimer();
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
                                + "\n\tPlease use this token to reconnect to the server.");


                        player.startWaitTimer();

                        // Choose matchmaking
                        sendMessageToPlayer(player, MessageType.GAME_MODE_REQUEST.toHeader() + "Please choose a matchmaking option: \n" +
                                "1. Normal\n" +
                                "2. Ranked\n"
                        );


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


            sendMessage(clientSocketChannel, MessageType.INFO.toHeader() + "Successfully registered as a new user.");
            sendMessage(clientSocketChannel, MessageType.AUTHENTICATION_SUCCESSFUL.toHeader());
            player.startWaitTimer();

            // Choose matchmaking
            sendMessageToPlayer(player, MessageType.GAME_MODE_REQUEST.toHeader() + "Please choose a matchmaking option: \n" +
                    "1. Normal\n" +
                    "2. Ranked\n"
            );

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
            // remove from normal queue or ranked queue
            normalQueue.remove(player);
            rankedQueue.remove(player);
            Game game = getGame(player);
            if (game != null) {
                game.removePlayer(player);
                sendMessageToPlayers(game, "Player " + player.getUsername() + " has left the game");
                if (game.getPlayers().isEmpty()) {
                    gamesLock.lock();
                    activeGames.remove(game);
                    gamesLock.unlock();
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
        for (Player player: normalQueue){
            if (player.getUsername().equalsIgnoreCase(username)) {
                return true;
            }
        }
        for(Player player: rankedQueue){
            if (player.getUsername().equalsIgnoreCase(username)){
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
        for (Player player : normalQueue) {
            if (player.getSocketChannel() == clientSocketChannel) {
                return player;
            }
        }
        for (Player player : rankedQueue) {
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
        for (Player player : normalQueue) {
            if (player.getSocketChannel() == clientSocketChannel) {
                return player;
            }
        }
        for (Player player : rankedQueue) {
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

    public static Game getGame(Player player) {
        for (Game game : activeGames) {
            if (game.getPlayers().contains(player)) {
                return game;
            }
        }
        return null;
    }

    public static void sendMessageToPlayers(Game game, String message) {
        System.out.println("Sending message to players: " + message);
        for (Player player : game.getPlayers()) {
            sendMessage(player.getSocketChannel(), message);
        }
    }

    public static void sendMessage(SocketChannel clientSocketChannel, String message) {
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

    public static void sendMessageToPlayer(Player player, String message) {
        System.out.println("Sending message to player " + player.getUsername() + ": " + message);
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