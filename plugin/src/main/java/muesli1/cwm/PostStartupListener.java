package muesli1.cwm;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class PostStartupListener implements StartupActivity {

    public static final String CONFIG_FILE_NAME = "cwm.txt";


    public static final Map<Project, ProjectUserData> projectMap = new HashMap<>();

    @Override
    public void runActivity(@NotNull Project project) {
        //System.out.println("Post startup project! " + project.getBasePath());

        // TODO: Only works for simple projects!
        final String basePathStr = project.getBasePath();

        if(basePathStr == null) {
            // Incorrect structure!
            return;
        }

        final Path basePath = Path.of(basePathStr);

        if(!Files.exists(basePath) || !Files.isDirectory(basePath)) {
            // Incorrect path!
            return;
        }

        final Path configFile = basePath.resolve(CONFIG_FILE_NAME);
        if(!Files.exists(configFile) || !Files.isRegularFile(configFile) || !Files.isReadable(configFile)) {
            // Incorrect setup!
            return;
        }

        final String projectName;

        try(BufferedReader reader = Files.newBufferedReader(configFile)) {
            projectName = reader.readLine();
        }
        catch(IOException e) {
            throw new RuntimeException(e);
        }

        if(projectName == null) {
            throw new RuntimeException("Could not find project name in config file!");
        }

        //System.out.println("Load project: '" + projectName + "'");

        NotificationGroupManager.getInstance()
                                .getNotificationGroup("Code With Marc Notifications")
                                .createNotification("Code With Marc: Initialized project '" + projectName + "'", NotificationType.INFORMATION)
                                .notify(project);


        createClient(project, basePath, projectName);

        ProjectManager.getInstance().addProjectManagerListener(project, new ProjectManagerListener() {
            @Override
            public void projectClosing(@NotNull Project project) {
                //System.out.println("Closing project: " + project.getBasePath());

                ProjectUserData data = projectMap.get(project);
                if(data != null) {
                    data.uninstall();
                    projectMap.remove(project);
                }
            }
        });
    }

    private void createClient(@NotNull Project project, @NotNull Path basePath, @NotNull String projectName) {

        projectMap.put(project,new ProjectUserData(project, basePath, projectName));

    }
}
