package main.client;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;

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
        System.out.println("Connected to server");
        while (!message.equals("exit")) {
            buffer.clear();
            int bytesRead = socketChannel.read(buffer);
            if (bytesRead == -1) {
                System.out.println("Disconnected from server");
                break;
            } else if (bytesRead > 0) {
                buffer.flip();
                message = new String(buffer.array(), 0, bytesRead);
                System.out.println(message);
            }

            if (message.equals("Game started! Guess a number between 1 and 100")) {
                System.out.print("Enter your guess: ");
                message = scanner.nextLine();
                buffer.clear();
                buffer.put(message.getBytes());
                buffer.flip();
                socketChannel.write(buffer);
            }
        }

        socketChannel.close();
    }
}

