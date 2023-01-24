package cfm;

import java.util.ArrayList;

public class FileData {
    private final String hash;
    private final ArrayList<String> lines;

    public FileData(String hash, ArrayList<String> lines) {
        this.hash = hash;
        this.lines = lines;
    }

    public String getHash() {
        return hash;
    }

    public ArrayList<String> getLines() {
        return lines;
    }
}
