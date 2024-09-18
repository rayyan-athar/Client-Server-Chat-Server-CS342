/* Rayyan Athar, Mohammed Shayan Khan */

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.function.Consumer;

//import com.sun.security.ntlm.Client;
// import com.sun.security.ntlm.Client;
import javafx.application.Platform;
import javafx.scene.control.ListView;

public class Server {
	int count = 1;
	ArrayList<ClientThread> clients = new ArrayList<ClientThread>();
	ArrayList<ArrayList<String>> groups = new ArrayList<>();
	HashMap<String, ClientThread> clientConnections = new HashMap<>();
	ArrayList<String> users = new ArrayList<>();
	TheServer server;
	private Consumer<Serializable> callback;

	Server(Consumer<Serializable> call) {
		callback = call;
		server = new TheServer();
		server.start();
	}

	public class TheServer extends Thread {

		public void run() {
			try (ServerSocket mysocket = new ServerSocket(5555);) {
				while (true) {
					ClientThread c = new ClientThread(mysocket.accept(), count);
					clients.add(c);
					c.start();
					count++;
				}
			}//end of try
			catch (Exception e) {
				callback.accept("Server socket did not launch");
			}
		}//end of while
	}

	class ClientThread extends Thread {
		Socket connection;
		int count;
		ObjectInputStream in;
		ObjectOutputStream out;

		ClientThread(Socket s, int count) {
			this.connection = s;
			this.count = count;
		}

		public synchronized void updateUsers(Message message) throws Exception {
			for (String user : users) {
				ClientThread clientThread = clientConnections.get(user);
				// If current thread, continue to next
				if (clientThread == this) {
					continue;
				}
				clientThread.out.reset();
				clientThread.out.writeObject(message);
			}
		}

		public synchronized void updateGroups(Message message) throws Exception {
			for (String user : users) {
				ClientThread clientThread = clientConnections.get(user);

				clientThread.out.reset();
				clientThread.out.writeObject(message);
			}
		}


		public void removeSocket() {
			// Find key for corresponding client connection
			String username = "";
			ClientThread thread = null;
			for (Map.Entry<String, ClientThread> entry: clientConnections.entrySet()) {
				if (entry.getValue() == this) {
					username = entry.getKey();
					thread = entry.getValue();
					break;
				}
			}
			users.remove(username);
			// Remove user connection from hash map
			clientConnections.remove(username);
			// Remove client connection from list
			clients.remove(thread);
		}

		public synchronized void handleRequest(Message message) throws Exception {
			Message response;
			ClientThread recipiantThread;
			switch (message.type) {
				case "new_user":
					String new_username = message.content;
					if (users.contains(new_username)) {
						// Send invalid username error
						response = new Message("invalid_username", "invalid_username");
						this.out.writeObject(response);
					}
					else {
						// Add username to hash set
						users.add(new_username);
						clientConnections.put(new_username, this);
						response = new Message("ok_create_user", message.sender, new ArrayList<>(groups), new ArrayList<>(users));
						this.out.writeObject(response); // Confirm addition to the new user first
						updateUsers(new Message("ok_update_users", message.sender, null, users)); // Now update all users, including the new one
						// Update server log
						callback.accept(new_username + " joined the Server");
					}
					break;
				case "indiv_messsage":
					response = new Message("indiv_message_ok", message.content, message.sender, message.recipient);
					// Send message to recipiant
					recipiantThread = clientConnections.get(message.recipient);
					recipiantThread.out.writeObject(response);
					callback.accept(message.sender + " to " + message.recipient + ": " + message.content);
					// Send confirmation to the client
					break;
				case "group_message":
//					groupMessage(new Message("ok_group_message", message.content, message.sender, null, message.destinationGroup));
					Integer groupNum = message.destinationGroup;
					// For every user in group
					for (String user: groups.get(groupNum)) {
						// Get their connection thread
						recipiantThread = clientConnections.get(user);
						// If recipiant thread is null (user offline), continue to next user
						if (recipiantThread != null) {
							recipiantThread.out.reset();
							// Send response to user
							recipiantThread.out.writeObject(new Message("ok_group_message", message.content, message.sender, user, groupNum));
						}
					}
					callback.accept(message.sender + " to " + groups.get(groupNum) + ": " + message.content);
					break;
				case "create_group":
					ArrayList<String> newGroup = message.groups.get(0);
					synchronized (groups) {
						// Add current user to the newGroup
						newGroup.add(message.sender);
						// Add new group to groups
						groups.add(newGroup);
						// Update all other clients
						updateGroups(new Message("update_new_group", message.sender, groups, null));
					}
					callback.accept("Group: " + newGroup.toString() + " created");
					break;
				case "broadcast":
					response = new Message("ok_broadcast", message.content, message.sender, null);
					// Update all users with this new message
					updateUsers(response);
					callback.accept(message.sender + " to everyone: " + message.content);
					break;
				case "leave_user":
					String username = message.sender;
					// Remove username from hash set
					users.remove(username);
					// Remove client connection
					ClientThread userThread = clientConnections.get(username);
					clients.remove(userThread);
					// Remove user client connection
					clientConnections.remove(username);
					// Send confirmation that user has left
					this.out.writeObject(new Message("ok_leave_user", null, username, null));
					// Update users that current user has left the server
					updateUsers(new Message("update_leave_user", null, username, null));
					callback.accept(message.sender + " left the server");
					break;
			}
		}

		public void run() {

			try {
				in = new ObjectInputStream(connection.getInputStream());
				out = new ObjectOutputStream(connection.getOutputStream());
				connection.setTcpNoDelay(true);
			} catch (Exception e) {
				System.out.println("Streams not open");
			}

			while (true) {
				try {
					Message data = (Message) in.readObject();
					this.handleRequest(data);

				} catch (Exception e) {
					removeSocket();
					break;
				}
			}
		}//end of run
	}
}





