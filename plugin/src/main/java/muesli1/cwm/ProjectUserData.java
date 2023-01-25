package muesli1.cwm;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeAnyChangeAbstractAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static muesli1.cwm.CommonKt.MAX_USER_CODE_LENGTH;

class ProjectUserData extends ClientReceiver {

    @NotNull
    private final Project project;
    @NotNull
    private final Path basePath;
    @NotNull
    private final String projectName;
    @NotNull
    private final List<String> whitelist;
    @NotNull
    private final HashMap<Path, Boolean> whitelistCache = new HashMap<>();
    private PsiTreeAnyChangeAbstractAdapter listener;

    private final Map<Path, String> developerSetTextMap = new HashMap<>();
    private final Object developerSetTextMonitor = new Object();

    private final ClientApplication client = ClientKt.createClient();

    private final Map<String, Map<Integer, List<String>>> developerUserCodeMap = new HashMap<>();
    private final Object developerUserCodeMapMonitor = new Object();

    public Map<String, Map<Integer, List<String>>> getDeveloperUserCodeMap() {
        return developerUserCodeMap;
    }

    public Object getDeveloperUserCodeMapMonitor() {
        return developerUserCodeMapMonitor;
    }

    public ProjectUserData(@NotNull Project project, @NotNull Path basePath, @NotNull String projectName, @NotNull List<String> whitelist) {
        this.project = project;
        this.basePath = basePath;
        this.projectName = projectName;
        this.whitelist = whitelist;

        connectToServer();

        installPsiChangeListener();

        /*

                updateText(basePath.resolve("src").resolve("main").resolve("java")
                                   .resolve("Other.java"), "class Uga {\n" +
                                   "\n" +
                                   "    public static void main(String[] args) {\n" +
                                   "        // <USER CODE>\n" +
                                   "        \n\n\n\n" +
                                   "        // </USER CODE>\n" +
                                   "    }\n" +
                                   "}\n");
         */
    }

    private void connectToServer() {
        final AppSettingsState settingsState = AppSettingsState.getInstance();
        final boolean developer = settingsState.developer;
        final String prefix = settingsState.prefix;

        this.client.connect(developer? prefix : null, this);
    }


    @Nullable
    public PsiFile findFile(@NotNull Path projectPath) {
        if(project.isDisposed()) {
            return null;
        }
        final PsiManager psiManager = PsiManager.getInstance(project);


        final VirtualFile foundVirtualFile = VirtualFileManager.getInstance().findFileByNioPath(projectPath);
        if(foundVirtualFile != null && foundVirtualFile.isValid()) {
            final PsiFile foundPsiFile = psiManager.findFile(foundVirtualFile);
            if(foundPsiFile != null && foundPsiFile.isValid()) {
                return foundPsiFile;
            }
        }
        return null;
    }

    public void updateText(@NotNull Path projectPath, String text) {
        if(project.isDisposed()) {
            return;
        }
        synchronized(developerSetTextMonitor) {
            developerSetTextMap.put(projectPath, text);
        }
        PsiFile foundFile = findFile(projectPath);

        if(foundFile != null) {
            parseText(foundFile);
        }
        else {
            // Try to write with normal nio
            if(Files.exists(projectPath) && Files.isRegularFile(projectPath) && Files.isWritable(projectPath)) {

                try(BufferedWriter writer = Files.newBufferedWriter(projectPath)) {
                    writer.write(text);
                    writer.flush();
                }
                catch(IOException e) {
                    throw new RuntimeException(e);
                }
            }

        }
    }

