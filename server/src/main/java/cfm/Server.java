package cfm;

import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class Server {

    public static final String DEVELOPER_KEY;
    public static final boolean PRINT_MESSAGES = false;

    public static long clientIdentifier = 1L;
    private static final JSONParser jsonParser = new JSONParser(JSONParser.MODE_STRICTEST);

    static {
        // Read developer key from file

        try (InputStream inputStream = Constants.class.getResourceAsStream("/developer_key.properties");
             InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
             BufferedReader reader = new BufferedReader(inputStreamReader)) {
            String line = reader.readLine();
            DEVELOPER_KEY = line.split("=", 2)[1];
        } catch (Exception e) {
            // Critical error
            throw new RuntimeException(e);
        }
    }

    /**
     * Name of the current Room.
     */
    @Nullable
    public String currentRoom = Constants.DEFAULT_ROOM_NAME;
    /**
     * Contains a list of all currently connected clients
     */
    private final ArrayList<ClientHandler> connectedClients = new ArrayList<>();
    /**
     * The message queue that gets processed by the worker thread
     * Clients can asynchronously write data to the queue
     */
    private final BlockingQueue<MessageData> messageQueue = new ArrayBlockingQueue<>(10000, true);
    /**
     * All files currently uploaded on the server kept in RAM
     */
    @Nullable
    private Repository serverRepository;

    /**
     * Crate a new server
     */
    public Server() {
        ServerSocket serverSocket;

        try {
            serverSocket = new ServerSocket(Constants.PORT);
        } catch (IOException e) {
            throw new RuntimeException("Could not open server.", e);
        }

        // Start message handler thread
        Thread messageHandlerThread = new Thread(this::messageHandlerThread, "Message Handler Thread");
        messageHandlerThread.start();

        System.out.println("Port: " + Constants.PORT);
        System.out.println("Server setup complete. Waiting for clients...");
        System.out.println();

        while (true) {
            try {
                Socket clientSocket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(this, clientSocket, clientIdentifier++);
                handler.start();
            } catch (IOException e) {
                new IOException("Error while trying to accept new client connection", e).printStackTrace();
            }
        }
    }

    private void messageHandlerThread() {
        while (true) {
            try {
                MessageData messageData = messageQueue.take();

                String line = messageData.line;
                ClientHandler handler = messageData.handler;

                try {
                    Object parsed = jsonParser.parse(line);

                    if (parsed == null || parsed instanceof JSONObject == false) {
                        handler.end();
                    } else {
                        JSONObject json = (JSONObject) parsed;
                        processMessage(handler, handler.getData(), handler.isVerifiedDeveloper(), json);
                    }
                } catch (Exception e) {
                    new RuntimeException("While trying to process client package", e).printStackTrace();
                    handler.end();
                }
            }
            catch (InterruptedException e) {
                new RuntimeException("While trying to process client package", e).printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        System.out.println("Running CodeWithMarc Version " + Constants.VERSION + " on (expected) host name " + Constants.HOST_NAME + " and port " + Constants.PORT);
        new Server();
    }

    /**
     * @return The name of the current room or null
     */
    @Nullable
    public String getCurrentRoom() {
        return currentRoom;
    }

    /**
     * @return If the server currently has a room setup.
     *         This returns false if Constants.DEFAULT_ROOM_NAME is null and the developer did not set a room yet
     */
    public boolean hasRoom() {
        return currentRoom != null;
    }

    /**
     * Asynchronously accepts new client and adds it to currently connected clients
     * @param handler Client handler
     */
    public void asyncAcceptedClient(@NotNull ClientHandler handler) {
        synchronized (connectedClients) {
            connectedClients.add(handler);
            System.out.println("Accepted new client: " + handler.getName() + " -- Currently connected: " + connectedClients.size() + " clients");
        }
    }

    /**
     * Asynchronously removes a client from the currently connected clients
     * @param handler Client handler
     * @param reason Reason why the connection was closed
     */
    public void asyncConnectionClosedToClient(ClientHandler handler, String reason) {
        synchronized (connectedClients) {
            connectedClients.remove(handler);
            System.out.println("Closed connection to client " + handler.getName() + ": " + reason + " -- Currently connected: " + connectedClients.size() + " clients");
        }
    }

    /**
     * Asynchronously adds the received message to the queue
     * @param handler Client handler
     * @param line Message
     */
    public void asyncReceiveMessage(ClientHandler handler, String line) {
        System.out.println("Received message from client " + handler.getName() + ": '" + (PRINT_MESSAGES ? line : "---") + "'");

        try {
            messageQueue.put(new MessageData(handler, line));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Processes a message of a client
     * @param handler Client handler
     * @param data The data holder of the client
     * @param verifiedDeveloper Whether the client is a verified developer
     * @param json The parsed JSON data of the message
     */
    private void processMessage(ClientHandler handler, ClientData data, boolean verifiedDeveloper, JSONObject json) {

        if (verifiedDeveloper) {

            if (data.initialMessage) {

                // Init repo!
                try {
                    initializeRepo(json);
                } catch (Exception e) {
                    e.printStackTrace();
                    handler.end();
                    return;
                }

                data.initialMessage = false;
            } else {
                try {
                    updateRepo(json);
                } catch (Exception e) {
                    e.printStackTrace();
                    handler.end();
                    return;
                }
            }

        } else {
            // Students only send initial scan and user code!
            if (data.initialMessage) {
                try {
                    data.studentRepo = new Repository(json, false);
                    checkAndSendUpdatesTo(handler, data);
                } catch (Exception e) {
                    e.printStackTrace();
                    handler.end();
                }
                data.initialMessage = false;

            } else {
                // USER CODE!
                //handler.end();

                try {
                    JsonHelper.parseUserCode(json, data.userCode);

                    for (ClientHandler connectedClient : connectedClients) {
                        if (connectedClient.isVerifiedDeveloper()) {
                            connectedClient.asyncSendMessage(JsonHelper.fromCompleteUserCode(handler.getIdentifier(), data.userCode).toJSONString());
                        }
                    }

                    //System.out.println(data.userCode);
                } catch (Exception e) {
                    e.printStackTrace();
                    handler.end();
                }
            }
        }
    }


    /**
     * Check if there are any files on the server and tries to send them to the newly connected client
     * @param handler Client handler
     * @param data Client data
     */
    private void checkAndSendUpdatesTo(ClientHandler handler, ClientData data) {
        if (serverRepository != null) {
            try {
                data.studentRepo.sendDifferences(this, handler, serverRepository);
            } catch (Exception e) {
                e.printStackTrace();
                handler.end();
            }
        } else {
            // Client connected before there was any data.
        }
    }

    /**
     * Updates server repository with the JSON data and sends any changes to all connected clients
     * @param json JSON data
     * @throws Exception If something goes wrong
     */
    private void updateRepo(JSONObject json) throws Exception {
        if(serverRepository == null) {
            throw new RuntimeException("There was no initial repository!");
        }
        serverRepository.updateWith(json);

        for (ClientHandler handler : connectedClients) {
            if (handler.isVerifiedDeveloper() == false && handler.getData().initialMessage == false) {
                // Send update to student
                checkAndSendUpdatesTo(handler, handler.getData());
            }
        }
    }

    /**
     * Initialize the server repository with the JSON data
     * @param json JSON data
     * @throws Exception If something goes wrong
     */
    private void initializeRepo(JSONObject json) throws Exception {
        serverRepository = new Repository(json, true);
        System.out.println("DEVELOPER initialized repository with " + serverRepository.fileInfo());
    }

    /**
     * Asynchronously set the room of the server
     * @param requestedRoom The requested room
     */
    public void asyncSetRoomFromDeveloper(String requestedRoom) {
        System.out.println("DEVELOPER set room to '" + requestedRoom + "'");
        this.currentRoom = requestedRoom;
    }

    /**
     * Data holder for a ClientHandler and line pair
     */
    private static class MessageData {
        private final ClientHandler handler;
        private final String line;

        public MessageData(ClientHandler handler, String line) {

            this.handler = handler;
            this.line = line;
        }
    }

}
