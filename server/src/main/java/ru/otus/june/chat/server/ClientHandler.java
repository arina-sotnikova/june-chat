package ru.otus.june.chat.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClientHandler {
    private Server server;
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private String username;
    private final Pattern DIRECT_MESSAGE_PATTERN = Pattern.compile("/w\\s(\\w+)\\s(.*)");
    private final Pattern KICK_PATTERN = Pattern.compile("/kick\\s(\\w+)");

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public ClientHandler(Server server, Socket socket) throws IOException {
        this.server = server;
        this.socket = socket;
        this.in = new DataInputStream(socket.getInputStream());
        this.out = new DataOutputStream(socket.getOutputStream());
        new Thread(() -> {
            try {
                System.out.println("Подключился новый клиент");
                while (true) {
                    String message = in.readUTF();
                    if (message.equals("/exit")) {
                        sendMessage("/exitok");
                        return;
                    }
                    if (message.startsWith("/auth ")) {
                        String[] elements = message.split(" ");
                        if (elements.length != 3) {
                            sendMessage("Неверный формат команды /auth");
                            continue;
                        }
                        if (server.getAuthenticationProvider().authenticate(this, elements[1], elements[2])) {
                            break;
                        }
                        continue;
                    }
                    if (message.startsWith("/register ")) {
                        String[] elements = message.split(" ");
                        if (elements.length != 4) {
                            sendMessage("Неверный формат команды /register");
                            continue;
                        }
                        if (server.getAuthenticationProvider().registration(this, elements[1], elements[2], elements[3])) {
                            break;
                        }
                        continue;
                    }
                    sendMessage("Перед работой с чатом необходимо выполнить аутентификацию '/auth login password' или регистрацию '/register login password username'");
                }
                while (true) {
                    String message = in.readUTF();
                    if (message.startsWith("/")) {
                        if (message.equals("/exit")) {
                            sendMessage("/exitok");
                            server.broadcastMessage("Из чата вышел: " + this.getUsername());
                            break;
                        } else if (message.startsWith("/w")) {
                            handleDirectMessageCommand(message);
                        } else if (message.startsWith("/kick") && server.getAuthenticationProvider().privilegeElevation(this)) {
                            handleKickCommand(message);
                        }
                        continue;
                    }
                    server.broadcastMessage(username + ": " + message);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                disconnect();
            }
        }).start();
    }

    public void sendMessage(String message) {
        try {
            out.writeUTF(message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void disconnect() {
        server.unsubscribe(this);
        try {
            if (in != null) {
                in.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            if (out != null) {
                out.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean validateCommand(Pattern commandPattern, String command) {
        return commandPattern.asMatchPredicate().test(command);
    }

    private void handleDirectMessageCommand(String command) {
        if (validateCommand(DIRECT_MESSAGE_PATTERN, command)) {
            Matcher matcher = DIRECT_MESSAGE_PATTERN.matcher(command);
            if (matcher.find()) {
                String receiverUserName = matcher.group(1);
                String messageContents = matcher.group(2);
                String formattedMessage = username + ": " + messageContents;
                server.sendDirectMessage(receiverUserName, formattedMessage);
            }
        } else {
            server.sendDirectMessage(this.username, "Невалидная команда отправки личного сообщения");
        }
    }

    private void handleKickCommand(String command) {
        if (validateCommand(KICK_PATTERN, command)) {
            Matcher matcher = KICK_PATTERN.matcher(command);
            if (matcher.find()) {
                String userNameToKick = matcher.group(1);
                server.kickUser(userNameToKick);
            } else {
                server.sendDirectMessage(this.username, "Невалидная команда исключения пользователя из чата");
            }
        }
    }
}
