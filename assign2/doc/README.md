Compile the files:
javac main.server.Server.java
javac main.client.Client.java
javac main.game.Player.java


Run the server:
java main.server.Server

Run multiple clients in separate terminal windows:
java main.client.Client
Type quit to disconnect from the server when asked for a game mode.

When a player connects to the server, they are prompted to login.
Trying to login with a username that is not registered will automatically register that username and login.
The user can also login with a token to recover their previous place in the queue. For that they must have entered either the Normal or Ranked queue.

Here are some login examples:

| Username | Password |
|----------|----------|
| a    | a    |
| b    | b    |
| c    | c    |
| d    | d    |
| abcd    | 1234    |
| lp   | lp    |
| ric  | ric    |
| 111 | 111    |


The game consists in 3 rounds of guessing a number between 1 and 100.
If playing ranked mode, the player score is updated according to the distance between the guess and the secret number.
Within 50 points of the secret number, the player gets (50 - distance) in points.
Outside that range, the player loses (distance - 50) points.

The constants MAX_GAMES and MAX_PLAYERS can be changed in the Server class to change the maximum number of games that can happen at the same time and the number of players required to start a game, respectively.

The constant MAX_ROUNDS can be changed in the Game class to change the number of rounds in a game.
