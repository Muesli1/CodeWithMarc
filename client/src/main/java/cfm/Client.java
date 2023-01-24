package cfm;

import net.minidev.json.JSONObject;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicLong;

public class Client {

    public static boolean STRESS_TEST = false;

    public static volatile AtomicLong tryId = new AtomicLong(1);
    public static boolean isRunning() {
        return instance != null;
    }

    public static void start(boolean developer, String prefix, boolean onlyTextFiles, long maxSize, String[] flags) {
        if(instance == null || STRESS_TEST) {
            instance = new Client(developer, prefix);
            instance.onlyTextFiles = onlyTextFiles;
            instance.maxSize = maxSize;
        }
        instance.open(flags);
    }
    public static void start(boolean developer, String prefix) {
        start(developer, prefix,false, 1048576*100, null); // 100 MB
    }
    public static void exit() {
        if(instance != null) {
            Client prevInstance = instance;
            instance = null;
            prevInstance.close();
        }
    }


    private volatile static Client instance;

    public static final long ANTI_SPAM_CONNECT_SLEEP = 5000L;
    private final boolean developer;
    private boolean isInitialized = false;

    public boolean onlyTextFiles = true;
    public long maxSize = 1048576; // 1 MB

    public static void main(String[] args) {
        if(args.length == 4) {
            start(args[0].equals("true"), args[1], false, 1048576*100, new String[] {args[2], args[3]});
        }
        else {
            System.out.println("Version " + Constants.VERSION);
        }
        //new Client(args.length == 2 && args[0].equals("DerEchteMusli"), args.length==2?args[1]:null);
        //start(args.length == 2 && args[0].equals("DerEchteMusli"), args.length==2?args[1]:null);
    }

    private ConnectionHandler handler;
    private final Object handlerMonitor = new Object();

    private JFrame connectionFrame;
    private final JLabel connectionLabel;
    private final JTextField roomName;
    private final JButton connectButton;

    private final ClientWorkspace workspace;
    private final String key;
    private String connectedRoomName;

