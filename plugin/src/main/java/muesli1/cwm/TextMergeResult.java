package muesli1.cwm;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class TextMergeResult {

    @NotNull
    private final String text;
    @NotNull
    private final List<String> userCode;

    public TextMergeResult(@NotNull String text, @NotNull List<String> userCode) {
        this.text = text;
        this.userCode = userCode;
    }

    public List<String> getUserCode() {
        return userCode;
    }

    public String getText() {
        return text;
    }

    private static List<int[]> findUserCodeLimits(List<@NotNull String> lines) {
        final List<int[]> limits = new ArrayList<>();

        int userCodeStartLine = -1;
        boolean isUserCode = false;
        for(int i = 0; i < lines.size(); i++) {
            final String currentLine = lines.get(i);
            if(currentLine.contains("<USER CODE>") && isUserCode == false) {
                isUserCode = true;
                userCodeStartLine = i;
            }
            else if(currentLine.contains("</USER CODE>")) {
                if(isUserCode) {
                    limits.add(new int[]{userCodeStartLine, i});
                }
                isUserCode = false;
            }
        }

        return limits;
    }

    @NotNull
    public static TextMergeResult mergeTexts(@NotNull String currentText, @NotNull String expectedText) {
        final List<@NotNull String> currentLines = List.of(currentText.split("\n"));
        final List<@NotNull String> expectedLines = List.of(expectedText.split("\n"));
        final List<@NotNull String> combinedLines = new ArrayList<>();


        final List<int[]> currentLimits = findUserCodeLimits(currentLines);
        List<int[]> expectedLimits = findUserCodeLimits(expectedLines);

        final List<String> userCode = new ArrayList<>();

        if(currentLimits.size() != expectedLimits.size()) {
            for(int[] expectedLimit : expectedLimits) {
                userCode.add("");
            }
            // Discard new text! Incorrect structure!
            return TextMergeResult.of(expectedText, userCode);
        }

        for(int i = 0; i < expectedLines.size(); i++) {

            boolean add = true;

            for(int limitIndex = 0; limitIndex < expectedLimits.size(); limitIndex++) {
                final int limitStart = expectedLimits.get(limitIndex)[0];
                final int limitEnd = expectedLimits.get(limitIndex)[1];

                if(limitStart == i) {
                    // After Start of limit!
                    combinedLines.add(expectedLines.get(i));
                    add = false;

                    // Get current lines
                    final int[] currentLimit = currentLimits.get(limitIndex);

                    final List<String> limitData = new ArrayList<>();
                    for(int j = currentLimit[0] + 1; j < currentLimit[1]; j++) {
                        limitData.add(currentLines.get(j));
                    }

                    combinedLines.addAll(limitData);
                    userCode.add(String.join("\n",limitData));

                    break;
                }
                else if(limitStart < i && i < limitEnd) {
                    // In between!
                    add = false;
                    break;
                }
            }
            if(add) {
                combinedLines.add(expectedLines.get(i));
            }
        }

        if(userCode.size() != expectedLimits.size()) {
            throw new RuntimeException("Internal Error: " + userCode.size() + " incorrect user code size!");
        }

        return TextMergeResult.of(String.join("\n", combinedLines), userCode);
    }

    private static TextMergeResult of(String text, List<String> userCode) {
        return new TextMergeResult(text, userCode);
    }
}
