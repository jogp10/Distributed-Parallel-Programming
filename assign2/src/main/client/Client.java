package main.client;
import main.utils.Helper;
import main.utils.MessageType;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;

import static main.utils.Helper.MESSAGE_TERMINATOR;
import static main.utils.MessageType.GAME_MODE_RESPONSE;

public class Client {
    private static final String SERVER_IP = "localhost";
    private static final int SERVER_PORT = 12345;
    private static final int BUFFER_SIZE = 1024;
    private static Queue<StringBuilder> messageQueue = new ArrayDeque<>();



    public static void main(String[] args) throws IOException {
        SocketChannel socketChannel = SocketChannel.open(new InetSocketAddress(SERVER_IP, SERVER_PORT));
        socketChannel.configureBlocking(false);

        Scanner scanner = new Scanner(System.in);
        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
        String message = "";
        String receivedMessage = "";
        System.out.println("Connected to server");

        //show an authentication sequence
        receivedMessage = getNextMessage(socketChannel, buffer);
        while (Helper.parseMessageType(receivedMessage) != MessageType.AUTHENTICATION_SUCCESSFUL) {
            if(Helper.parseMessageType(receivedMessage) == MessageType.INFO){
                receivedMessage = getNextMessage(socketChannel, buffer);
                continue;
            }

            if (Helper.parseMessageType(receivedMessage) == MessageType.AUTHENTICATION_FAILURE) {
                System.out.println("Authentication failed. Please try again.");
            }

//            message = MessageType.AUTHENTICATION_ATTEMPT.toHeader();
            System.out.print("Rejoin queue using token (optional, enter token or leave blank): ");
            message = scanner.nextLine();
//            if message is empty, then the user did not enter a token
//            if message is not empty, use another message type AUTHENTICATION_ATTEMPT_TOKEN
            if (message.equals("")) {
                message = MessageType.AUTHENTICATION_ATTEMPT.toHeader();
                System.out.print("Enter your username: ");
                message += scanner.nextLine();
                message += ";";
                System.out.print("Enter your password: ");
                message += scanner.nextLine();
            } else {
                message = MessageType.AUTHENTICATION_ATTEMPT_TOKEN.toHeader() + message;
            }
            System.out.println(message); //todo remove later
            sendMessageToServer(socketChannel, buffer, message);
            receivedMessage = getNextMessage(socketChannel, buffer);
        }

        while (!receivedMessage.equals("exit")) {

            message = "";
            receivedMessage = getNextMessage(socketChannel, buffer);


            switch (Helper.parseMessageType(receivedMessage)){
                case GAME_MODE_REQUEST:
                    System.out.print("Enter your game mode: ");
                    message = GAME_MODE_RESPONSE.toHeader();
                    message += scanner.nextLine();
                    sendMessageToServer(socketChannel, buffer, message);
                    break;
                case GAME_GUESS_REQUEST:
                    System.out.print("Enter your guess: ");
                    message = MessageType.GAME_GUESS.toHeader();
                    message += scanner.nextLine();
                    sendMessageToServer(socketChannel, buffer, message);
                    break;
                case DISCONNECT:
                    System.out.println("Disconnected from server");
                    socketChannel.close();
                    return;
                default:
                    break;

            }
        }

        socketChannel.close();
    }

    private static String getNextMessage(SocketChannel socketChannel, ByteBuffer buffer) throws IOException {
        String receivedMessage;
        if (messageQueue.isEmpty()) {
            receivedMessage = receiveServerMessage(socketChannel, buffer);
        }
        else {
            StringBuilder queueMessage = messageQueue.peek();
            receivedMessage = queueMessage.append(receiveServerMessage(socketChannel, buffer)).toString();
            messageQueue.poll();
        }
        return receivedMessage;
    }

    private static void sendMessageToServer(SocketChannel socketChannel, ByteBuffer buffer, String message) throws IOException {
        message += MESSAGE_TERMINATOR;
        buffer.clear();
        buffer.put(message.getBytes());
        buffer.flip();
        while (buffer.hasRemaining()) {
            socketChannel.write(buffer);
        }
    }

    private static String receiveServerMessage(SocketChannel socketChannel, ByteBuffer buffer) throws IOException {
        String receivedMessage = "";
        buffer.clear();

        while (true) {
            // attempt to read data from the socket channel
            int bytesRead = socketChannel.read(buffer);

            // if no bytes were read, the connection has been lost
            if (bytesRead == -1) {
                throw new IOException("Socket channel connection lost.");
            }


            if (buffer.position() > 0) {
                // convert the buffer to a string and append it to the message
                receivedMessage += new String(buffer.array(), 0, bytesRead);

                // if the message end character is found, exit the loop
                //it can be found in the middle
                if (buffer.hasArray() && receivedMessage.contains(String.valueOf(MESSAGE_TERMINATOR))) {
                    break;
                }

                // clear the buffer to read more data
                buffer.clear();
            }

            if (bytesRead == 0 && !messageQueue.isEmpty()) {
                break;
            }
        }

        String[] messages = receivedMessage.split("\\t");

        receivedMessage = messages[0];

        int startIndex = 1;

        if (!messageQueue.isEmpty()) {
            //if received;«Message starts wit <, then we put everything in the queue and return ""
            if (receivedMessage.length() > 0 && receivedMessage.charAt(0) == '<') {
                messageQueue.add(new StringBuilder(receivedMessage));
                receivedMessage = "";
                startIndex = 0;
            }
        }

        if (messages.length > 1) {
            for (int i = startIndex; i < messages.length; i++) {
                messageQueue.add(new StringBuilder(messages[i]));
            }
        }

        if(!receivedMessage.equals(""))System.out.println(":-->" + receivedMessage);
        if(Helper.parseMessageType(receivedMessage) == MessageType.INFO){
            System.out.println('\n');
        }
        return receivedMessage;
    }
}

