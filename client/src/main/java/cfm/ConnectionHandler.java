package cfm;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Objects;

public class ConnectionHandler {

    private final Client client;
    private final PrintWriter writer;
    private final BufferedReader reader;
    private final Socket socket;

    private final Thread thread;
    private volatile boolean alive = false;
    private final Object aliveMonitor = new Object();

    public ConnectionHandler(Client client, String ip, int port, String roomName, String key) throws IOException {
        this.client = client;

        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(ip, port), 3000);
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (IOException e) {
            throw new IOException("Could not connect!", e);
        }

        try {
            writer.println((key != null?key:"") + roomName);
            String result = reader.readLine();

            if(result == null) {
                throw new IOException("Null line");
            }
            else {
                if(Objects.equals(result, Constants.CORRECT_ROOM_MESSAGE)) {
                    // ACCEPTED!
                }
                else if(Objects.equals(result, Constants.INCORRECT_ROOM_MESSAGE)) {
                    // NOT ACCEPTED!
                    socket.close();
                    throw new IOException("Incorrect room!");
                }
                else {
                    throw new IOException("Invalid answer from server: " + result);
                }
            }

        } catch (IOException e) {
            throw new IOException("Could not authenticate!", e);
        }

        alive = true;

        this.thread = new Thread(this::listenToServer,"Server Connection Thread");
        this.thread.start();
    }

    private void listenToServer() {
        while(alive) {
            listen();
        }
    }

    private void listen() {
        try {
            String line = reader.readLine();
            if(line == null) {
                closeConnectionBlocking("Null line");
            }
            else {
                client.asyncReceiveMessage(this, line);
            }
        } catch (IOException e) {
            new RuntimeException("Error while trying to listen to server",e).printStackTrace();
            closeConnectionBlocking("Error");
        }
    }

    private void closeConnectionBlocking(String reason) {
        synchronized (aliveMonitor) {
            alive = false;
            client.asyncConnectionClosed(this, reason);
        }
        try {
            socket.close();
        } catch (IOException e) {
            new RuntimeException("Error while trying to close server socket. Close reason: " + reason, e).printStackTrace();
        }
    }

    public void sendMessage(String line) {
        synchronized (aliveMonitor) {
            if(alive == false) {
                return;
            }
        }

        writer.println(line);
    }

    public void close() {
        try {
            socket.close();
        } catch (IOException e) {

        }
    }
}
