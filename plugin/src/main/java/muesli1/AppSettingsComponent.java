package muesli1;

import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Supports creating and managing a {@link JPanel} for the Settings Dialog.
 */
public class AppSettingsComponent {

    private final JPanel myMainPanel;
    private final JBPasswordField myPasswordText = new JBPasswordField();
    private final JBCheckBox myDeveloperStatus = new JBCheckBox("Developer ");

    public AppSettingsComponent() {
        myMainPanel = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("Enter password: "), myPasswordText, 1, false)
                .addComponent(myDeveloperStatus, 1)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
    }

    public JPanel getPanel() {
        return myMainPanel;
    }

    public JComponent getPreferredFocusedComponent() {
        return myPasswordText;
    }

    @NotNull
    public String getPasswordText() {
        return myPasswordText.getText();
    }

    public void setPasswordText(@NotNull String newText) {
        myPasswordText.setText(newText);
    }

    public boolean getDeveloperStatus() {
        return myDeveloperStatus.isSelected();
    }

    public void setDeveloperStatus(boolean newStatus) {
        myDeveloperStatus.setSelected(newStatus);
    }

}