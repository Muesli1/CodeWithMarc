package cfm;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class FileRepository {


    public static HashMap<String, FileData> getFileDataMap(JSONObject json, String name, boolean expectingLines) throws Exception {

        if(json.containsKey(name) == false) {
            throw new RuntimeException("Missing file data map '" + name + "'!");
        }
        Object obj = json.get(name);
        if(obj instanceof JSONObject == false) {
            throw new RuntimeException("Malformed file data map '" + name + "'!");
        }

        HashMap<String, FileData> map = new HashMap<>();

        JSONObject array = (JSONObject) obj;

        for (Map.Entry<String, Object> entry : array.entrySet()) {
            String path = entry.getKey();

            if(checkIsValidPath(path, false) == false) {
                throw new RuntimeException("Maliciously formed file name in '" + name + "' -> '" + path + "'!");
            }

            Object value = entry.getValue();
            if(value instanceof JSONObject == false) {
                throw new RuntimeException("Malformed file data map '" + name + "'!");
            }

            JSONObject fileDataJson = (JSONObject) value;
            if(fileDataJson.containsKey("hash") == false || (expectingLines && fileDataJson.containsKey("lines") == false)) {
                throw new RuntimeException("Malformed file data map '" + name + "'!");
            }

            Object hashObj = fileDataJson.get("hash");
            if(hashObj instanceof String == false) {
                throw new RuntimeException("Malformed file data map '" + name + "'!");
            }
            String hash = (String) hashObj;
            ArrayList<String> lines = null;

            if(expectingLines) {
                Object linesObj = fileDataJson.get("lines");
                if(linesObj instanceof JSONArray == false) {
                    throw new RuntimeException("Malformed file data map '" + name + "'!");
                }
                lines = readList(((JSONArray) linesObj));
            }

            map.put(path, new FileData(hash, lines));
        }

        return map;
    }

    private static ArrayList<String> readList(JSONArray array) throws Exception {

        ArrayList<String> list = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            Object obj = array.get(i);
            if(obj instanceof String == false) {
                throw new RuntimeException("Malformed list.");
            }
            list.add(((String) obj));
        }

        return list;
    }

    public static ArrayList<String> getFileList(JSONObject json, String name, boolean expectDirectories) throws Exception {

        if(json.containsKey(name) == false) {
            throw new RuntimeException("Missing file list '" + name + "'!");
        }
        Object obj = json.get(name);
        if(obj instanceof JSONArray == false) {
            throw new RuntimeException("Malformed file list '" + name + "'!");
        }

        JSONArray array = (JSONArray)obj;

        ArrayList<String> strings = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            Object o = array.get(i);
            if(o instanceof String == false) {
                throw new RuntimeException("Malformed file list '" + name + "'!");
            }

            String path = (String)o;
            if(checkIsValidPath(path, expectDirectories) == false) {
                throw new RuntimeException("Maliciously formed file name in '" + name + "' -> '" + path + "'!");
            }

            strings.add(path);
        }

        return strings;
    }

    public static Pattern VALID_PATH_PATTERN = Pattern.compile("^[A-Za-z0-9_. \\/-]+$");

    private static boolean checkIsValidPath(String path, boolean expectDirectories) {
        if(expectDirectories != path.endsWith("/")) {
            return false;
        }
        if(VALID_PATH_PATTERN.matcher(path).matches() == false) {
            return false;
        }

        return path.contains("//") == false;
    }

    protected ArrayList<String> directories = new ArrayList<>();
    protected HashMap<String, FileData> files = new HashMap<>();

    public FileRepository(JSONObject json, boolean developer) throws Exception {

        ArrayList<String> newFiles = getFileList(json, "newFiles", false);
        ArrayList<String> newDirectories = getFileList(json, "newDirectories", true);
        HashMap<String, FileData> changedFiles = getFileDataMap(json, "changedFiles", developer);

        if(developer) {
            ArrayList<String> removedFiles = getFileList(json, "removedFiles", false);
            ArrayList<String> removedDirectories = getFileList(json, "removedDirectories", true);

            if (removedFiles.isEmpty() == false || removedDirectories.isEmpty() == false) {
                throw new RuntimeException("Cannot remove files or directories in initial package!");
            }
        }

        directories.addAll(newDirectories);

        if (checkIdentical(newFiles, new ArrayList<>(changedFiles.keySet())) == false) {
            throw new RuntimeException("New files and changed files have to be the same thing!");
        }

        files.putAll(changedFiles);

        checkValidSystem();

    }

    public static boolean checkIdentical(ArrayList<String> one, ArrayList<String> two) {

        if(one.size() != two.size()) {
            return false;
        }

        ArrayList<String> copyOne = new ArrayList<>(one);
        for (String s : two) {
            copyOne.remove(s);
        }

        if(copyOne.isEmpty() == false) {
            return false;
        }

        ArrayList<String> copyTwo = new ArrayList<>(two);
        for (String s : one) {
            copyTwo.remove(s);
        }

        return copyTwo.isEmpty();
    }

    public void updateWith(JSONObject json) throws Exception {
        // Always developer

        ArrayList<String> newFiles = getFileList(json, "newFiles", false);
        ArrayList<String> newDirectories = getFileList(json, "newDirectories", true);
        ArrayList<String> removedFiles = getFileList(json, "removedFiles", false);
        ArrayList<String> removedDirectories = getFileList(json, "removedDirectories", true);
        HashMap<String, FileData> changedFiles = getFileDataMap(json, "changedFiles", true);

        /*if(checkIdentical(newFiles, new ArrayList<>(changedFiles.keySet())) == false) {
            throw new RuntimeException("New files and changed files have to be the same thing!");
        }*/

        for (String newDirectory : newDirectories) {
            if(directories.contains(newDirectory)) {
                throw new RuntimeException("Directory already there!");
            }
        }
        directories.addAll(newDirectories);

        for (String removedDirectory : removedDirectories) {
            if(directories.contains(removedDirectory) == false) {
                throw new RuntimeException("Directory was not there!");
            }
        }

        directories.removeAll(removedDirectories);


        for (String newFile : newFiles) {
            if(changedFiles.containsKey(newFile) == false) {
                throw new RuntimeException("New file '" + newFile + "' was not in changed files!");
            }
        }
        for (Map.Entry<String, FileData> stringFileDataEntry : changedFiles.entrySet()) {
            if(newFiles.contains(stringFileDataEntry.getKey()) == false && files.containsKey(stringFileDataEntry.getKey()) == false) {
                throw new RuntimeException("Changed file was not new or currently registered!");
            }
        }

        // Update files
        files.putAll(changedFiles);

        for (String removedFile : removedFiles) {
            if(files.containsKey(removedFile) == false) {
                throw new RuntimeException("Remove file was not registered!");
            }
        }

        for (String removedFile : removedFiles) {
            files.remove(removedFile);
        }



        checkValidSystem();
    }

    private void checkValidSystem() throws Exception {

        for (String directory : directories) {
            if(directory.equals("/")) {
                // Base directory
            }
            else {
                String[] split = directory.split("/");
                String myName = split[split.length-1];
                String neededDir = directory.substring(0, directory.length()-myName.length()-1);
                //System.out.println(directory + " needs " + neededDir);

                if(directories.contains(neededDir) == false) {
                    throw new RuntimeException("Directory '" + directory + "' needed '" + neededDir + "', but was not found!");
                }
            }
        }

        for (Map.Entry<String, FileData> entry : files.entrySet()) {
            String path = entry.getKey();
            String[] split = path.split("/");
            String myName = split[split.length-1];

            String neededDir = path.substring(0, path.length() - myName.length());
            if(directories.contains(neededDir) == false) {
                throw new RuntimeException("File '" + path + "' needed '" + neededDir + "', but was not found!");
            }
        }

    }
    public String fileInfo() {
        return files.size() + " files and " + directories.size() + " directories";
    }
}
