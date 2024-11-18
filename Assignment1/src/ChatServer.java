import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class ChatServer {
    private static final int PORT = 12345;
    private static List<ClientHandler> clientHandlers = new ArrayList<>();
    private static List<String> messageHistory = new ArrayList<>();
    private static final int HISTORY_LIMIT = 100;
    private static ClientHandler adminHandler;
    private static Map<String, Set<ClientHandler>> chatRooms = new HashMap<>();

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server is listening on port " + PORT);

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("New client connected");

                ClientHandler clientHandler = new ClientHandler(socket);

                if (clientHandlers.isEmpty()) {
                    adminHandler = clientHandler;
                    clientHandler.setAdmin(true);
                    System.out.println("Admin assigned: " + clientHandler.getUsername());
                }

                clientHandlers.add(clientHandler);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static List<ClientHandler> getClientHandlers() {
        return clientHandlers;
    }

    public static void broadcastMessage(String message, ClientHandler sender, String roomName) {
        System.out.println("Broadcasting in " + roomName + ": " + message);

        if (messageHistory.size() >= HISTORY_LIMIT) {
            messageHistory.remove(0);
        }
        messageHistory.add(message);

        Set<ClientHandler> roomMembers = chatRooms.get(roomName);
        if (roomMembers != null) {
            for (ClientHandler clientHandler : roomMembers) {
                if (clientHandler != sender) {
                    try {
                        clientHandler.sendMessage(message);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public static void sendHistory(ClientHandler clientHandler) {
        try {
            clientHandler.sendMessage("Chat History:");
            for (String message : messageHistory) {
                clientHandler.sendMessage(message);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void removeClient(ClientHandler clientHandler) {
        clientHandlers.remove(clientHandler);
    }

    public static ClientHandler getAdminHandler() {
        return adminHandler;
    }

    public static void shutdownServer() {
        for (ClientHandler clientHandler : clientHandlers) {
            clientHandler.closeConnections();
        }
        System.exit(0);
    }

    public static void addToRoom(String roomName, ClientHandler clientHandler) {
        chatRooms.computeIfAbsent(roomName, k -> new HashSet<>()).add(clientHandler);
    }

    public static void removeFromRoom(String roomName, ClientHandler clientHandler) {
        Set<ClientHandler> roomMembers = chatRooms.get(roomName);
        if (roomMembers != null) {
            roomMembers.remove(clientHandler);
            if (roomMembers.isEmpty()) {
                chatRooms.remove(roomName);
            }
        }
    }

    public static void updateClientStatus(String username, String status) {
        for (ClientHandler clientHandler : clientHandlers) {
            if (!clientHandler.getUsername().equals(username)) {
                try {
                    clientHandler.sendMessage(username + " is now " + status);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
