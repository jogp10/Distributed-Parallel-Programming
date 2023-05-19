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

import static main.utils.Helper.MESSAGE_TERMINATOR;

import static java.lang.Math.abs;

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
    private static final Lock databaseLock = new ReentrantLock();


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
                            iterator.remove(); // this will remove the player from the queue
                            removeAbsentPlayer(player);
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
                    Iterator<Player> iterator = normalQueue.iterator();
                    int playersAdded = 0;
                    while (iterator.hasNext() && playersAdded < MAX_PLAYERS) {
                        Player player = iterator.next();
                        iterator.remove(); // this will remove the player from the queue

                        if (player.getAbsent()) {
                            removeAbsentPlayer(player);
                            continue;
                        }
                        players.add(player);
                        player.stopWaitTimer();
                        player.setInGame(true);
                        playersAdded++;
                    }

                    if (playersAdded == MAX_PLAYERS) {
                        startGame(players);
                    }
                }
                else {
                    //todo delete
//                    System.out.println("Not enough players in the queue");
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
                            String username = player.getUsername();
                            updatePlayerEntry(player);
                            if (player.isInQueue()) {
                                suspendPlayer(player);
                            }
                            else {
                                removePlayerNotInQueue(player);
                            }
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
                        String message = "";
                        do {
                            // attempt to read data from the socket channel
                            // if no bytes were read, the connection has been lost
                            if (bytesRead == -1) {
                                throw new IOException("Socket channel connection lost.");
                            }

                            if (buffer.position() > 0) {
                                // convert the buffer to a string and append it to the message
                                message += new String(buffer.array(), 0, bytesRead);
                                // if the message end character is found, exit the loop
                                if (buffer.hasArray() && buffer.array()[buffer.position() - 1] == MESSAGE_TERMINATOR) {
                                    break;
                                }
                                // clear the buffer to read more data
                                buffer.clear();
                            }
                            bytesRead = clientSocketChannel.read(buffer);
                        } while (true);
                        handleMessage(clientSocketChannel, message);
                    } else {
                        // TODO
                        // ??
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


        // todo check if this is not stupid
        // Continue executing the thread without waiting for the task to complete
        new Thread(() -> {
            try {
                Future<?> future = threadPool.submit(game);
                future.get();
                System.out.println("Game " + game.getId() + " finished");
                endGame(game);
            } catch (InterruptedException e) {
                // Handle interruption
                e.printStackTrace();
            } catch (ExecutionException e) {
                // Handle exception thrown by the task
                e.printStackTrace();
            }
        }).start();
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
        message = message.substring(0, message.length() - 1).trim(); // Remove message terminator
        
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
                sendMessageToPlayer(player, MessageType.GAME_MODE_RESPONSE.toHeader() + "You have selected simple mode.\n" +
                        "Waiting for other players to join...\n" +
                        "Players in queue: " + normalQueue.size() + "\n");
                player.startWaitTimer();
            }
        }
        else if(parseMessage.equals("2")) {
            Player player = getUnauthenticatedPlayer(clientSocketChannel);
            unauthenticatedPlayers.remove(player);
            if(player != null) {
                rankedQueue.add(player);
                sendMessageToPlayer(player, MessageType.GAME_MODE_RESPONSE.toHeader() + "You have selected ranked mode.\n" +
                        "Waiting for other players to join...\n" +
                        "Players in queue: " + rankedQueue.size() + "\n");
                player.startWaitTimer();
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
                sendMessageToPlayer(player, MessageType.INFO.toHeader() + "Guess out of range, try again between " + game.getMinRange() + " and " + game.getMaxRange());
                sendMessageToPlayer(player, MessageType.GAME_GUESS_REQUEST.toHeader() + "Please enter a valid guess");
                return;
            }
        }
        catch (NumberFormatException e) {
            sendMessageToPlayer(player, MessageType.INFO.toHeader() +"Invalid guess");
            sendMessageToPlayer(player, MessageType.GAME_GUESS_REQUEST.toHeader() + "Please enter a valid guess");
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
            //normalQueue.add(p);
            //p.startWaitTimer();
            sendMessageToPlayer(p,MessageType.GAME_END.toHeader() + "Game Ended\n");
            unauthenticatedPlayers.add(p);
            sendMessageToPlayer(p, MessageType.GAME_MODE_REQUEST.toHeader() +
                    "Please choose a matchmaking option: \n" +
                    "1. Normal\n" +
                    "2. Ranked\n"
            );
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
                sendMessageToPlayers(game, MessageType.INFO.toHeader() + "Player " + player.getUsername() + " has left the game");
                if (game.getPlayers().isEmpty()) {
                    gamesLock.lock();
                    activeGames.remove(game);
                    gamesLock.unlock();
                }
            }
            player.setInGame(false);
            player = null;
        }
    }

    private static void removePlayerNotInQueue(Player player){
        if (player != null) {
            Game game = getGame(player);
            if (game != null) {
                game.removePlayer(player);
                sendMessageToPlayers(game, MessageType.INFO.toHeader() + "Player " + player.getUsername() + " has left the game");
                if (game.getPlayers().isEmpty()) {
                    activeGames.remove(game);
                }
            }
            player.setInGame(false);
        }
    }

    private static void removeAbsentPlayer(Player player){
        if (player != null) {
            player.setInGame(false);
            player.setInQueue(false);
            player = null;
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
        databaseLock.lock();
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
                                + "\n\tPlease use this token to reconnect to the server.");


                        player.startWaitTimer();

                        // Choose matchmaking
                        sendMessageToPlayer(player, MessageType.GAME_MODE_REQUEST.toHeader() + "Please choose a matchmaking option: \n" +
                                "1. Normal\n" +
                                "2. Ranked\n"
                        );
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
            databaseLock.unlock();
        }
        return loginSuccessful;
    }

    public static void writeNewPlayerToFile(String username, String password, SocketChannel clientSocketChannel) {
        // Acquire the lock for writing
        databaseLock.lock();
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
        } finally {
            // Release the lock after writing
            databaseLock.unlock();
        }
    }

    //function that updates the file, uses the lock. the function receives a player and updates the file with the new score and games played

    public static void updatePlayerEntry(Player player){
        if (!player.getUpdated()){
            return;
        }
        player.setUpdated(false);
        databaseLock.lock();
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
            databaseLock.unlock();
        }
    }

    //function that creates a set of all of the players that are in the waitQueue or are in the games and that have the player.wasUpdated() flag set to true
    //the function then updates the file with the new score and games played for each player in the set

    public static void updateAllPlayers(){
        Set<Player> playersToUpdate = new HashSet<>();
        for(Player player : normalQueue){
            if(player.getUpdated()){
                playersToUpdate.add(player);
            }
        }
        for(Player player : rankedQueue){
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