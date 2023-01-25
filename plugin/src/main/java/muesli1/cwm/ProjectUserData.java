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
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class ProjectUserData extends ClientReceiver {

    @NotNull
    private final Project project;
    @NotNull
    private final Path basePath;
    @NotNull
    private final String projectName;
    private PsiTreeAnyChangeAbstractAdapter listener;

    private final Map<Path, String> setText = new HashMap<>();

    private final ClientApplication client = ClientKt.createClient();

    public ProjectUserData(@NotNull Project project, @NotNull Path basePath, @NotNull String projectName) {
        this.project = project;
        this.basePath = basePath;
        this.projectName = projectName;

        connectToServer();

        installPsiChangeListener();
    }

    private void connectToServer() {
        final AppSettingsState settingsState = AppSettingsState.getInstance();
        final boolean developer = settingsState.developer;
        final String prefix = settingsState.prefix;

        client.connect(developer? prefix : null, this);
    }


    public void updateText(@NotNull Path projectPath, String text) {
        setText.put(projectPath, text);

        final PsiManager psiManager = PsiManager.getInstance(project);

        boolean foundFile = false;

        final VirtualFile foundVirtualFile = VirtualFileManager.getInstance().findFileByNioPath(projectPath);
        if(foundVirtualFile != null && foundVirtualFile.isValid()) {
            final PsiFile foundPsiFile = psiManager.findFile(foundVirtualFile);
            if(foundPsiFile != null && foundPsiFile.isValid()) {
                parseText(foundPsiFile);
                foundFile = true;
            }
        }

        if(!foundFile) {
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

            final String myText = setText.get(projectPath);

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

    private void uploadDeveloperCode(Path projectPath, String currentText) {
        final Path relativePath = basePath.relativize(projectPath);

        System.out.println("Upload developer code " + relativePath + ":");
        System.out.println(currentText);
    }

    private void uploadUserCode(Path path, List<String> userCode) {
        final Path relativePath = basePath.relativize(path);

        System.out.println("Upload user code " + relativePath + ":");
        for(int i = 0; i < userCode.size(); i++) {
            System.out.println("Code#" + i + ":");
            System.out.println(userCode.get(i));
        }
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

                updateText(basePath.resolve("src").resolve("main").resolve("java")
                                   .resolve("Other.java"), "class Uga {\n" +
                                   "\n" +
                                   "    public static void main(String[] args) {\n" +
                                   "        // <USER CODE>\n" +
                                   "        \n\n\n\n" +
                                   "        // </USER CODE>\n" +
                                   "    }\n" +
                                   "}\n");


                parseText(file);

            }
        };
        PsiManager.getInstance(project).addPsiTreeChangeListener(listener);
    }

    public void uninstall() {
        PsiManager.getInstance(project).removePsiTreeChangeListener(listener);
    }

    @Override
    public void closed(@Nullable String reason, @Nullable String exceptionReason) {

        final String str;
        if(reason != null) {
            str = reason;
        }
        else if(exceptionReason != null) {
            str = exceptionReason;
        }
        else {
            str = "Unknown";
        }

        NotificationGroupManager.getInstance()
                                .getNotificationGroup("Code With Marc Notifications")
                                .createNotification("Code With Marc: Error in connection: " + str + "\nRetry in 5s", NotificationType.ERROR)
                                .notify(project);
    }

    @Override
    public void received(@NotNull Packet packet) {
        System.out.println("Received: " + packet);
    }

    @NotNull
    @Override
    public DeveloperInitPacket createDeveloperData() {
        return new DeveloperInitPacket(projectName);
    }
}
