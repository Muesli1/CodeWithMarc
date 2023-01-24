package cfm;

import java.io.*;
import java.net.Socket;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class ClientHandler {
    private final Server server;
    private final Socket clientSocket;
    private final long identifier;
    private final Thread listenerThread;
    private final Thread writeThread;

    private boolean accepted = false;
    private volatile boolean alive = false;
    private final Object aliveMonitor = new Object();

    private PrintWriter writer = null;
    private BufferedReader reader = null;

    private boolean isVerifiedDeveloper = false;
    private final BlockingQueue<String> queue = new ArrayBlockingQueue<>(500, true);

    private final ClientData data;

    public ClientData getData() {
        return data;
    }

    public ClientHandler(Server server, Socket clientSocket, long identifier) {
        this.server = server;
        this.clientSocket = clientSocket;
        this.identifier = identifier;
        this.data = new ClientData();
        this.listenerThread = new Thread(this::startListening);
        this.writeThread = new Thread(this::write);
    }

    private void write() {
        while(alive) {
            try {
                String poll = queue.poll(20L, TimeUnit.MINUTES);
                blockingSendMessage(poll);
            } catch (Exception e) {
                closeConnectionBlocking("Timeout");
                e.printStackTrace();
                break;
            }
        }
    }

    public long getIdentifier() {
        return identifier;
    }

    public boolean isVerifiedDeveloper() {
        return isVerifiedDeveloper;
    }

    public void start() {
        this.listenerThread.start();
    }

    private void startListening() {
        if(acceptClient(clientSocket)) {
            accepted = true;
            synchronized (aliveMonitor) {
                alive = true;
                server.asyncAcceptedClient(this);
            }
        }
        else {
            closeConnectionBlocking("Denied");
            accepted = false;
            return;
        }

        this.writeThread.start();

        while(alive) {
            listenToClient();
        }
    }

    private void listenToClient() {
        try {
            String line = reader.readLine();
            if(line == null) {
                closeConnectionBlocking("Null line");
            }
            else {
                server.asyncReceiveMessage(this, line);
            }
        } catch (IOException e) {
            new RuntimeException("Error while trying to listen to client",e).printStackTrace();
            closeConnectionBlocking("Error");
        }
    }

    private void closeConnectionBlocking(String reason) {
        synchronized (aliveMonitor) {
            alive = false;
            if (accepted) {
                server.asyncConnectionClosedToClient(this, reason);
            }
        }
        try {
            clientSocket.close();
        } catch (IOException e) {
            new RuntimeException("Error while trying to close client socket. Close reason: " + reason, e).printStackTrace();
        }
    }

    private boolean acceptClient(Socket clientSocket) {
        try {
            writer = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream()), true);
            reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

            String requestedRoom = reader.readLine();
            if(requestedRoom != null && requestedRoom.startsWith(Server.DEVELOPER_KEY)) {
                //DEVELOPER
                isVerifiedDeveloper = true;
                requestedRoom = requestedRoom.substring(Server.DEVELOPER_KEY.length());
                server.asyncSetRoomFromDeveloper(requestedRoom);
            }

            if(server.hasRoom() == false || Objects.equals(requestedRoom, server.getCurrentRoom()) == false) {
                // Incorrect room!
                writer.println(Constants.INCORRECT_ROOM_MESSAGE);
                return false;
            }

            writer.println(Constants.CORRECT_ROOM_MESSAGE);

            return true;
        } catch (IOException e) {
            new RuntimeException("Error while trying to accept client", e).printStackTrace();
            return false;
        }
    }

    public void blockingSendMessage(String line) {
        synchronized (aliveMonitor) {
            if(alive == false) {
                return;
            }
        }


        writer.println(line);


    }

    public void asyncSendMessage(String line) {
        synchronized (aliveMonitor) {
            if(alive == false) {
                return;
            }
        }

        try {
            //writer.println(line);
            if(queue.offer(line, 5, TimeUnit.SECONDS) == false) {
                throw new RuntimeException("Queue offer timeout");
            }
        }
        catch (Exception e) {
            new IOException("While trying to send message to client " + getName(), e).printStackTrace();
            closeConnectionBlocking("Timeout");
        }
    }

    public void end() {
        closeConnectionBlocking("Kicked");
    }

    public String getName() {
        return "#" + this.getIdentifier() + (this.isVerifiedDeveloper()?" (Developer)":"");
    }
}
