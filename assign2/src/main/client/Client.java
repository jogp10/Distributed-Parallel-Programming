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

    public static void main(String[] args) throws IOException {
        SocketChannel socketChannel = SocketChannel.open(new InetSocketAddress(SERVER_IP, SERVER_PORT));
        socketChannel.configureBlocking(false);

        Scanner scanner = new Scanner(System.in);
        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
        String message = "";
        String receivedMessage = "";
        System.out.println("Connected to server");

        //show an authentication sequence
        receivedMessage = receiveServerMessage(socketChannel, buffer);
        while (Helper.parseMessageType(receivedMessage) != MessageType.AUTHENTICATION_SUCCESSFUL) {
            if(Helper.parseMessageType(receivedMessage) == MessageType.INFO){
                receivedMessage = receiveServerMessage(socketChannel, buffer);
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
            receivedMessage = receiveServerMessage(socketChannel, buffer);
        }

        while (!receivedMessage.equals("exit")) {

            message = "";
            receivedMessage = receiveServerMessage(socketChannel, buffer);


            switch (Helper.parseMessageType(receivedMessage)){
                case GAME_MODE_REQUEST:
                    System.out.print("Enter your game mode: ");
                    message = GAME_MODE_RESPONSE.toHeader();
                    message += scanner.nextLine();
                    break;
                case GAME_GUESS_REQUEST:
                    System.out.print("Enter your guess: ");
                    message = MessageType.GAME_GUESS.toHeader();
                    message += scanner.nextLine();
                    break;

            }

            sendMessageToServer(socketChannel, buffer, message);

        }

        socketChannel.close();
    }

    private static void sendMessageToServer(SocketChannel socketChannel, ByteBuffer buffer, String message) throws IOException {
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
                if (buffer.hasArray() && buffer.array()[buffer.position() - 1] == MESSAGE_TERMINATOR) {
                    break;
                }

                // clear the buffer to read more data
                buffer.clear();
            }
        }

        receivedMessage = receivedMessage.substring(0, receivedMessage.length() - 1).trim();
        if(!receivedMessage.equals(""))System.out.println(":-->" + receivedMessage);
        if(Helper.parseMessageType(receivedMessage) == MessageType.INFO){
            System.out.println('\n' + Helper.parseMessage(receivedMessage) + '\n');
        }
        return receivedMessage;
    }
}

