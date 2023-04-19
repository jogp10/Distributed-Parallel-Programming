package main.utils;

public enum MessageType {
    //add types: AUTHENTICATION, GAME_GUESS, DISCONNECT, KEEP_ALIVE.
    AUTHENTICATION_REQUEST, //server to client
    AUTHENTICATION_SUCCESSFUL, // server to client
    AUTHENTICATION_FAILURE, // server to client
    AUTHENTICATION_ATTEMPT, //client to server
    GAME_GUESS,
    DISCONNECT,
    KEEP_ALIVE,
    DEFAULT;
    //...


    public String toHeader() {
        return "<" + name() + ">";
    }

}