    public void parseText(@NotNull PsiFile file) {
        if(!file.isValid()) {
            return;
        }
        final VirtualFile virtualFile = file.getVirtualFile();
        final PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
        final Document document = psiDocumentManager.getDocument(file);

        if(virtualFile != null && document != null && document.isWritable()) {
            // Found document!

            final String pathStr = virtualFile.getPath();
            final Path projectPath;

            try {
                projectPath = Path.of(pathStr);
            }
            catch(Exception e) {
                // Invalid path!
                return;
            }

            if(AppSettingsState.getInstance().developer) {
                // Developer filter and then send


                final Application application = ApplicationManager.getApplication();
                if(application != null) {
                    // Try to write
                    application.assertReadAccessAllowed();
                    final String currentText = document.getText();

                    uploadDeveloperCode(projectPath, currentText);
                }
                return;
            }

            final String myText;
            synchronized(developerSetTextMonitor) {
                myText = developerSetTextMap.get(projectPath);
            }

            if(myText == null) {
                // Ignore path!
                return;
            }

            //System.out.println(path);


            //System.out.println(document.getText());
            //System.out.println(document.getLineCount());

            final Application application = ApplicationManager.getApplication();
            if(application != null) {
                // Try to write
                application.assertReadAccessAllowed();

                final String currentText = document.getText();
                //System.out.println(currentText);
                //application.assertWriteAccessAllowed();

                final TextMergeResult mergeResult = TextMergeResult.mergeTexts(currentText, myText);

                uploadUserCode(projectPath, mergeResult.getUserCode());
                final String changedText = mergeResult.getText();

                //System.out.println("YAY");

                    /*try {
                        virtualFile.setBinaryContent("UGA".getBytes(StandardCharsets.UTF_8));
                    }
                    catch(IOException e) {
                        throw new RuntimeException(e);
                    }
                    virtualFile.refresh(false, false);*/

                if(!changedText.equals(currentText)) {
                    application.invokeLater(() -> {
                        application.runWriteAction(() -> {
                            if(application.isDisposed() ||
                                    project.isDisposed()) {
                                return;
                            }

                            application.assertWriteAccessAllowed();
                            document.setText(changedText);

                            psiDocumentManager.commitDocument(document);
                            file.clearCaches();
                        });


                        //file.clearCaches();
                    });
                }


                //PsiElementFactory factory = JavaParseP.getInstance(project).getElementFactory();
                //final PsiFile newFile = PsiFileFactory.getInstance(project).createFileFromText("Nope", file);
                //file.replace(newFile);


            }
        }

            /*
            if(application != null) {
                if(file != null) {
                    System.out.println(file.getName() + "<-");
                }
                application.invokeLater(() -> {
                    //FileDocumentManager.getInstance().reloadFromDisk();
                    System.out.println("save all documents.");
                    FileDocumentManager.getInstance().saveAllDocuments();
                });

                //
            }*/
    }

    private boolean checkProjectWhitelist(Path projectPath) {
        boolean isInWhitelist = false;
        Boolean cacheHit = whitelistCache.get(projectPath);

        if(cacheHit != null) {
            isInWhitelist = cacheHit;
        }
        else {
            for(Path whitelistedDir : getWhitelistedDirs()) {
                if(isChild(whitelistedDir, projectPath)) {
                    isInWhitelist = true;
                    break;
                }
            }
            whitelistCache.put(projectPath, isInWhitelist);
        }

        //System.out.println(projectPath + "->" + isInWhitelist);
        return isInWhitelist;
    }

    private void uploadDeveloperCode(Path projectPath, String currentText) {
        final Path relativePath = basePath.relativize(projectPath);

        if(checkProjectWhitelist(projectPath)) {

            //System.out.println("Upload developer code " + relativePath + ":");
            //System.out.println(currentText);

            client.sendUnsafe(new DeveloperUpdatePacket(pathToUniversalString(relativePath), currentText));
        }

    }

    private static boolean isChild(Path dir, Path file) {
        Path parent = file.getParent();
        while(parent != null) {
            if(parent.equals(dir)) {
                return true;
            }

            parent = parent.getParent();
        }
        return false;
    }

    private void uploadUserCode(Path projectPath, List<String> userCode) {
        final Path relativePath = basePath.relativize(projectPath);

        if(checkProjectWhitelist(projectPath)) {
            /*System.out.println("Upload user code " + relativePath + ":");
            for(int i = 0; i < userCode.size(); i++) {
                System.out.println("Code#" + i + ":");
                System.out.println(userCode.get(i));
            }*/

            if(checkForSafety(userCode)) {
                client.sendUnsafe(new UserCodeUpdatePacket(pathToUniversalString(relativePath), userCode));
            }
        }
    }

    private boolean checkForSafety(List<String> userCode) {
        final boolean tooLong = userCode.stream().anyMatch(s -> s.length() > MAX_USER_CODE_LENGTH);

        if(tooLong) {
            showNotification("User code is too long!", NotificationType.ERROR);
        }

        return !tooLong;
    }


    private void installPsiChangeListener() {


        listener = new PsiTreeAnyChangeAbstractAdapter() {
            @Override
            protected void onChange(@Nullable PsiFile file) {
                if(project.isDisposed()) {
                    return;
                }
                if(file == null) {
                    return;
                }

                parseText(file);

            }
        };
        PsiManager.getInstance(project).addPsiTreeChangeListener(listener);
    }

    public void uninstall() {
        PsiManager.getInstance(project).removePsiTreeChangeListener(listener);

        client.close();
    }

    @Override
    public void closed(@Nullable String reason, @Nullable String exceptionReason, boolean connecting) {

        final String str;
        if(exceptionReason != null) {
            str = exceptionReason;
        }
        else if(reason != null) {
            str = reason;
        }
        else {
            str = "Unknown";
        }

        final String suffix = connecting? "\nRetry in 5s" : "\nEND OF SESSION";

        showNotification("Code With Marc: Error in connection: " + str + suffix, NotificationType.ERROR);
    }

