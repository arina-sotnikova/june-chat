package ru.otus.sotnikova.june.chat.server;

import ru.otus.sotnikova.june.chat.server.authenticator.AuthenticationProvider;
import ru.otus.sotnikova.june.chat.server.authenticator.PersistentAuthenticationProvider;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class Server {
    private int port;
    private List<ClientHandler> clients;
    private AuthenticationProvider authenticationProvider;
    private boolean receivedShutdown = false;

    public AuthenticationProvider getAuthenticationProvider() {
        return authenticationProvider;
    }

    public Server(int port) {
        this.port = port;
        this.clients = new ArrayList<>();
        //this.authenticationProvider = new InMemoryAuthenticationProvider(this);
        this.authenticationProvider = new PersistentAuthenticationProvider(this);
    }

    public List<String> getUserNames() {
        return this.clients.stream().map(ClientHandler::getUsername).collect(Collectors.toList());
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Сервер запущен на порту: " + port);
            serverSocket.setSoTimeout(1_200_000);
            authenticationProvider.initialize();
            while (!receivedShutdown) {
                Socket socket = serverSocket.accept();
                socket.setSoTimeout(1_200_000);
                ClientHandler handler = new ClientHandler(this, socket);
                handler.handle();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized void disconnectAndBan(String userName) {
        this.kickUser(userName);
        authenticationProvider.ban(userName);
    }

    public synchronized void shutdown() {
        broadcastMessage("Сервер будет остановлен");
        new ArrayList<>(clients).forEach(ClientHandler::disconnect);
        this.receivedShutdown = true;
    }

    public synchronized void subscribe(ClientHandler clientHandler) {
        broadcastMessage("В чат зашел: " + clientHandler.getUsername());
        clients.add(clientHandler);
    }

    public synchronized void unsubscribe(ClientHandler clientHandler) {
        clients.remove(clientHandler);
        broadcastMessage("Из чата вышел: " + clientHandler.getUsername());
    }

    public synchronized void kickUser(String userToKick) {
        Optional<ClientHandler> clientToDisconnect = clients.stream()
                .filter(clientHandler -> clientHandler.getUsername().equals(userToKick))
                .findAny();
        clientToDisconnect.ifPresent(client -> {
            client.disconnect();
            broadcastMessage("Пользователь " + userToKick + " удален из чата администратором");
        });
    }

    public synchronized void broadcastMessage(String message) {
        for (ClientHandler c : clients) {
            c.sendMessage(message);
        }
    }

    public synchronized  void sendDirectMessage(String userName, String message) {
        clients.stream()
                .filter(handler -> handler.getUsername().equals(userName))
                .forEach(handler -> handler.sendMessage(message));
    }

    public boolean isUsernameBusy(String username) {
        for (ClientHandler c : clients) {
            if (c.getUsername().equals(username)) {
                return true;
            }
        }
        return false;
    }

    public void changeNickIfValid(ClientHandler clientHandler, String newUserName) {
        if (isUsernameBusy(newUserName))
            sendDirectMessage(clientHandler.getUsername(), "Имя пользователя занято");
        else {
            authenticationProvider.changeNick(clientHandler, newUserName);
            clientHandler.setUsername(newUserName);
        }
    }
}
