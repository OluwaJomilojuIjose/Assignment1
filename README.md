Application Overview: This project is a multi-client chat application built using Java, providing real-time communication capabilities for users in different chat rooms. The application supports private messaging, file transfers, chat rooms, typing indicators, and admin functionalities like user management and server shutdown. Designed for group collaboration, this chat system allows seamless communication in both personal and public settings, with novel features such as typing status indicators and idle user detection to enhance user engagement.

How to Run the Application:
1.	Server Setup:
•	Compile the server file: javac ChatServer.java.
•	Start the server by running: java ChatServer.
•	The server will start listening on port 12345 by default.
2.	Client Setup:
•	Compile the client file: javac ChatClient.java.
•	Start the client by running: java ChatClient.
•	Follow the prompts to either login or register.

Commands and Functionalities:
•	@history - View the chat history.

•	@create <roomName> - Create a new chat room and join it.

•	@join <roomName> - Join an existing chat room.

•	@leave - Leave the current chat room.

•	@sendfile <username> - Send a file to a specific user using dialog box.

•	@shutdown - Admin command to shut down the server.

•	@kick <username> - Admin command to kick a user from the server.

•	@broadcast <message> - Admin command to send a message to all users.

•	@<username> <message> - Send a private message to a specific user.

•	@typingstart - Indicate that the user is typing.

•	@typingstop - Indicate that the user has stopped typing.


