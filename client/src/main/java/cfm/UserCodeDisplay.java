package cfm;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.*;

public class UserCodeDisplay {

    private final JFrame codeDisplay;
    private final JPanel mainPanel;
    private final JTextField searchbar;
    private final JScrollPane pane;

    public UserCodeDisplay() {
        codeDisplay = new JFrame("User code");
        codeDisplay.setSize(500,500);
        codeDisplay.setLayout(new BorderLayout());

        codeDisplay.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        codeDisplay.setAlwaysOnTop(true);
        codeDisplay.setLocationRelativeTo(null);

        searchbar = new JTextField("/Project/src/main/java/fop/Test.java:Main");
        addChangeListener(searchbar, e -> SwingUtilities.invokeLater(() -> {
            refresh();
        }));

        mainPanel = new JPanel(new GridLayout(-1,3));
        pane = new JScrollPane(mainPanel, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        codeDisplay.add(searchbar, BorderLayout.NORTH);
        codeDisplay.add(pane, BorderLayout.CENTER);

        codeDisplay.setVisible(true);
        SwingUtilities.invokeLater(this::refresh);
    }

    private final HashMap<String, HashMap<Long, ArrayList<String>>> codeMap = new HashMap<>();
    private final ArrayList<JTextArea> areas = new ArrayList<>();

    private void refresh() {
        // Refresh all scroll panes

        String text = searchbar.getText();

        for (JTextArea area : areas) {
            mainPanel.remove(area);
        }
        areas.clear();

        HashMap<Long, ArrayList<String>> codeList = codeMap.get(text);
        if(codeList != null) {

            ArrayList<Map.Entry<Long, ArrayList<String>>> entries = new ArrayList<>(codeList.entrySet());
            entries.sort(Comparator.comparingLong(Map.Entry::getKey));

            for (Map.Entry<Long, ArrayList<String>> entry : entries) {
                ArrayList<String> strings = entry.getValue();

                StringBuilder builder = new StringBuilder();

                for (String string : strings) {
                    if(builder.length() != 0) {
                        builder.append("\n");
                    }
                    builder.append(string);
                }

                JTextArea area = new JTextArea(builder.toString());
                area.setEditable(false);
                areas.add(area);

                mainPanel.add(area);
            }

        }

        pane.validate();
        pane.repaint();
        mainPanel.validate();
        mainPanel.repaint();
    }

    public void asyncChange(long userId, HashMap<String, HashMap<String, ArrayList<String>>> userCode) {
        for (Map.Entry<String, HashMap<String, ArrayList<String>>> fileEntry : userCode.entrySet()) {

            for (Map.Entry<String, ArrayList<String>> codeEntry : fileEntry.getValue().entrySet()) {
                String path = fileEntry.getKey() + ":" + codeEntry.getKey();
                ArrayList<String> code = transformCode(codeEntry.getValue());
                if(isEmpty(code) == false) {

                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            HashMap<Long, ArrayList<String>> all = codeMap.get(path);
                            if (all == null) {
                                all = new HashMap<>();
                                codeMap.put(path, all);
                            }
                            all.put(userId, code);

                            refresh();
                        }
                    });
                }
            }

        }
    }

    private boolean isEmpty(ArrayList<String> code) {
        if(code.isEmpty()) {
            return true;
        }
        boolean notEmpty = false;
        for (String s : code) {
            if(s.strip().isEmpty() == false) {
                notEmpty = true;
            }
        }
        return notEmpty == false;
    }


    private ArrayList<String> transformCode(ArrayList<String> value) {

        if(value.size() > 20) {
            ArrayList<String> result = new ArrayList<>();
            result.add("TOO LONG! (" + value.size() + ") rows.");
            return result;
        }
        for (int i = 0; i < value.size(); i++) {
            if(value.get(i).length() > 100) {
                ArrayList<String> result = new ArrayList<>();
                result.add("TOO WIDE! (" + value.get(i).length() + ") chars.");
                return result;
            }
        }

        if(value.isEmpty() == false) {
            int spacesBeforeCode = 50000;
            for (String s : value) {
                int spaces = 0;
                for (int i = 0; i < s.length(); i++) {
                    if(Character.isWhitespace(s.charAt(i))) {
                        spaces += 1;
                    }
                    else {
                        break;
                    }
                }
                if(s.isEmpty()) {
                    spaces = spacesBeforeCode;
                }
                spacesBeforeCode = Math.min(spaces, spacesBeforeCode);
            }

            //System.out.println(spacesBeforeCode + "?");
            if(spacesBeforeCode > 0) {
                for (int i = 0; i < value.size(); i++) {
                    value.set(i, value.get(i).substring(Math.min(spacesBeforeCode, value.get(i).length())));
                }
            }
        }

        return value;
    }

    public void exit() {
        // Dispose frame
        codeDisplay.setVisible(false);
        codeDisplay.dispose();
    }

    /**
     * Installs a listener to receive notification when the text of any
     * {@code JTextComponent} is changed. Internally, it installs a
     * {@link DocumentListener} on the text component's {@link Document},
     * and a {@link PropertyChangeListener} on the text component to detect
     * if the {@code Document} itself is replaced.
     *
     * @param text any text component, such as a {@link JTextField}
     *        or {@link JTextArea}
     * @param changeListener a listener to receieve {@link ChangeEvent}s
     *        when the text is changed; the source object for the events
     *        will be the text component
     * @throws NullPointerException if either parameter is null
     */
    public static void addChangeListener(JTextComponent text, ChangeListener changeListener) {
        Objects.requireNonNull(text);
        Objects.requireNonNull(changeListener);
        DocumentListener dl = new DocumentListener() {
            private int lastChange = 0, lastNotifiedChange = 0;

            @Override
            public void insertUpdate(DocumentEvent e) {
                changedUpdate(e);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                changedUpdate(e);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                lastChange++;
                SwingUtilities.invokeLater(() -> {
                    if (lastNotifiedChange != lastChange) {
                        lastNotifiedChange = lastChange;
                        changeListener.stateChanged(new ChangeEvent(text));
                    }
                });
            }
        };
        text.addPropertyChangeListener("document", (PropertyChangeEvent e) -> {
            Document d1 = (Document)e.getOldValue();
            Document d2 = (Document)e.getNewValue();
            if (d1 != null) d1.removeDocumentListener(dl);
            if (d2 != null) d2.addDocumentListener(dl);
            dl.changedUpdate(null);
        });
        Document d = text.getDocument();
        if (d != null) d.addDocumentListener(dl);
    }
}
