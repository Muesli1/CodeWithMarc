package muesli1.cwm;

import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class SampleDialogWrapper extends DialogWrapper {

    public SampleDialogWrapper() {
        super(true); // use current window as parent
        setTitle("Test DialogWrapper");
        init();
    }


    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return field;
    }

    private final JTextField field = new JTextField();
    private final JCheckBox checkBox = new JCheckBox("Live-Update", false);

    public JTextField getField() {
        return field;
    }

    public JCheckBox getCheckBox() {
        return checkBox;
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        JPanel dialogPanel = new JPanel(new BorderLayout());

        /*JLabel label = new JLabel("testing");
        label.setPreferredSize(new Dimension(100, 100));
        dialogPanel.add(label, BorderLayout.CENTER);*/


        dialogPanel.add(field, BorderLayout.CENTER);
        dialogPanel.add(checkBox, BorderLayout.SOUTH);

        return dialogPanel;
    }
}