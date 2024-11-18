import java.io.*;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

class ClientHandler implements Runnable {
    private Socket clientSocket;
    private DataOutputStream out;
    private DataInputStream in;
    private String username;
    private boolean isAdmin = false;
    private String currentRoom;
    private static final String USER_FILE = "users.txt";
    private static Map<String, String> users = new HashMap<>();
    private long lastActiveTime;
    private static final long IDLE_TIMEOUT = 300_000; // 5 minutes
    private boolean isIdle = false;

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
        loadUsers();
        this.lastActiveTime = System.currentTimeMillis();
    }

    @Override
    public void run() {
        try {
            out = new DataOutputStream(clientSocket.getOutputStream());
            in = new DataInputStream(clientSocket.getInputStream());

            if (!authenticate()) {
                closeConnections();
                return;
            }

            System.out.println(username + " has joined the chat.");
            ChatServer.broadcastMessage(username + " has joined the chat.", this, currentRoom);

            new Thread(this::checkIdleStatus).start();

            String command;
            while (true) {
                command = in.readUTF();
                lastActiveTime = System.currentTimeMillis();

                if (isIdle) {
                    isIdle = false;
                    ChatServer.updateClientStatus(username, "Active");
                }

                if (command.equalsIgnoreCase("@history")) {
                    ChatServer.sendHistory(this);
                } else if (command.startsWith("@create ")) {
                    handleCreateRoom(command);
                } else if (command.startsWith("@join ")) {
                    handleJoinRoom(command);
                } else if (command.equalsIgnoreCase("@leave")) {
                    handleLeaveRoom();
                } else if (command.startsWith("@sendfile ")) {
                    handleFileTransfer(command);
                } else if (isAdmin && command.equalsIgnoreCase("@shutdown")) {
                    out.writeUTF("Shutting down the server...");
                    ChatServer.shutdownServer();
                    break;
                } else if (isAdmin && command.startsWith("@kick ")) {
                    handleKickCommand(command);
                } else if (isAdmin && command.startsWith("@broadcast ")) {
                    handleBroadcastCommand(command);
                } else if (command.startsWith("@")) {
                    sendPrivateMessage(command);
                } else if (currentRoom != null) {
                    ChatServer.broadcastMessage(username + ": " + command, this, currentRoom);
                } else {
                    sendMessage("Please join a room to chat. Use '@join roomName'");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (currentRoom != null) {
                ChatServer.removeFromRoom(currentRoom, this);
            }
            closeConnections();
        }
    }

    private void checkIdleStatus() {
        while (true) {
            if (System.currentTimeMillis() - lastActiveTime > IDLE_TIMEOUT && !isIdle) {
                isIdle = true;
                ChatServer.updateClientStatus(username, "Idle");
            }
            try {
                Thread.sleep(10_000); // Check idle status every 10 seconds
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleCreateRoom(String command) throws IOException {
        String roomName = command.split(" ", 2)[1];
        ChatServer.addToRoom(roomName, this);
        currentRoom = roomName;
        sendMessage("Room created and joined: " + roomName);
    }

    private void handleJoinRoom(String command) throws IOException {
        String roomName = command.split(" ", 2)[1];
        if (currentRoom != null) {
            ChatServer.removeFromRoom(currentRoom, this);
        }
        ChatServer.addToRoom(roomName, this);
        currentRoom = roomName;
        sendMessage("Joined room: " + roomName);
    }

    private void handleLeaveRoom() throws IOException {
        if (currentRoom != null) {
            ChatServer.removeFromRoom(currentRoom, this);
            sendMessage("Left room: " + currentRoom);
            currentRoom = null;
        } else {
            sendMessage("You're not in a room.");
        }
    }

    private boolean authenticate() throws IOException {
        out.writeUTF("Enter 1 to login, 2 to register:");
        String choice = in.readUTF();

        if (choice.equals("1")) {
            return login();
        } else if (choice.equals("2")) {
            return register();
        } else {
            out.writeUTF("Invalid option. Disconnecting.");
            return false;
        }
    }

    private boolean login() throws IOException {
        out.writeUTF("Username:");
        String username = in.readUTF();
        out.writeUTF("Password:");
        String password = in.readUTF();

        if (users.containsKey(username) && users.get(username).equals(password)) {
            this.username = username;
            out.writeUTF("Login successful. Welcome " + username + "!");
            return true;
        } else {
            out.writeUTF("Invalid credentials. Disconnecting.");
            return false;
        }
    }

    private boolean register() throws IOException {
        out.writeUTF("Choose a username:");
        String newUsername = in.readUTF();
        out.writeUTF("Choose a password:");
        String newPassword = in.readUTF();

        if (users.containsKey(newUsername)) {
            out.writeUTF("Username already exists. Try logging in.");
            return false;
        } else {
            users.put(newUsername, newPassword);
            saveUser(newUsername, newPassword);
            this.username = newUsername;
            out.writeUTF("Registration successful. Welcome " + newUsername + "!");
            return true;
        }
    }

    private void loadUsers() {
        try (BufferedReader reader = new BufferedReader(new FileReader(USER_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":");
                if (parts.length == 2) {
                    users.put(parts[0], parts[1]);
                }
            }
        } catch (IOException e) {
            System.out.println("Error loading users: " + e.getMessage());
        }
    }

    private void saveUser(String username, String password) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(USER_FILE, true))) {
            writer.write(username + ":" + password);
            writer.newLine();
        } catch (IOException e) {
            System.out.println("Error saving user: " + e.getMessage());
        }
    }

    public String getUsername() {
        return username;
    }

    public void setAdmin(boolean isAdmin) {
        this.isAdmin = isAdmin;
    }

    public boolean isAdmin() {
        return isAdmin;
    }

    public void closeConnections() {
        try {
            in.close();
            out.close();
            clientSocket.close();
            ChatServer.removeClient(this);
            ChatServer.broadcastMessage(username + " has left the chat.", this, currentRoom);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendMessage(String message) throws IOException {
        out.writeUTF(message);
    }

    private void handleFileTransfer(String command) throws IOException {
        String[] parts = command.split(" ", 3);
        if (parts.length != 3) {
            out.writeUTF("Invalid command format. Use '@sendfile username filename'");
            return;
        }

        String recipientName = parts[1];
        String fileName = parts[2];

        ClientHandler recipient = null;
        for (ClientHandler clientHandler : ChatServer.getClientHandlers()) {
            if (clientHandler.getUsername().equals(recipientName)) {
                recipient = clientHandler;
                break;
            }
        }

        if (recipient == null) {
            out.writeUTF("User not found: " + recipientName);
            return;
        }

        recipient.out.writeUTF("[File] " + fileName + " from " + username);

        long fileSize = in.readLong();
        recipient.out.writeLong(fileSize);

        byte[] buffer = new byte[4096];
        int bytesRead;
        while (fileSize > 0 && (bytesRead = in.read(buffer, 0, (int) Math.min(buffer.length, fileSize))) != -1) {
            recipient.out.write(buffer, 0, bytesRead);
            fileSize -= bytesRead;
        }

        out.writeUTF("File sent successfully to " + recipientName);
        recipient.out.writeUTF("File received and saved as 'received_" + fileName + "'");
    }

    private void handleKickCommand(String command) throws IOException {
        String[] parts = command.split(" ", 2);
        if (parts.length != 2) {
            out.writeUTF("Invalid command format. Use '@kick username'");
            return;
        }

        String targetUsername = parts[1];

        for (ClientHandler clientHandler : ChatServer.getClientHandlers()) {
            if (clientHandler.getUsername().equals(targetUsername)) {
                ChatServer.broadcastMessage("Admin has kicked " + targetUsername + " from the chat.", this, currentRoom);
                clientHandler.closeConnections();
                return;
            }
        }

        out.writeUTF("User not found: " + targetUsername);
    }

    private void handleBroadcastCommand(String command) throws IOException {
        String broadcastMessage = command.substring(11);
        ChatServer.broadcastMessage("[Admin Broadcast] " + broadcastMessage, this, currentRoom);
    }

    private void sendPrivateMessage(String message) throws IOException {
        int spaceIndex = message.indexOf(' ');
        if (spaceIndex == -1) {
            out.writeUTF("Invalid message format. Use '@username message'");
            return;
        }

        String recipientName = message.substring(1, spaceIndex);
        String privateMessage = message.substring(spaceIndex + 1);

        for (ClientHandler clientHandler : ChatServer.getClientHandlers()) {
            if (clientHandler.getUsername().equals(recipientName)) {
                clientHandler.sendMessage("[Private] " + username + ": " + privateMessage);
                sendMessage("[Private to " + recipientName + "] " + privateMessage);
                return;
            }
        }

        out.writeUTF("User not found: " + recipientName);
    }
}
