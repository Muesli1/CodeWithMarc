package muesli1;

import cfm.Client;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.TypedAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeAnyChangeAbstractAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public class PopupDialogAction extends AnAction {


    public static Client server;

    public static ArrayList<Project> projects = new ArrayList<>();

    @Override
    public void update(AnActionEvent e) {
        // Using the event, evaluate the context, and enable or disable the action.

        // Needs nothing at all
        e.getPresentation().setEnabledAndVisible(true);

    }

    public static Project project;

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        // Using the event, implement an action. For example, create and show a dialog.

        project = e.getProject();
        //System.out.println("PREFIX=" +AppSettingsState.getInstance().prefix);
        //System.out.println("DEV=" + AppSettingsState.getInstance().developer);
        Client.start(AppSettingsState.getInstance().developer, AppSettingsState.getInstance().prefix);

       // Client.setProject(e.getProject());

        System.out.println("Start it!");

        if(e.getProject() != null && projects.contains(e.getProject()) == false) {
            PsiManager.getInstance(e.getProject()).addPsiTreeChangeListener(new PsiTreeAnyChangeAbstractAdapter() {
                @Override
                protected void onChange(@Nullable PsiFile file) {
                    Application application = ApplicationManager.getApplication();
                    if(application != null && Client.isRunning()) {
                        if(file != null) {
                            System.out.println(file.getName() + "<-");
                        }
                        application.invokeLater(() -> {
                            //FileDocumentManager.getInstance().reloadFromDisk();
                            System.out.println("save all documents.");
                            FileDocumentManager.getInstance().saveAllDocuments();
                        });

                        //
                    }


                }
            }, e.getProject());
            projects.add(e.getProject());
        }
    }

}