    public void showNotification(@NotNull String text, @NotNull NotificationType type) {
        NotificationGroupManager.getInstance()
                                .getNotificationGroup("Code With Marc Notifications")
                                .createNotification(text, type)
                                .notify(project);
    }

    @Override
    public void received(@NotNull Packet packet) {
        //System.out.println("Received: " + packet);
        if(packet instanceof DeveloperUpdatePacket) {
            final DeveloperUpdatePacket dup = (DeveloperUpdatePacket) packet;
            final Path projectPath = getProjectPath(dup.getPath());

            //System.out.println("Try receive: " + projectPath);
            final Application application = ApplicationManager.getApplication();
            if(application != null) {
                application.invokeLater(() -> {
                    updateText(projectPath, dup.getText());
                    //System.out.println("Received: " + projectPath);
                });
            }
            //System.out.println("DEV UPGRADE?");
        }
        else if(packet instanceof CompleteUserCodePacket) {
            synchronized(developerUserCodeMapMonitor) {
                developerUserCodeMap.clear();
                developerUserCodeMap.putAll(((CompleteUserCodePacket) packet).getCode());
                // System.out.println(developerUserCodeMap);
            }
        }
        else {
            throw new RuntimeException("Received invalid Packet: " + packet.getClass());
        }
    }

    @NotNull
    public Path getProjectPath(@NotNull String path) {
        return basePath.resolve(universalStringToPath(path));
    }


    public String getText(@NotNull Path projectPath) throws IOException {

        //TODO: PSI?
        /*final PsiFile foundFile = findFile(projectPath);

        if(foundFile != null) {
            final PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
            final Document document = psiDocumentManager.getDocument(foundFile);
            final VirtualFile virtualFile = foundFile.getVirtualFile();

            if(virtualFile != null && document != null) {
                // TODO: Readable?
                return document.getText();
            }
        }*/

        return String.join("\n", Files.readAllLines(projectPath));
    }

    public List<Path> getWhitelistedDirs() {

        final List<Path> dirs = new ArrayList<>();

        for(String s : whitelist) {
            try {
                final Path subPath = universalStringToPath(s);
                final Path fullPath = basePath.resolve(subPath);


                if(!Files.exists(fullPath) || !Files.isDirectory(fullPath)) {
                    showNotification("Non existent directory: " + s, NotificationType.ERROR);
                }
                else {
                    dirs.add(fullPath);
                }
            }
            catch(InvalidPathException e) {
                showNotification("Invalid path: " + s, NotificationType.ERROR);
            }
        }

        return dirs;
    }

    public List<Path> getWhitelistedFiles() {
        return getWhitelistedDirs().stream().flatMap(dir -> {
            try {
                return Files.walk(dir);
            }
            catch(IOException e) {
                throw new RuntimeException(e);
            }
        }).filter(Files::isRegularFile).filter(Files::exists).filter(Files::isReadable).collect(Collectors.toList());
    }

    @NotNull
    @Override
    public DeveloperInitPacket createDeveloperData() {

        List<Path> files = getWhitelistedFiles();
        final Map<String, String> code = new HashMap<>();

        for(Path file : files) {
            try {
                final String text = getText(file);
                final Path relativeFile = basePath.relativize(file);
                code.put(pathToUniversalString(relativeFile), text);
            }
            catch(IOException e) {
                showNotification("File error: " + file + " (" + e.toString() + ")", NotificationType.ERROR);
                throw new RuntimeException(e);
            }
        }

        showNotification("Sent developer packet with " + files.size() + " file(s).", NotificationType.INFORMATION);

        return new DeveloperInitPacket(projectName, code);
    }


    private static String pathToUniversalString(Path relativeFile) {
        final StringBuilder builder = new StringBuilder();
        for(int i = 0; i < relativeFile.getNameCount(); i++) {
            final String part = relativeFile.getName(i).toString();
            if(builder.length() != 0) {
                builder.append("/");
            }
            builder.append(part);
        }

        return builder.toString();
    }

    @NotNull
    private Path universalStringToPath(@NotNull String path) {
        final String[] split = path.split("/");
        if(split.length == 0) {
            throw new RuntimeException("Incorrect path: " + path);
        }
        Path p = Path.of(split[0]);
        for(int i = 1; i < split.length; i++) {
            p = p.resolve(split[i]);
        }

        return p;
    }

    @Override
    public void connectedSession() {
        showNotification("Connected to server!", NotificationType.INFORMATION);
    }
}
