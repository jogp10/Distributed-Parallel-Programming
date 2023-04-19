package main.utils;

public class Helper {

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

}
