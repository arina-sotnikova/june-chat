package ru.otus.sotnikova.june.chat.server.authenticator;

import ru.otus.sotnikova.june.chat.server.ClientHandler;

public interface AuthenticationProvider {
    void initialize();
    boolean authenticate(ClientHandler clientHandler, String login, String password);
    boolean registration(ClientHandler clientHandler, String login, String password, String username);
    boolean privilegeElevation(ClientHandler clientHandler);
    boolean isBanned(String usernameToCheck);
    void ban(String usernameToBan);
    void changeNick(ClientHandler clientHandler, String newUsername);
}
