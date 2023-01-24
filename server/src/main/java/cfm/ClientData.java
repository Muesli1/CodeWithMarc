package cfm;

import java.util.ArrayList;
import java.util.HashMap;

public class ClientData {

    public volatile boolean initialMessage = true;
    public Repository studentRepo = null;
    public HashMap<String, HashMap<String, ArrayList<String>>> userCode = new HashMap<>();

    public ClientData() {

    }
}
