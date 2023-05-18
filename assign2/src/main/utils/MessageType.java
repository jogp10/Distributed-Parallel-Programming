package main.utils;

public enum MessageType {
    //add types: AUTHENTICATION, GAME_GUESS, DISCONNECT, KEEP_ALIVE.
    AUTHENTICATION_REQUEST, //server to client
    AUTHENTICATION_SUCCESSFUL, // server to client
    AUTHENTICATION_FAILURE, // server to client
    AUTHENTICATION_ATTEMPT, //client to server
    AUTHENTICATION_ATTEMPT_TOKEN, //client to server

    GAME_GUESS,
    DISCONNECT,
    KEEP_ALIVE,

    INFO, //server to client, should be printed to the user
    GAME_MODE_REQUEST,
    GAME_MODE_RESPONSE,
    GAME_GUESS_REQUEST,

    DEFAULT;
    //...


    public String toHeader() {
        return "<" + name() + ">";
    }

}
