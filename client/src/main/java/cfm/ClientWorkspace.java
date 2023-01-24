package cfm;

import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class ClientWorkspace {

    private final Client client;

    private final Thread thread;
    private final LinkedList<Event> eventQueue = new LinkedList<>();


    // Remember this when disconnected temporarily
    private File workspaceDirectory = null;

    private JFrame infoFrame;
    private final JLabel infoLabel = new JLabel();
    private final JLabel infoLabel2 = new JLabel();
    private JButton completeResync;
    private boolean activated = false;

    private volatile boolean running = true;



    // Cached file data:
    private final ArrayList<String> ignoreRules = new ArrayList<>();
    private final ArrayList<Pattern> ignorePatterns = new ArrayList<>();
    // Use LinkedHashMap to remove files in correct order!
    private final LinkedHashMap<String, String> hashMap = new LinkedHashMap<>();
    private HashSet<String> removedSet = new HashSet<>();
    private final LinkedHashMap<String, Long> lastModifiedMap = new LinkedHashMap<>();
    private final ArrayList<String> newDirectories = new ArrayList<>();
    private final ArrayList<String> newFiles = new ArrayList<>();
    private final HashMap<String, FileData> changedFiles = new HashMap<String, FileData>();

    private void clearCache() {
        ignoreRules.clear();
        ignorePatterns.clear();
        hashMap.clear();
        removedSet.clear();
        lastModifiedMap.clear();
        newDirectories.clear();
        newFiles.clear();
        changedFiles.clear();
    }

    private UserCodeDisplay userCodeDisplay;

    public ClientWorkspace(Client client) {
        this.client = client;
        this.thread = new Thread(this::run,"Workspace Thread");
        this.thread.start();

        if(client.isDeveloper()) {
            userCodeDisplay = new UserCodeDisplay();
        }
    }



    private void hashFile(File file, String relativePath) {


        if(relativePath.equals("/" + Constants.PROJECT_FILE_NAME)) {
            // Ignore project name file
            return;
        }

        if(file.getName().contains("/")) {
            // Just to be safe
            throw new RuntimeException("Invalid file detected: Contained special character '/'!");
        }

        if(file.isDirectory()) {
            // Directories end with an / at the end of their name
            relativePath += "/";
        }

        if(ignoreRules.contains(relativePath)) {
            //Ignoring dir/file when in the ignored list
            return;
        }
        for (Pattern ignorePattern : ignorePatterns) {
            if(ignorePattern.matcher(relativePath).matches()) {
                return;
            }
        }

        if(isValidFile(file) == false) {
            System.out.println("Warning: Ignoring file: " + relativePath);
            return;
        }

        // Only put directories in the list, do not hash them (makes no sense)
        // Initial scan: Only put into hashMap and scan children
        // Difference scan: Check if already in hashMap
        //                  if not: NEW DIRECTORY: put into hashMap
        //                  otherwise remove from removedSet
        if(file.isDirectory()) {

            if(hashMap.containsKey(relativePath) == false) {
                //System.out.println("New directory: " + relativePath);
                newDirectories.add(relativePath);
                hashMap.put(relativePath,"DIR");
            }
            else {
                // Already seen directory, just mark it as seen
                removedSet.remove(relativePath);
            }

            for (File child : file.listFiles()) {
                hashFile(child, relativePath + child.getName());
            }
        }
        else {

            // Initial scan: Put hash into hashMap and time in lastModifiedMap
            // Difference scan: Check if already in hashMap/lastModifiedMap
            //                  if not: NEW FILE: put hash into hashMap and time into lastModifiedMap;
            //                  otherwise: remove from removedSet and check for file difference

            if(lastModifiedMap.containsKey(relativePath) == false) {
                // New file
                //System.out.println("New file: " + relativePath);
                newFiles.add(relativePath);
                hashOneFile(file, relativePath);
            }
            else {
                // Already seen file, mark it as seen and check if there is a difference
                removedSet.remove(relativePath);
                long newModified = file.lastModified();

                if(newModified != lastModifiedMap.get(relativePath)) {
                    //System.out.println("Modified? " + relativePath);
                    // Should be modified, but check md5 to be safe

                    String lastHash = hashMap.get(relativePath);
                    String newHash = null;
                    try {

                        ArrayList<String> lines = readLines(file);

                        newHash = getFileChecksum(MessageDigest.getInstance("MD5"), lines);
                        // Only set after there was no error, so that the next scan can try this again
                        lastModifiedMap.put(relativePath, newModified);

                        if(lastHash.equals(newHash) == false) {
                            hashMap.put(relativePath, newHash);
                            generateFileData(relativePath, lines, newHash);
                            System.out.println("Developer: Actually modified file: " + relativePath);
                        }
                    } catch (IOException e) {
                        new IOException("File '" + relativePath + "'", e).printStackTrace();
                    } catch (NoSuchAlgorithmException e) {
                        new IOException("File '" + relativePath + "'", e).printStackTrace();
                    }


                }
            }
        }
    }

    private boolean isValidFile(File file) {
        if(file.isDirectory()) {
            return true;
        }

        if(file.length() > client.maxSize) { // default 1MB
            System.out.println("Warning: File " + file.getName() + " is too big: " + file.length());
            return false;
        }

        if(client.onlyTextFiles && isTextFile(file) == false) {
            return false;
        }


        return true;
    }

    private boolean isTextFile(File file) {
        return file.getName().endsWith(".java") || file.getName().endsWith(".txt") || file.getName().endsWith(".gitignore") || file.getName().endsWith(".xml") || file.getName().endsWith(".name")
                || file.getName().endsWith(".kts") || file.getName().endsWith(".iml") || file.getName().endsWith(".bat") || file.getName().endsWith(".editorconfig");
    }

    private void hashOneFile(File file, String relativePath) {
        lastModifiedMap.put(relativePath, file.lastModified());


        try {
            ArrayList<String> lines = readLines(file);
            hashMap.put(relativePath, getFileChecksum(MessageDigest.getInstance("MD5"), lines));
            generateFileData(relativePath, lines, hashMap.get(relativePath));
        } catch (IOException e) {
            new IOException("While generating file data for file '" + relativePath + "' (" + file + ")", e).printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            new IOException("While generating file data for file '" + relativePath + "' (" + file + ")", e).printStackTrace();
        }

    }

    private ArrayList<String> readLines(File file) throws IOException {
        try(FileReader fileReader = new FileReader(file); BufferedReader reader = new BufferedReader(fileReader)) {

            ArrayList<String> lines = new ArrayList<>();

            String line;
            while ((line = reader.readLine()) != null) {
                //NICELY DONE!
                lines.add(line);
            }

            return lines;
        }
    }

    private void generateFileData(String relativePath, ArrayList<String> lines, String hash) {

        FileData data = new FileData(hash, lines);
        changedFiles.put(relativePath, data);

    }

    private void initialScan() {
        scan(true);
    }
    private void scan(boolean initial) {
        long start = System.nanoTime();

        // Reset temporary lists
        removedSet = new HashSet<>(hashMap.keySet());
        newDirectories.clear();
        newFiles.clear();
        changedFiles.clear();

        hashFile(workspaceDirectory,"");

        if(newDirectories.isEmpty() == false) {
            newDirectories.sort(Comparator.comparingInt(String::length));

        }

        if(newFiles.isEmpty() == false) {
            newFiles.sort(Comparator.comparingInt(String::length));

        }

        ArrayList<String> removedDirectories = new ArrayList<>();
        ArrayList<String> removedFiles = new ArrayList<>();

        // removedSet contains all directories and files that were missing in action (not seen)
        if(removedSet.isEmpty() == false) {

            HashSet<String> extraRemove = new HashSet<>();

            // If a directory is removed every file and directory in it must also be removed!
            for (String dir : removedSet) {
                // Check if this is a directory
                if(dir.endsWith("/")) {
                    for (String child : hashMap.keySet()) {
                        // Check if not same directory, is a subdirectory and not already on the remove list or temporary remove list
                        if(child.equals(dir) == false && child.startsWith(dir) && removedSet.contains(child) == false &&
                                extraRemove.contains(child) == false) {
                            extraRemove.add(child);
                        }
                    }
                }
            }

            removedSet.addAll(extraRemove);


            // Remove all files and then directories sorted with length
            ArrayList<String> sortedRemovalOrder = new ArrayList<>(removedSet);
            sortedRemovalOrder.sort((o1, o2) -> {
                boolean dir1 = o1.endsWith("/");
                boolean dir2 = o2.endsWith("/");
                if(dir1 != dir2) {
                    // File before dir
                    if(dir1) {
                        return 1;
                    }
                    else {
                        return -1;
                    }
                }
                else {
                    // If both are files, order is irrelevant. But for consistency’s sake order them with length
                    return -Integer.compare(o1.length(), o2.length());
                }
            });

            //Reverse removed Map


            for (String s : sortedRemovalOrder) {

                boolean isDirectory = s.endsWith("/");

                if(isDirectory) {
                    removedDirectories.add(s);
                }
                else {
                    removedFiles.add(s);
                }

                //System.out.println("Removed " + (isDirectory?"directory":"file") + ": " + s);
                if(hashMap.remove(s) == null) throw new RuntimeException("hashMap did not have removed file '" + s + "' in it! Critical failure!");

                if(isDirectory == false) {
                    // Last modified only saved for files
                    if (lastModifiedMap.remove(s) == null)
                        throw new RuntimeException("lastModifiedMap did not have removed file '" + s + "' in it! Critical failure!");
                }
            }
        }


        // SEND PACKAGE! AND IF NOT POSSIBLE; DISCONNECT!
        if(client.isDeveloper()) {
            client.sendDeveloperPackage(newDirectories, newFiles, removedDirectories, removedFiles, changedFiles);
        }
        else {
            if(removedDirectories.isEmpty() == false || removedFiles.isEmpty() == false) {
                throw new RuntimeException("Students should not be able to remove directories or files!");
            }
            client.sendStudentPackage(newDirectories, newFiles, changedFiles);
        }


        //System.out.println(String.format("Subsequent scan took %.4f seconds", (System.nanoTime()-start)/1000000000d));
    }

    private void run() {

        /*File file = new File("/home/marc/Downloads/_CodeWithMarcTest/Project/src/main/java/AsteroidRace.java");
        try {
            PrintWriter printWriter = new PrintWriter(new FileWriter(file),true);

            while(true) {

                printWriter.println("TEST LINE");


                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }*/


        while(running) {

            //System.out.println("TICK");
            synchronized (eventQueue) {
                while(eventQueue.isEmpty() == false) {
                    Event event = eventQueue.removeFirst();

                    if(event instanceof IntEvent) {
                        if(((IntEvent) event).getValue() == 1) {
                            //Activate
                            activateWorkspaceBlocking();
                        }
                        else if(((IntEvent) event).getValue() == 2) {
                            //Deactive
                            deactivateWorkspace();
                        }
                        else {
                            // Manual resync!
                            deactivateWorkspace();
                            activateWorkspaceBlocking();
                        }
                    }
                    else if(event instanceof MessageEvent) {
                        synchronized (this) {
                            if(activated && workspaceDirectory != null) {
                                receiveMessage(((MessageEvent) event).getLine());
                            }

                        }
                    }
                }
            }

            synchronized (this) {
                if (activated) {
                    if (client.isDeveloper()) {
                        //Actively scan files
                        scan(false);
                    }
                }
            }

            try {
                Thread.sleep(1L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private final JSONParser jsonParser = new JSONParser(JSONParser.MODE_STRICTEST);

    private void receiveMessage(String line) {
        //System.out.println("RECEIVE " + line);


        try {
            Object parsed = jsonParser.parse(line);

            if (parsed == null || parsed instanceof JSONObject == false) {
                System.out.println("Error: Wrong package!");
                client.getHandler().close();
            } else {
                JSONObject json = (JSONObject) parsed;

                //parse(handler, handler.getData(), handler.isVerifiedDeveloper(), json);
                try {
                    if(Client.STRESS_TEST == false) {
                        parse(json);
                    }
                    else {
                        ArrayList<String> stupid = new ArrayList<>();
                        HashMap<String, ArrayList<String>> userCodeLines = new HashMap<>();
                        stupid.add("DWA");
                        userCodeLines.put("OK",stupid);
                        client.sendStudentUserCodePackage("DUMMY", userCodeLines);
                    }
                }
                catch (Exception e) {
                    e.printStackTrace();
                    //TODO: Close or not close?
                    client.getHandler().close();
                }

            }
        } catch (ParseException e) {
            e.printStackTrace();
            client.getHandler().close();
            //System.out.println("EROR="+line);
        }
    }

    public File getFile(String relativePath) {
        if(relativePath.equals("/")) {
            throw new RuntimeException("Main directory was sent as change! Corrupted file system.");
        }
        String[] split = relativePath.split("/");
        if(split.length < 2) {
            throw new RuntimeException("No relative path was found for '" + relativePath + "'!");
        }
        if(split[0].equals("") == false) {
            throw new RuntimeException("Invalid relative path was found for '" + relativePath + "'!");
        }

        File file = workspaceDirectory;
        for (int i = 1; i < split.length; i++) {
            file = new File(file, split[i]);
        }
        //System.out.println(relativePath + " -> " + file);

        return file;
    }

    private void parse(JSONObject json) throws Exception {

        if(client.isDeveloper()) {
            //System.out.println(json);
            // USER DATA PACKAGE!
            HashMap<String, HashMap<String, ArrayList<String>>> userCode = JsonHelper.parseCompleteUserCode(json);
            long userId = JsonHelper.getUserId(json);

            //System.out.println(userId + ": " + userCode);
            userCodeDisplay.asyncChange(userId, userCode);

            return;
        }

        ArrayList<String> newFiles = FileRepository.getFileList(json, "newFiles", false);
        ArrayList<String> newDirectories = FileRepository.getFileList(json, "newDirectories", true);
        HashMap<String, FileData> changedFiles = FileRepository.getFileDataMap(json, "changedFiles", true);
        ArrayList<String> removedFiles = FileRepository.getFileList(json, "removedFiles", false);
        ArrayList<String> removedDirectories = FileRepository.getFileList(json, "removedDirectories", true);




        newDirectories.sort(Comparator.comparingInt(String::length));

        // Create directories
        for (String newDirectory : newDirectories) {
            File file = getFile(newDirectory);
            if(file.exists()) {
                if(Client.STRESS_TEST == false) System.out.println("Warning: Directory '" + newDirectory + "' was already there. Created by user?");
            }
            else {
                file.mkdir();
                if(file.exists() == false || file.isDirectory() == false) {
                    throw new RuntimeException("Could not create directory '" + file + "'!");
                }
                if(Client.STRESS_TEST == false) System.out.println("Info: Create directory '" + newDirectory + "' (" + file + ")");
            }
        }

        // Create/Change files
        for (String newFile : newFiles) {
            if(changedFiles.containsKey(newFile) == false) {
                throw new RuntimeException("New file '" + newFile + "' was not in changed files!");
            }
        }


        // TODO: DO THIS!
        /* if(changedFiles.size() > 0) {
            if(PopupDialogAction.project.isDisposed()) {
                Client.exit();
                throw new RuntimeException("Restart!");
            }
            else {
                ApplicationManager.getApplication().invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        //System.out.println("RUN RELOAD!");
                        FileDocumentManager.getInstance().reloadFromDisk(FileEditorManager.getInstance(PopupDialogAction.project).getSelectedTextEditor().getDocument());
                        VfsUtil.markDirtyAndRefresh(true, true, true, ProjectRootManager.getInstance(PopupDialogAction.project).getContentRoots());
                        //System.out.println("RELOADED!");
                    }
                });
            }
        }*/

        bigLoop:
        for (Map.Entry<String, FileData> stringFileDataEntry : changedFiles.entrySet()) {
            File file = getFile(stringFileDataEntry.getKey());
            if(newFiles.contains(stringFileDataEntry.getKey()) == false && file.exists() == false) {
                if(Client.STRESS_TEST == false) System.out.println("Info: Changed file '" + stringFileDataEntry.getKey() + "' was not new or existing. Overwritting data");
            }
            if(newFiles.contains(stringFileDataEntry.getKey()) && file.exists()) {
                if(Client.STRESS_TEST == false) System.out.println("Warning: File '" + stringFileDataEntry.getKey() + "' was already there. Created by user?");
            }

            if(newFiles.contains(stringFileDataEntry.getKey()) == false) {
                // Modify!
                for (String s : Constants.ONLY_SYNC_ONCE) {
                    if(s.equals(stringFileDataEntry.getKey())) {
                        // DO NOT MODIFY
                        continue bigLoop;
                    }
                }
            }

            writeFile(stringFileDataEntry.getKey(), file, stringFileDataEntry.getValue());
        }

        // Delete files
        for (String removedFile : removedFiles) {
            File file = getFile(removedFile);
            if(file.exists() == false) {
                if(Client.STRESS_TEST == false) System.out.println("Warning: File '" + removedFile + "' did not exist. Already deleted by user?");
            }
            else {
                file.delete();
                if(file.exists()) {
                    throw new RuntimeException("Could not delete file '" + file + "'!");
                }
                if(Client.STRESS_TEST == false) System.out.println("Info: Deleted file '" + removedFile + "' (" + file + ")");
            }
        }

        // Delete directories
        for (String removedDirectory : removedDirectories) {
            File file = getFile(removedDirectory);
            if(file.exists() == false) {
                if(Client.STRESS_TEST == false) System.out.println("Warning: Directory '" + removedDirectory + "' did not exist. Already deleted by user?");
            }
            else {
                file.delete();
                if(file.exists()) {
                    throw new RuntimeException("Could not delete directory '" + file + "'!");
                }
                if(Client.STRESS_TEST == false) System.out.println("Info: Deleted directory '" + removedDirectory + "' (" + file + ")");
            }
        }





    }

    private void writeFile(String relativePath, File file, FileData value) {
        if(file.exists() == false) {
            try {
                file.createNewFile();
                if (file.exists() == false) {
                    throw new IOException("Creation succeeded but file did not exist afterwards.");
                }
                if(Client.STRESS_TEST == false) System.out.println("Info: Created file '" + relativePath + "' (" + file + ")");
            } catch (IOException e) {
                throw new RuntimeException("Could not create file '" + relativePath + "' (" + file + ")!", e);
            }
        }
        HashMap<String,ArrayList<String>> userCodeLines = new HashMap<>();

        if(Constants.CHECK_FOR_USER_CODE) {

            String currentUserCodeName = null;

            ArrayList<String> userCode = new ArrayList<>();

            try(FileReader fileReader = new FileReader(file); BufferedReader reader = new BufferedReader(fileReader)) {
                String line;
                while((line = reader.readLine()) != null) {

                    Matcher endMatcher = Constants.USER_CODE_END_PATTERN.matcher(line);
                    if(endMatcher.matches()) {
                        if(currentUserCodeName == null) {
                            System.out.println("Warning: Found end of non-beginning user code.");
                        }
                        else {

                            userCodeLines.put(currentUserCodeName, new ArrayList<>(userCode));
                            userCode.clear();
                            currentUserCodeName = null;
                        }
                    }
                    else {
                        if(currentUserCodeName != null) {
                            userCode.add(line);
                        }
                    }

                    Matcher startMatcher = Constants.USER_CODE_START_PATTERN.matcher(line);
                    if(startMatcher.matches()) {
                        currentUserCodeName = startMatcher.group(1);
                        userCode.clear();
                    }




                }
                if(currentUserCodeName != null) {
                    System.out.println("Warning: Could not find end of usercode '" + currentUserCodeName + "'!");
                    currentUserCodeName = null;
                }
            }
            catch (IOException e) {
                throw new RuntimeException("Could not read from file to check for user code '" + relativePath + "' (" + file + ")", e);
            }

            //System.out.println(userCodeLines);

            client.sendStudentUserCodePackage(relativePath, userCodeLines);
        }

        try(FileWriter fileWriter = new FileWriter(file); PrintWriter printWriter = new PrintWriter(fileWriter)) {

            String currentUserCodeName = null;

            for (String line : value.getLines()) {
                boolean shouldPrintLine = true;

                Matcher endMatcher = Constants.USER_CODE_END_PATTERN.matcher(line);
                if(endMatcher.matches()) {


                    if(currentUserCodeName == null) {
                        System.out.println("Warning: Found end of non-beginning user code.");
                    }
                    else {

                        if(userCodeLines.containsKey(currentUserCodeName) == false) {
                            System.out.println("Warning: Found no code for user code '" + currentUserCodeName + "'.");
                        }
                        else {
                            for (String s : userCodeLines.get(currentUserCodeName)) {
                                printWriter.println(s);
                            }
                        }

                        currentUserCodeName = null;
                    }
                }
                else {
                    if(currentUserCodeName != null) {
                        shouldPrintLine = false;
                    }
                }

                Matcher startMatcher = Constants.USER_CODE_START_PATTERN.matcher(line);
                if(startMatcher.matches()) {
                    currentUserCodeName = startMatcher.group(1);
                }

                if(shouldPrintLine) {
                    printWriter.println(line);
                }
            }
            printWriter.flush();

            if(Client.STRESS_TEST == false) System.out.println("Info: Modified file '" + relativePath + "' (" + file + ") [" + value.getHash() + "]");

            //FileDocumentManager.getInstance().reloadFromDisk(FileEditorManager.getInstance(e.getProject()).getSelectedTextEditor().getDocument());


            //ApplicationManager.getApplication().

            if(Constants.CHECK_HASH_AFTER_MODIFY) {
                ArrayList<String> lines = readLines(file);
                String newHash = getFileChecksum(MessageDigest.getInstance("MD5"), lines);
                //System.out.println(newHash);
                if (value.getHash().equals(newHash) == false) {
                    System.out.println("Warning: Hash-conflict in file '" + relativePath + "' (" + file + ")!");
                }
            }

        } catch (IOException e) {
            throw new RuntimeException("Could not write file '" + relativePath + "' (" + file + ")", e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Critical error. Could not find MD5.", e);
        }
    }

    private void deactivateWorkspace() {
        synchronized (this) {
            //System.out.println("DEACTIVATE!");
            if (infoFrame != null) {
                infoFrame.setVisible(false);
            }
            activated = false;

            clearCache();
        }
    }

    private void activateWorkspaceBlocking() {

        clearCache();
        displayInfoFrame();

        File setTempFile = workspaceDirectory;
        workspaceDirectory = null;


        //UIManager.put("FileChooser.readOnly", Boolean.TRUE);
        JFileChooser chooser = new JFileChooser();
        //TODO: DEBUG enable line
        //chooser.setCurrentDirectory(new java.io.File("."));
        chooser.setCurrentDirectory(new File("/home/marc/Downloads"));
        chooser.setDialogTitle("Select project directory");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);

        boolean firstLoop = true;

        while (true) {

            if(firstLoop) {
                firstLoop = false;
            }
            else {
                // Maybe choose other dir!
                setTempFile = null;
            }

            // Skip dialog when already selected setTempFile!
            if (setTempFile != null || chooser.showDialog(infoFrame, "Select as project directory") == JFileChooser.APPROVE_OPTION) {
                /*System.out.println("getCurrentDirectory(): "
                        + chooser.getCurrentDirectory());
                System.out.println("getSelectedFile() : "
                        + chooser.getSelectedFile());*/

                File selected = (setTempFile != null)? setTempFile : chooser.getSelectedFile();

                if (selected == null || selected.exists() == false || selected.isDirectory() == false || selected.listFiles() == null) {
                    System.out.println("Error: Invalid directory: " + selected);
                    continue;
                }

                File projectNameFile = new File(selected, Constants.PROJECT_FILE_NAME);

                boolean emptyDirectory = selected.listFiles().length == 0;
                boolean projectInDirectory = projectNameFile.exists();

                if (emptyDirectory || projectInDirectory) {
                    // Empty or project file

                    if(projectInDirectory) {
                        try(FileReader fileReader = new FileReader(projectNameFile); BufferedReader reader = new BufferedReader(fileReader)) {
                            String line = reader.readLine();
                            if(line == null) {
                                throw new IOException("Project name is empty.");
                            }
                            if(line.equals(client.getConnectedRoomName()) == false) {
                                int i = JOptionPane.showConfirmDialog(infoFrame, "Do you really want to overwrite the current project '" + line + "'?");
                                if (i != JOptionPane.OK_OPTION) {
                                    continue;
                                } else {
                                    // Proceed
                                    projectInDirectory = false;
                                    projectNameFile.delete();
                                    if(projectNameFile.exists()) {
                                        JOptionPane.showMessageDialog(infoFrame, "Could not delete existing project in directory '" + selected.getAbsolutePath() + "'.");
                                        continue;
                                    }
                                }
                            }
                            else {
                                // Load ignore rules from file
                                while((line = reader.readLine()) != null) {
                                    //System.out.println("Ignore rule: '" + line + "'");
                                    parseIgnoreLine(line);
                                }
                            }
                        } catch (FileNotFoundException e) {
                            JOptionPane.showMessageDialog(infoFrame, "Could not read project in directory '" + selected.getAbsolutePath() + "'. (" + e.getMessage() + ")");
                            continue;
                        } catch (IOException e) {
                            JOptionPane.showMessageDialog(infoFrame, "Could not read project in directory '" + selected.getAbsolutePath() + "'. (" + e.getMessage() + ")");
                            continue;
                        }
                    }

                    workspaceDirectory = selected;


                    try {
                        if(projectInDirectory == false) {
                            projectNameFile.createNewFile();
                        }

                        if(projectNameFile.exists() == false) {
                            throw new IOException("Could not create needed file " + projectNameFile);
                        }

                        if(projectInDirectory == false) {
                            try (FileWriter fileWriter = new FileWriter(projectNameFile); PrintWriter printWriter = new PrintWriter(fileWriter, true)) {
                                printWriter.println(client.getConnectedRoomName());

                                for (String defaultIgnoreLine : Constants.DEFAULT_IGNORE_LINES) {
                                    printWriter.println(defaultIgnoreLine);
                                    parseIgnoreLine(defaultIgnoreLine);
                                }
                                printWriter.flush();
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        continue;
                    }


                    break;
                }
                else {
                    JOptionPane.showMessageDialog(infoFrame, "The directory '" + selected.getAbsolutePath() + "' is not empty or a valid project.");
                    continue;
                }


            } else {
                int i = JOptionPane.showConfirmDialog(infoFrame, "Exit Code with Marc?");
                //int i = JOptionPane.OK_OPTION;
                if (i != JOptionPane.OK_OPTION) {
                    continue;
                } else {
                    //System.exit(5);
                    exitThis();
                    Client.exit();
                    return;
                }
            }
        }


        SwingUtilities.invokeLater(() -> {
            infoLabel2.setText("Sync running in background");
            completeResync.setEnabled(true);
        });

        initialScan();
        activated = true;

    }

    private void parseIgnoreLine(String line) {
        if(line.startsWith(Constants.IGNORE_PATTERN_PREFIX)) {
            line = line.substring(Constants.IGNORE_PATTERN_PREFIX.length());
            try {
                ignorePatterns.add(Pattern.compile(line));
            }
            catch (PatternSyntaxException e) {
                new RuntimeException("Incorrect ignore pattern: '" + line + "'", e).printStackTrace();
            }
        }
        else {
            ignoreRules.add(line);
        }
    }

    public void exitThis() {
        running = false;
        deactivateWorkspace();
        if(infoFrame != null) {
            infoFrame.dispose();
        }
        if(userCodeDisplay != null) {
            userCodeDisplay.exit();
        }
    }


    private void displayInfoFrame() {
        if(infoFrame != null) {
            infoFrame.setVisible(false);
            infoFrame = null;
        }

        infoFrame = new JFrame("Code with Marc v" + Constants.VERSION);
        infoFrame.setMinimumSize(new Dimension(280,150));
        infoFrame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        infoFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                super.windowClosed(e);
                Client.exit();
            }
        });
        infoFrame.setSize(300,200);
        infoFrame.setLayout(new GridLayout(-1, 1));
        infoFrame.add(infoLabel);
        infoFrame.add(infoLabel2);

        completeResync = new JButton("Manual resync");
        completeResync.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                //RESYNC!
                synchronized (eventQueue) {
                    eventQueue.add(new IntEvent(3));
                }
            }
        });
        //infoFrame.add(completeResync);



        infoFrame.setLocationRelativeTo(null);

        SwingUtilities.invokeLater(() -> {
            infoLabel.setHorizontalAlignment(SwingConstants.CENTER);
            infoLabel2.setHorizontalAlignment(SwingConstants.CENTER);
            completeResync.setEnabled(false);
            infoLabel.setText("Successfully connected.");
            infoLabel2.setText("Choose project directory to proceed.");
            if(Client.STRESS_TEST == false) {
                infoFrame.setVisible(true);
            }
        });
    }


    public void asyncEnable() {
        synchronized (eventQueue) {
            eventQueue.add(new IntEvent(1));
        }
    }

    public void asyncDisable() {
        synchronized (eventQueue) {
            eventQueue.add(new IntEvent(2));
        }
    }

    public void asyncReceiveMessage(ConnectionHandler connectionHandler, String line) {
        synchronized (eventQueue) {
            eventQueue.add(new MessageEvent(line));
        }
    }

    public void setWorkspaceFromFlag(String flag) {
        //workspaceDirectory = new File("/home/marc/Downloads/_CodeWithMarcTest");


        workspaceDirectory = new File(flag);
        if(workspaceDirectory.exists() == false || workspaceDirectory.isDirectory() == false) {
            throw new RuntimeException("Incorrect workspace: " + workspaceDirectory);
        }
    }

    private static class Event {

    }
    private static class IntEvent extends Event {
        private final int value;

        public IntEvent(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }
    private static class MessageEvent extends Event {
        private final String line;

        public MessageEvent(String line) {
            this.line = line;
        }

        public String getLine() {
            return line;
        }
    }

    private static String getFileChecksum(MessageDigest digest, ArrayList<String> lines)
    {
        for (String line : lines) {
            byte[] bytes;
            if(line.isEmpty()) {
                bytes = new byte[] {2, 5, 2};
            }
            else {
                bytes = line.getBytes(StandardCharsets.UTF_8);
            }
            digest.update(bytes, 0, bytes.length);
        }

        byte[] bytes = digest.digest();

        //This bytes[] has bytes in decimal format;
        //Convert it to hexadecimal format
        StringBuilder sb = new StringBuilder();
        for(int i=0; i< bytes.length ;i++)
        {
            sb.append(Integer.toString((bytes[i] & 0xff) + 0x100, 16).substring(1));
        }

        //return complete hash
        return sb.toString();
    }
    private static String getFileChecksum(MessageDigest digest, File file) throws IOException
    {
        //Get file input stream for reading the file content
        FileInputStream fis = new FileInputStream(file);

        //Create byte array to read data in chunks
        byte[] byteArray = new byte[1024];
        int bytesCount = 0;

        //Read file data and update in message digest
        while ((bytesCount = fis.read(byteArray)) != -1) {
            digest.update(byteArray, 0, bytesCount);
        }

        //close the stream; We don't need it now.
        fis.close();

        //Get the hash's bytes
        byte[] bytes = digest.digest();

        //This bytes[] has bytes in decimal format;
        //Convert it to hexadecimal format
        StringBuilder sb = new StringBuilder();
        for(int i=0; i< bytes.length ;i++)
        {
            sb.append(Integer.toString((bytes[i] & 0xff) + 0x100, 16).substring(1));
        }

        //return complete hash
        return sb.toString();
    }

}
