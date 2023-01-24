package cfm;

import net.minidev.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Repository extends FileRepository {


    public Repository(JSONObject json, boolean developer) throws Exception {
        super(json, developer);
    }

    public void sendDifferences(Server server, ClientHandler handler, Repository serverRepo) throws Exception {

        ArrayList<String> removedDirectories = new ArrayList<>();
        ArrayList<String> newDirectories = new ArrayList<>();
        ArrayList<String> removedFiles = new ArrayList<>();
        ArrayList<String> newFiles = new ArrayList<>();
        HashMap<String, FileData> changedFiles = new HashMap<>();

        for (String directory : directories) {
            if(serverRepo.directories.contains(directory) == false) {
                // Remove directory!
                removedDirectories.add(directory);
            }
        }
        for (String directory : serverRepo.directories) {
            if(directories.contains(directory) == false) {
                // Add directory!
                newDirectories.add(directory);
            }
        }

        for (Map.Entry<String, FileData> entry : files.entrySet()) {
            if(serverRepo.files.containsKey(entry.getKey()) == false) {
                // Removed file!
                removedFiles.add(entry.getKey());
            }
        }
        for (Map.Entry<String, FileData> entry : serverRepo.files.entrySet()) {
            boolean isNew = files.containsKey(entry.getKey()) == false;
            if(isNew) {
                // New file!
                newFiles.add(entry.getKey());
            }
            boolean wasChanged = isNew || files.get(entry.getKey()).getHash().equals(entry.getValue().getHash()) == false;
            if(wasChanged) {
                changedFiles.put(entry.getKey(), entry.getValue());
            }
        }

        JSONObject json = new JSONObject();

        json.appendField("newDirectories", JsonHelper.toJsonArray(newDirectories));
        json.appendField("newFiles", JsonHelper.toJsonArray(newFiles));
        json.appendField("removedDirectories", JsonHelper.toJsonArray(removedDirectories));
        json.appendField("removedFiles", JsonHelper.toJsonArray(removedFiles));
        json.appendField("changedFiles", JsonHelper.toJsonObject(changedFiles,true));

        updateWith(json);

        handler.asyncSendMessage(json.toJSONString());
    }
}
