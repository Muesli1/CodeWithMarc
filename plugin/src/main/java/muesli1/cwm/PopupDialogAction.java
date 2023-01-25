package muesli1.cwm;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class PopupDialogAction extends AnAction {


    public static final String SEPARATION_LAYER = "-".repeat(25);
    public static final int LIVE_UPDATE_WAIT_MILLIS = 100;
    private static final int LIVE_UPDATE_FLICKER_TICKS = 10;

    @Override
    public void update(AnActionEvent e) {
        // Using the event, evaluate the context, and enable or disable the action.

        // Needs nothing at all
        e.getPresentation().setEnabledAndVisible(AppSettingsState.getInstance().developer);
    }


    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        // Using the event, implement an action. For example, create and show a dialog.

        final SampleDialogWrapper wrapper = new SampleDialogWrapper();
        if(wrapper.showAndGet()) {
            // user pressed OK

            final String filter = wrapper.getField().getText();
            final boolean liveUpdate = wrapper.getCheckBox().isSelected();
            final Project project = e.getProject();


            if(project != null && !project.isDisposed()) {
                final ProjectUserData userData = PostStartupListener.getUserData(project);
                if(userData != null) {
                    // Display user data!

                    boolean correctFormat = false;
                    int userCodeIndex = -1;
                    String fileName = "None";

                    if(filter.contains(":")) {
                        String[] split = filter.split(":");
                        if(split.length == 2) {
                            try {
                                userCodeIndex = Integer.parseInt(split[1]);
                                fileName = split[0];
                                correctFormat = true;
                            }
                            catch(NumberFormatException ignored) {

                            }
                        }
                    }

                    if(!correctFormat) {
                        userData.showNotification("Expected format: FileName:UserCodeIndex", NotificationType.ERROR);
                        return;
                    }


                    final String code = getText(userData, fileName, userCodeIndex);

                    PsiFileFactory factory = PsiFileFactory.getInstance(project);

                    final PsiPlainTextFile textFile = (PsiPlainTextFile) factory.createFileFromText("userCode.txt", PlainTextLanguage.INSTANCE, code);
                    final PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
                    final Document document = psiDocumentManager.getDocument(textFile);

                    if(document == null) {
                        throw new RuntimeException("Could not create document!");
                    }
                    //final Editor editor = editorFactory.createEditor(document);
                    final VirtualFile virtualFile = textFile.getVirtualFile();
                    FileEditorManager.getInstance(project).openFile(virtualFile, true);

                    if(liveUpdate) {
                        final int finalUserCodeIndex = userCodeIndex;
                        final String finalFileName = fileName;
                        Thread t = new Thread(() -> {
                            final AtomicInteger updateTick = new AtomicInteger();
                            while(true) {
                                final Application application = ApplicationManager.getApplication();

                                if(textFile.isValid() && textFile.isWritable() && !project.isDisposed() &&
                                        document.isWritable() && application != null && !application.isDisposed() &&
                                        virtualFile.isValid() &&
                                        FileEditorManager.getInstance(project).isFileOpen(virtualFile)) {

                                    String newText = getText(userData, finalFileName, finalUserCodeIndex);

                                    application.invokeLaterOnWriteThread(() -> {
                                        application.runWriteAction(() -> {
                                            if(document.isWritable() && textFile.isValid()) {
                                                final String liveText = (updateTick.getAndIncrement() / LIVE_UPDATE_FLICKER_TICKS) % 2 == 0? "LIVE" : "";

                                                document.setText(liveText + "\n" + newText);
                                            }
                                        });
                                    });

                                    try {
                                        Thread.sleep(LIVE_UPDATE_WAIT_MILLIS);
                                    }
                                    catch(InterruptedException ex) {
                                        throw new RuntimeException(ex);
                                    }
                                }
                                else {
                                    //System.out.println("STOP IT!");
                                    break;
                                }
                            }
                        });
                        t.start();
                    }
                }
            }
        }

        //System.out.println("PREFIX=" +AppSettingsState.getInstance().prefix);
        //System.out.println("DEV=" + AppSettingsState.getInstance().developer);
        //Client.start(AppSettingsState.getInstance().developer, AppSettingsState.getInstance().prefix);

        // Client.setProject(e.getProject());

        /*for(Map.Entry<Project, ProjectUserData> ent : PostStartupListener.projectMap.entrySet()) {
            ent.getValue().uninstall();
        }*/
    }

    private static String getText(ProjectUserData userData, String filterFileName, int userCodeIndex) {

        final List<String> possibleFiles = new ArrayList<>();

        synchronized(userData.getDeveloperUserCodeMapMonitor()) {


            for(Map.Entry<String, Map<Integer, List<String>>> entry : userData.getDeveloperUserCodeMap().entrySet()) {
                if(entry.getKey().contains(filterFileName)) {
                    possibleFiles.add(entry.getKey());
                }
            }

            if(possibleFiles.size() != 1) {
                return "Found " + possibleFiles.size() + " possible files: " + possibleFiles;
            }
        }

        final String fileName = possibleFiles.get(0);
        final List<String> userCodeSnippets = new ArrayList<>();

        synchronized(userData.getDeveloperUserCodeMapMonitor()) {
            final Map<Integer, List<String>> map = userData.getDeveloperUserCodeMap().get(fileName);
            if(map != null) {
                for(Map.Entry<Integer, List<String>> userEntry : map.entrySet()) {
                    //final Integer userId = userEntry.getKey();
                    final List<String> list = userEntry.getValue();
                    if(list != null && userCodeIndex < list.size()) {
                        userCodeSnippets.add(list.get(userCodeIndex));
                    }
                }
                /*final List<String> list = map.get(userCodeIndex);
                if(list != null) {
                    userCodeSnippets.addAll(list);
                }*/
            }
        }

        final List<String> mappedUserCodeSnippets = new ArrayList<>();
        for(String snippet : userCodeSnippets) {
            final String mapped = mapSnippet(snippet);
            if(mapped != null) {
                mappedUserCodeSnippets.add(mapped);
            }
        }

        final StringBuilder code = new StringBuilder();

        code.append("Found ").append(mappedUserCodeSnippets.size()).append(" user codes:");
        code.append("\n");

        for(String mappedUserCodeSnippet : mappedUserCodeSnippets) {
            code.append("\n");
            code.append(SEPARATION_LAYER).append("\n");
            code.append("\n");
            code.append(mappedUserCodeSnippet).append("\n");
        }

        return code.toString();
    }

    @Nullable
    private static String mapSnippet(@NotNull String snippet) {
        snippet = snippet.replaceAll("\t", "    ");
        final String[] split = snippet.split("\n");
        boolean empty = true;
        for(String s : split) {
            if(!s.isBlank()) {
                empty = false;
                break;
            }
        }

        if(empty) {
            return null;
        }

        int minSpacesBefore = Integer.MAX_VALUE;
        for(String s : split) {
            int whiteSpaces = 0;
            for(int i = 0; i < s.length(); i++) {
                if(Character.isWhitespace(s.charAt(i))) {
                    whiteSpaces += 1;
                }
                else {
                    break;
                }
            }
            minSpacesBefore = Math.min(whiteSpaces, minSpacesBefore);
        }

        final List<String> result = new ArrayList<>();
        for(String s : split) {
            result.add(s.substring(minSpacesBefore));
        }

        // System.out.println(snippet + " -> " + result);

        return String.join("\n", result);
    }

}