package ru.otus.june.chat.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClientHandler {
    private Server server;
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private String username;

    private Thread handlerThread;
    private final Pattern DIRECT_MESSAGE_PATTERN = Pattern.compile("/w\\s(\\w+)\\s(.*)");
    private final Pattern KICK_PATTERN = Pattern.compile("/kick\\s(\\w+)");
    private final Pattern BAN_PATTERN = Pattern.compile("/ban\\s(\\w+)");
    private final Pattern CHANGE_NICK_PATTERN = Pattern.compile("/changenick\\s(\\w+)");

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
        this.handlerThread =  new Thread(() -> {
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
                        } else if (message.startsWith("/w"))
                            handleDirectMessageCommand(message);
                        else if (message.startsWith("/kick") && server.getAuthenticationProvider().privilegeElevation(this))
                            handleKickCommand(message);
                        else if (message.startsWith("/shutdown") && server.getAuthenticationProvider().privilegeElevation(this))
                            handleShutdownCommand();
                        else if (message.startsWith("/activelist")) {
                            String listOfActiveUsers = String.join(", ", handleActiveListCommand());
                            server.sendDirectMessage(this.username, String.format("Список активных пользователей: %s", listOfActiveUsers));
                        } else if (message.startsWith("/ban") && server.getAuthenticationProvider().privilegeElevation(this))
                            handleBanCommand(message);
                        else if (message.startsWith("/changenick"))
                            handleChangeNickCommand(message);
                        continue;
                    }
                    server.broadcastMessage(getMessageTimestampAsString() + " " + username + ": " + message);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                disconnect();
            }
        });
    }

    private String getMessageTimestampAsString() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"));
    }

    public void handle() {
        handlerThread.setDaemon(true);
        handlerThread.start();
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
                String formattedMessage = getMessageTimestampAsString() + " " + username + ": " + messageContents;
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

    private void handleShutdownCommand() {
        server.shutdown();
    }

    private List<String> handleActiveListCommand() {
        return server.getUserNames();
    }

    private void handleBanCommand(String command) {
        if (validateCommand(BAN_PATTERN, command)) {
            Matcher matcher = BAN_PATTERN.matcher(command);
            if (matcher.find()) {
                String userNameToBan = matcher.group(1);
                server.sendDirectMessage(userNameToBan, "Вы были забанены администратором");
                server.disconnectAndBan(userNameToBan);
            } else {
                server.sendDirectMessage(this.username, "Невалидная команда бана пользователя");
            }
        }
    }

    private void handleChangeNickCommand(String command) {
        if (validateCommand(CHANGE_NICK_PATTERN, command)) {
            Matcher matcher = CHANGE_NICK_PATTERN.matcher(command);
            if (matcher.find()) {
                String newUserName = matcher.group(1);
                server.changeNickIfValid(this, newUserName);
                server.sendDirectMessage(this.username, String.format("Имя пользователя изменено на %s", newUserName));
            } else {
                server.sendDirectMessage(this.username,"Невалидная команда смены ника");
            }
        }
    }
}
