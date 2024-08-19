package ru.otus.june.chat.server;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Server {
    private int port;
    private List<ClientHandler> clients;
    private AuthenticationProvider authenticationProvider;

    public AuthenticationProvider getAuthenticationProvider() {
        return authenticationProvider;
    }

    public Server(int port) {
        this.port = port;
        this.clients = new ArrayList<>();
        this.authenticationProvider = new InMemoryAuthenticationProvider(this);
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Сервер запущен на порту: " + port);
            authenticationProvider.initialize();
            while (true) {
                Socket socket = serverSocket.accept();
                new ClientHandler(this, socket);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
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
}