    public Client(boolean developer, String key) {

        this.developer = developer;
        this.key = key;

        workspace = new ClientWorkspace(this);

        connectButton = new JButton("Connect");
        roomName = new JTextField("Room");
        connectionLabel = new JLabel("");

        if(developer) {
            connectionLabel.setText("DEVELOPER ACCOUNT");
        }
        roomName.setHorizontalAlignment(SwingConstants.CENTER);
        connectionLabel.setHorizontalAlignment(SwingConstants.CENTER);

        connectButton.addActionListener(e -> {
            if(connectButton.isEnabled()) {
                connectButton.setEnabled(false);
                connectButton.setText("Connecting...");

                final String text = roomName.getText();

                new Thread(() -> {
                    if(tryToConnectBlocking(text)) {
                        //END!
                        SwingUtilities.invokeLater(() -> {
                            connectionFrame.setVisible(false);
                        });
                        workspace.asyncEnable();
                    }
                    else {
                        try {
                            Thread.sleep(ANTI_SPAM_CONNECT_SLEEP);
                        } catch (InterruptedException ex) {
                            ex.printStackTrace();
                        }
                        SwingUtilities.invokeLater(() -> {
                            connectButton.setText("Connect");
                            connectButton.setEnabled(true);
                        });
                    }
                }, "Connection Try " + (tryId.getAndIncrement())).start();
            }
        });


        connectionFrame = new JFrame("Code with Marc v" + Constants.VERSION);
        connectionFrame.setMinimumSize(new Dimension(100,100));
        connectionFrame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        connectionFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                super.windowClosed(e);
                exit();
            }
        });
        connectionFrame.setLayout(new GridLayout(-1, 1));
        connectionFrame.add(connectionLabel);
        connectionFrame.add(roomName);
        connectionFrame.add(connectButton);
        connectionFrame.setSize(300,200);
        connectionFrame.setLocationRelativeTo(null);



    }

    public void open(String[] flags) {
        if(isInitialized == false) {

            if(STRESS_TEST == false) {
                SwingUtilities.invokeLater(() -> {
                    connectionFrame.setVisible(true);
                });
            }


            if(flags != null) {
                workspace.setWorkspaceFromFlag(flags[1]);

                SwingUtilities.invokeLater(() -> {
                    roomName.setText(flags[0]);
                    connectButton.doClick();
                });
            }

            isInitialized = true;
        }
        else {
            //What happens now? Nothing. A JFrame should be open!
        }
    }
    public void close() {
        // END EVERYTHING!
        workspace.exitThis();

        SwingUtilities.invokeLater(() -> {
            //connectionFrame.setVisible(false);
            connectionFrame.dispose();
        });

        synchronized (handlerMonitor) {
            if(handler != null) {
                handler.close();
            }
        }
    }


    public boolean tryToConnectBlocking(String roomName) {
        synchronized (handlerMonitor) {
            if (handler != null) {
                throw new RuntimeException("Already connected!");
            }
            try {
                connectedRoomName = roomName;
                System.out.println("Info: Connecting...");
                handler = new ConnectionHandler(this, Constants.HOST_NAME, Constants.PORT, roomName, developer?key:null);
                System.out.println("Info: Successfully connected to server.");
                return true;
            } catch (IOException e) {
                System.out.println("Error: Failed to connect: " + getWholeMessage(e));
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        connectButton.setText("Refreshing...");
                        connectionLabel.setText("Failed to connect:  " + getWholeMessage(e));
                    }
                });
                return false;
            }
        }
    }

    /*private static String readKey() {

        File keyFile = new File("/home/marc/Dokumente/Daten/CodeWithMarcKey.txt");
        if(keyFile.exists() == false) {
            throw new RuntimeException();
        }
        try(FileReader fileReader = new FileReader(keyFile); BufferedReader reader = new BufferedReader(fileReader)) {
            String s = reader.readLine();
            if(s == null || s.length() == 0) {
                throw new RuntimeException();
            }
            return s;
        } catch (IOException e) {
            throw new RuntimeException();
        }
    }*/

    private String getWholeMessage(Throwable e) {
        if(e.getCause() != null) {
            return e.getMessage() + " (" + getWholeMessage(e.getCause()) + ")";
        }

        return e.getMessage();
    }

    public void asyncReceiveMessage(ConnectionHandler connectionHandler, String line) {
        //System.out.println("Received message from server: " + line);
        workspace.asyncReceiveMessage(connectionHandler, line);
    }

    public void asyncConnectionClosed(ConnectionHandler connectionHandler, String reason) {

        synchronized (handlerMonitor) {
            handler = null;
            workspace.asyncDisable();
        }

        System.out.println("Error: Connection to server closed: " + reason);
        if(instance == this) {
            SwingUtilities.invokeLater(() -> {
                connectionLabel.setText("Connection to server closed: " + reason);
                connectButton.setText("Connect");
                connectButton.setEnabled(true);
                if(STRESS_TEST == false) {
                    connectionFrame.setVisible(true);
                }
            });
        }

    }

    public boolean isDeveloper() {
        return developer;
    }

    public String getConnectedRoomName() {
        return connectedRoomName;
    }

    public void sendDeveloperPackage(ArrayList<String> newDirectories, ArrayList<String> newFiles, ArrayList<String> removedDirectories, ArrayList<String> removedFiles, HashMap<String, FileData> changedFiles) {
        if(newDirectories.isEmpty() == false || newFiles.isEmpty() == false || removedDirectories.isEmpty() == false || removedFiles.isEmpty() == false || changedFiles.isEmpty() == false) {
            //System.out.println("Send package to server!");
            JSONObject json = new JSONObject();

            json.appendField("newDirectories", JsonHelper.toJsonArray(newDirectories));
            json.appendField("newFiles", JsonHelper.toJsonArray(newFiles));
            json.appendField("removedDirectories", JsonHelper.toJsonArray(removedDirectories));
            json.appendField("removedFiles", JsonHelper.toJsonArray(removedFiles));
            json.appendField("changedFiles", JsonHelper.toJsonObject(changedFiles,true));

            synchronized (handlerMonitor) {
                handler.sendMessage(json.toJSONString());
            }
        }
    }


    public void sendStudentPackage(ArrayList<String> newDirectories, ArrayList<String> newFiles, HashMap<String, FileData> changedFiles) {

        //Always send package. This is the initial and only package from every student
        JSONObject json = new JSONObject();

        json.appendField("newDirectories", JsonHelper.toJsonArray(newDirectories));
        json.appendField("newFiles", JsonHelper.toJsonArray(newFiles));
        json.appendField("changedFiles", JsonHelper.toJsonObject(changedFiles,false));

        synchronized (handlerMonitor) {
            if(handler != null) {
                handler.sendMessage(json.toJSONString());
            }
        }
    }

    public ConnectionHandler getHandler() {
        return handler;
    }

    public void sendStudentUserCodePackage(String relativePath, HashMap<String, ArrayList<String>> userCodeLines) {
        JSONObject json = JsonHelper.fromUserCode(relativePath, userCodeLines);

        synchronized (handlerMonitor) {
            if(handler != null) {
                handler.sendMessage(json.toJSONString());
            }
        }
    }
}
