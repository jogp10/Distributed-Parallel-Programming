package main.utils;


import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Helper {

    public static final char MESSAGE_TERMINATOR = '\t';

    //function that given a mesage that starts with "<type>" returns the type

    public static MessageType parseMessageType(String message) {

        try{
            String type = message.substring(1, message.indexOf(">"));
            return MessageType.valueOf(type);
        }
        catch (IndexOutOfBoundsException | IllegalArgumentException e) {
            // Handle invalid message format or unsupported type
//            System.err.println("Invalid message format or unsupported type: " + message);
            return MessageType.DEFAULT;
        }
    }

    //extract the message from the message
    public static String parseMessage(String message) {
        return message.substring(message.indexOf(">") + 1);
    }


    public static String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashedBytes = md.digest(password.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hashedBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // Handle error
            System.err.println("Error hashing password: " + e.getMessage());
            return null;
        }
    }

    public static boolean verifyPassword(String password, String hashedPassword) {
        String hashedInput = hashPassword(password);
        return hashedInput != null && hashedInput.equals(hashedPassword);
    }


    public static String generateSessionToken() {
        return hashPassword(String.valueOf(System.currentTimeMillis()));
    }
}
