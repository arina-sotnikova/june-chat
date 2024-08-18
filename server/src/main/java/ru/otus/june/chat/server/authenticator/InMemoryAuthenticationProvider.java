package ru.otus.june.chat.server.authenticator;

import ru.otus.june.chat.server.ClientHandler;
import ru.otus.june.chat.server.Server;
import ru.otus.june.chat.server.UserRole;

import java.util.ArrayList;
import java.util.List;

public class InMemoryAuthenticationProvider implements AuthenticationProvider {
    private final Server server;
    private final List<User> users;
    private final List<String> bannedUsers = new ArrayList<>();

    public InMemoryAuthenticationProvider(Server server) {
        this.server = server;
        this.users = new ArrayList<>();
        this.users.add(new User("cat", "godmode", "admin", UserRole.ADMIN));
    }

    @Override
    public boolean registration(ClientHandler clientHandler, String login, String password, String username) {
        if (login.trim().length() < 3 || password.trim().length() < 6 || username.trim().isEmpty()) {
            clientHandler.sendMessage("Логин 3+ символа, пароль 6+ символов, имя пользователя 1+ символ");
            return false;
        }
        if (isLoginAlreadyExist(login)) {
            clientHandler.sendMessage("Указанный логин уже занят");
            return false;
        }
        if (isUsernameAlreadyExist(username)) {
            clientHandler.sendMessage("Указанное имя пользователя уже занято");
            return false;
        }
        users.add(new User(login, password, username));
        clientHandler.setUsername(username);
        server.subscribe(clientHandler);
        clientHandler.sendMessage("/regok " + username);
        return true;
    }


    @Override
    public void initialize() {
        System.out.println("Сервис аутентификации запущен: In-Memory режим");
    }

    @Override
    public boolean isBanned(String usernameToCheck) {
        return bannedUsers.stream().anyMatch(user -> user.equals(usernameToCheck));
    }

    @Override
    public void ban(String usernameToBan) {
        if (!isBanned(usernameToBan))
            bannedUsers.add(usernameToBan);
        else
            System.out.println("The user is already banned.");
    }

    @Override
    public void changeNick(ClientHandler clientHandler, String newUsername) {
        users.stream()
                .filter(user -> user.username.equals(clientHandler.getUsername()))
                .forEach(user -> user.setUsername(newUsername));
        clientHandler.setUsername(newUsername);
    }

    private String getUsernameByLoginAndPassword(String login, String password) {
        for (User u : users) {
            if (u.login.equals(login) && u.password.equals(password)) {
                return u.username;
            }
        }
        return null;
    }

    private boolean isLoginAlreadyExist(String login) {
        for (User u : users) {
            if (u.login.equals(login)) {
                return true;
            }
        }
        return false;
    }

    private boolean isUsernameAlreadyExist(String username) {
        for (User u : users) {
            if (u.username.equals(username)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public synchronized boolean authenticate(ClientHandler clientHandler, String login, String password) {
        String authUsername = getUsernameByLoginAndPassword(login, password);
        if (authUsername == null) {
            clientHandler.sendMessage("Некорретный логин/пароль");
            return false;
        }
        if (server.isUsernameBusy(authUsername)) {
            clientHandler.sendMessage("Указанная учетная запись уже занята");
            return false;
        }
        if (isBanned(authUsername)) {
            clientHandler.sendMessage("Этот пользователь забанен администратором");
            return false;
        }
        clientHandler.setUsername(authUsername);
        server.subscribe(clientHandler);
        clientHandler.sendMessage("/authok " + authUsername);
        return true;
    }

    public boolean privilegeElevation(ClientHandler clientHandler) {
        return users.stream().anyMatch(user -> clientHandler.getUsername().equals(user.username) && user.role == UserRole.ADMIN);
    }

    private class User {
        private final String login;
        private final String password;
        private String username;
        private UserRole role = UserRole.USER;

        public User(String login, String password, String username) {
            this.login = login;
            this.password = password;
            this.username = username;
        }

        private User(String login, String password, String username, UserRole role) {
            this.login = login;
            this.password = password;
            this.username = username;
            this.role = role;
        }

        public void setUsername(String newUsername) {
            this.username = newUsername;
        }
    }
}