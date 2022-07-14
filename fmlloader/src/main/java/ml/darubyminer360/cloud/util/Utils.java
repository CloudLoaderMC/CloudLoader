package ml.darubyminer360.cloud.util;

import java.util.regex.Pattern;

public class Utils {
    public static String replaceAllAfter(String input, String toReplace, String replaceWith, String start) {
        return replaceAllAfter(input, toReplace, replaceWith, start, null);
    }

    public static String replaceAllAfter(String input, String toReplace, String replaceWith, String start, String end) {
        int startIndex = input.indexOf(start);
        int endIndex = end != null ? input.indexOf(end) : -1;
        String newInput;
        if (startIndex >= 0) {
            if (endIndex >= 0) {
                newInput = input.substring(startIndex, endIndex);
            }
            else {
                newInput = input.substring(startIndex);
            }
        }
        else {
            newInput = input;
        }

        if (startIndex >= 0 && endIndex >= 0) {
            return input.replace(newInput, "").replace(input.substring(endIndex), "") + newInput.replaceAll(Pattern.quote(toReplace), replaceWith) + input.substring(endIndex);
        }
        return input.replace(newInput, "") + newInput.replaceAll(Pattern.quote(toReplace), replaceWith);
    }
}
