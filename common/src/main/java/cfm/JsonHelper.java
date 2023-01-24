package cfm;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class JsonHelper {


    public static JSONObject toJsonObject(HashMap<String, FileData> changedFiles, boolean lineData) {
        JSONObject object = new JSONObject();

        for (Map.Entry<String, FileData> entry : changedFiles.entrySet()) {
            JSONObject fileData = new JSONObject();

            fileData.put("hash",entry.getValue().getHash());
            if(lineData) {
                fileData.put("lines", toJsonArray(entry.getValue().getLines()));
            }

            object.put(entry.getKey(), fileData);
        }

        return object;
    }

    public static JSONArray toJsonArray(ArrayList<String> list) {

        JSONArray array = new JSONArray();
        for (int i = 0; i < list.size(); i++) {
            array.appendElement(list.get(i));
        }
        return array;
    }

    public static void parseUserCode(JSONObject json, HashMap<String, HashMap<String, ArrayList<String>>> map) throws Exception {


        if(json.containsKey("usercode") == false) {
            throw new RuntimeException("Malformed user code package");
        }
        if(json.get("usercode") instanceof String == false) {
            throw new RuntimeException("Malformed user code package");
        }
        String relativePath = (String)json.get("usercode");

        HashMap<String, ArrayList<String>> smallMap = new HashMap<>();
        map.put(relativePath, smallMap);

        if(json.containsKey("lines") == false) {
            throw new RuntimeException("Malformed user code package");
        }
        if(json.get("lines") instanceof JSONArray == false) {
            throw new RuntimeException("Malformed user code package");
        }

        JSONArray array = (JSONArray) json.get("lines");

        for (int i = 0; i < array.size(); i++) {
            Object obj = array.get(i);
            if(obj instanceof JSONObject == false) {
                throw new RuntimeException("Malformed user code package");
            }
            JSONObject entryObj = (JSONObject) obj;
            if(entryObj.containsKey("key") == false || entryObj.get("key") instanceof String == false) {
                throw new RuntimeException("Malformed user code package");
            }
            String key = ((String) entryObj.get("key"));

            if(entryObj.containsKey("value") == false || entryObj.get("value") instanceof JSONArray == false) {
                throw new RuntimeException("Malformed user code package");
            }

            JSONArray valueArray = (JSONArray) entryObj.get("value");
            ArrayList<String> lines = new ArrayList<>();

            for (int j = 0; j < valueArray.size(); j++) {
                if(valueArray.get(j) instanceof String == false) {
                    throw new RuntimeException("Malformed user code package");
                }
                lines.add((String) valueArray.get(j));
            }

            smallMap.put(key, lines);
        }
    }

    public static long getUserId(JSONObject object) throws Exception {
        if(object.containsKey("userid") == false || (object.get("userid") instanceof Integer == false && object.get("userid") instanceof Long == false)) {
            throw new RuntimeException("Malformed user code package");
        }
        if(object.get("userid") instanceof Integer) {
            return ((Integer) object.get("userid"));
        }
        else {
            return ((Long) object.get("userid"));
        }

    }
    public static  HashMap<String, HashMap<String, ArrayList<String>>> parseCompleteUserCode(JSONObject object) throws Exception {

        if(object.containsKey("userid") == false) {
            throw new RuntimeException("Malformed user code package");
        }
        HashMap<String, HashMap<String, ArrayList<String>>> map = new HashMap<>();
        for (String s : object.keySet()) {
            if(s.equals("userid")) {
                continue;
            }
            if(object.get(s) instanceof JSONObject == false) {
                throw new RuntimeException("Malformed user code package");
            }
            parseUserCode((JSONObject) object.get(s), map);
        }
        return map;
    }

    public static JSONObject fromCompleteUserCode(long userId, HashMap<String, HashMap<String, ArrayList<String>>> map) {
        JSONObject object = new JSONObject();
        object.put("userid",userId);

        for (Map.Entry<String, HashMap<String, ArrayList<String>>> entry : map.entrySet()) {
            //array.appendElement(fromUserCode(entry.getKey(), entry.getValue()));
            object.put(entry.getKey(), fromUserCode(entry.getKey(), entry.getValue()));
        }

        return object;
    }
    public static JSONObject fromUserCode(String relativePath, HashMap<String, ArrayList<String>> userCodeLines) {
        JSONObject json = new JSONObject();

        json.appendField("usercode",relativePath);

        JSONArray array = new JSONArray();

        for (Map.Entry<String, ArrayList<String>> stringArrayListEntry : userCodeLines.entrySet()) {
            JSONObject entryObj = new JSONObject();
            entryObj.appendField("key", stringArrayListEntry.getKey());
            entryObj.appendField("value", JsonHelper.toJsonArray(stringArrayListEntry.getValue()));

            array.appendElement(entryObj);
        }

        json.appendField("lines", array);

        return json;
    }
}
