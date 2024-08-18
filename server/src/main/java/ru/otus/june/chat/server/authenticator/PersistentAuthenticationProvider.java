package ru.otus.june.chat.server.authenticator;

import ru.otus.june.chat.server.ClientHandler;
import ru.otus.june.chat.server.Server;
import ru.otus.june.chat.server.UserRole;

import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.util.Properties;

public class PersistentAuthenticationProvider implements AuthenticationProvider {
    private Server server;
    private Connection conn;
    PreparedStatement createUser;
    PreparedStatement getUser;
    PreparedStatement banUser;
    PreparedStatement updateNick;

    public PersistentAuthenticationProvider(Server server) {
        this.server = server;
        try {
            Class.forName("org.postgresql.Driver");
            InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream("db.properties");
            Properties dbProps = new Properties();
            dbProps.load(inputStream);
            conn = DriverManager
                    .getConnection(
                            String.format("jdbc:postgresql://%s/%s", dbProps.getProperty("host"), dbProps.getProperty("database")),
                            dbProps.getProperty("user"), dbProps.getProperty("password")
                    );
            createUser = conn.prepareStatement("INSERT INTO users (login, passwd, username, role, is_banned) VALUES (?, ?, ?, ?, ?)");
            getUser = conn.prepareStatement("SELECT login, passwd, username, role, is_banned FROM users WHERE login = ?");
            banUser = conn.prepareStatement("UPDATE users SET is_banned = ? WHERE username = ?");
            updateNick = conn.prepareStatement("UPDATE users SET username = ? WHERE username = ?");

        } catch (IOException | ClassNotFoundException | SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void initialize() {
        System.out.println("Сервис аутентификации запущен: Persistent режим");
    }

    @Override
    public synchronized boolean authenticate(ClientHandler clientHandler, String login, String password) {
        boolean isAuthenticated = false;
        String username = "";
        try {
            getUser.setString(1, login);
            try (ResultSet userData = getUser.executeQuery()) {
                if (userData.next()) {
                    String dbLogin = userData.getString("login");
                    String dbPasswdHash = userData.getString("passwd");
                    boolean isBanned = userData.getBoolean("is_banned");
                    username = userData.getString("username");
                    isAuthenticated = dbLogin.equals(login) && dbPasswdHash.equals(password) && !isBanned;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        if (isAuthenticated) {
            clientHandler.setUsername(username);
            server.subscribe(clientHandler);
            clientHandler.sendMessage("/authok " + username);
        }
        return isAuthenticated;
    }

    @Override
    public boolean registration(ClientHandler clientHandler, String login, String password, String username) {
        boolean successfulOperation = false;
        try {
            createUser.setString(1, login);
            createUser.setString(2, password);
            createUser.setString(3, username);
            createUser.setInt(4, UserRole.USER.ordinal());
            createUser.setBoolean(5, false);
            createUser.execute();
            successfulOperation = true;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        if (successfulOperation) {
            clientHandler.setUsername(username);
            server.subscribe(clientHandler);
            clientHandler.sendMessage("/regok " + username);
        }
        return successfulOperation;
    }

    @Override
    public boolean privilegeElevation(ClientHandler clientHandler) {
        boolean elevated = false;
        try {
            try (ResultSet userData = getUser.executeQuery()) {
                if (userData.next()) {
                    int userRole = userData.getInt("role");
                    elevated = userRole == UserRole.ADMIN.ordinal();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return elevated;
    }

    @Override
    public boolean isBanned(String usernameToCheck) {
        boolean isUserBanned = true;
        try {
            try (ResultSet userData = getUser.executeQuery()) {
                if (userData.next()) {
                    isUserBanned = userData.getBoolean("is_banned");
                }
            }
        }
        catch (SQLException e) {
            e.printStackTrace();
        }
        return isUserBanned;
    }

    @Override
    public void ban(String usernameToBan) {
        try {
            banUser.setBoolean(1, true);
            banUser.setString(2, usernameToBan);
            banUser.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void changeNick(ClientHandler clientHandler, String newUsername) {
        try {
            updateNick.setString(1, newUsername);
            updateNick.setString(2, clientHandler.getUsername());
            updateNick.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
