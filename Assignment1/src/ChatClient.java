import javax.swing.*;
import java.io.*;
import java.net.Socket;

public class ChatClient {
    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;
    private String username;
    private boolean isTyping = false;

    public ChatClient(String serverAddress, int port) {
        try {
            socket = new Socket(serverAddress, port);
            out = new DataOutputStream(socket.getOutputStream());
            in = new DataInputStream(socket.getInputStream());

            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            System.out.println(in.readUTF()); // Prompt for login/register
            String choice = reader.readLine();
            out.writeUTF(choice);

            // Handle registration/login
            if (choice.equals("1")) {

                System.out.println(in.readUTF());
                username = reader.readLine();
                out.writeUTF(username);

                System.out.println(in.readUTF());
                String password = reader.readLine();
                out.writeUTF(password);

                System.out.println(in.readUTF());
            } else if (choice.equals("2")) {
                // Registration process
                System.out.println(in.readUTF());
                username = reader.readLine();
                out.writeUTF(username);

                System.out.println(in.readUTF());
                String password = reader.readLine();
                out.writeUTF(password);

                System.out.println(in.readUTF());
            }

            new Thread(new ReceiveMessages()).start();

            while (true) {
                String message = reader.readLine();

                // Handle typing indicator only after successful registration/login
                if (!isTyping) {
                    out.writeUTF("@typingstart");
                    isTyping = true;
                }

                if (message.startsWith("@sendfile ")) {
                    handleFileTransfer(message);
                } else if (message.equals("@typingstop")) {
                    isTyping = false;
                    out.writeUTF("@typingstop");
                } else {
                    out.writeUTF(message);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleFileTransfer(String message) throws IOException {
        String[] parts = message.split(" ");
        if (parts.length < 2) {
            System.out.println("Invalid command format. Use '@sendfile username'");
            return;
        }

        String recipientName = parts[1];

        // Select a file using JFileChooser to make selection easier
        JFileChooser fileChooser = new JFileChooser();
        int result = fileChooser.showOpenDialog(null);

        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            out.writeUTF("@sendfile " + recipientName + " " + selectedFile.getName());

            // Send the file size and contents
            out.writeLong(selectedFile.length());
            try (FileInputStream fileIn = new FileInputStream(selectedFile)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fileIn.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }

            System.out.println("File sent: " + selectedFile.getName());
        } else {
            System.out.println("File selection canceled.");
        }
    }

    private class ReceiveMessages implements Runnable {
        @Override
        public void run() {
            try {
                while (true) {
                    String message = in.readUTF();
                    System.out.println("Server: " + message);

                    if (message.startsWith("[File]")) {
                        String fileName = message.substring(message.indexOf("] ") + 2);
                        long fileSize = in.readLong();
                        try (FileOutputStream fileOut = new FileOutputStream("received_" + fileName)) {
                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            while (fileSize > 0 && (bytesRead = in.read(buffer, 0, (int) Math.min(buffer.length, fileSize))) != -1) {
                                fileOut.write(buffer, 0, bytesRead);
                                fileSize -= bytesRead;
                            }
                        }
                        System.out.println("File saved as: " + "received_" + fileName);
                    } else if (message.equals("You are now idle") || message.equals("You are now active")) {
                        System.out.println("Server: " + message);
                    }
                }
            } catch (EOFException e) {
                System.out.println("Connection closed by server.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        String serverAddress = "localhost";
        int port = 12345;
        new ChatClient(serverAddress, port);
    }
}
