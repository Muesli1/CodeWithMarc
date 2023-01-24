package cfm;

import java.io.*;
import java.util.regex.Pattern;

public final class Constants {

    public static final String VERSION;

    public static void main(String[] args) {
        //TODO: REMOVE THIS METHOD
        //TODO: AUTOMATIC CORRECT BUILDING OF ALL THINGS
        //TODO: MAKE GIT REPOSITORY!

        //TODO: MAKE COMPLETE SERVER LOG WITH ALL MESSAGES (and IpAddress/Username)
        //TODO: SHOW CURRENT STATUS ON SERVER ALL THE TIME
    }
    static {

        try(InputStream inputStream = Constants.class.getResourceAsStream("/config.properties");
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            BufferedReader reader = new BufferedReader(inputStreamReader)) {
            VERSION = reader.readLine().split("=", 2)[1];
        } catch (Exception e) {
            throw new RuntimeException(e);
        }


    }

    public static final int PORT = 24112;
    public static final String IGNORE_PATTERN_PREFIX = "r";
    public static final String HOST_NAME = "themensprechstun.de";

    public static final String DEFAULT_ROOM_NAME = null; //"DoNotGuess"

    public static final String INCORRECT_ROOM_MESSAGE = "Incorrect room!";
    public static final String CORRECT_ROOM_MESSAGE = "Accepted!";

    public static final boolean CHECK_FOR_USER_CODE = true;
    public static final Pattern USER_CODE_START_PATTERN = Pattern.compile("[ \\t]*\\/\\/ *<USER CODE>[ \\t]*(.*?)$");
    public static final Pattern USER_CODE_END_PATTERN = Pattern.compile("[ \\t]*\\/\\/ *</USER CODE>[ \\t]*(.*?)$");

    //TODO: Debug! Maybe disable?
    public static final boolean CHECK_HASH_AFTER_MODIFY = true;

    public static final String PROJECT_FILE_NAME = ".cwmProject";

    public static final String[] ONLY_SYNC_ONCE = new String[] {"/Project/.idea/misc.xml","/Project/.idea/workspace.xml","/Project/.idea/gradle.xml"};

    public static final String[] DEFAULT_IGNORE_LINES =
           ("/Project/build/\n" +
            "/Project/gradle/\n" +
            "/Project/.gradle/\n" +
            "/Project/gradlew.bat\n" +
            "/Project/gradlew\n" +
            "r.*?\\/\\.goutputstream-.*?$\n" +
            "r.*?~$").split("\n");
}
