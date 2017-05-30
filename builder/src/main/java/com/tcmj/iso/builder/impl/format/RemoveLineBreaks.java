package com.tcmj.iso.builder.impl.format;

import com.tcmj.iso.api.SourceFormatter;

/**
 * Normalizing Formatter which removes line breaks and reduces all doubled spaces to one.
 * Also used for easier comparison of our test results
 */
public class RemoveLineBreaks implements SourceFormatter {
    protected static final String LINE = System.getProperty("line.separator");
    private static final String SPACE = " ";
    @Override public String format(String source) {
        if (source != null) {
            String result = source.replace("\r\n", "\n");
            result = result.replace("\n", SPACE);
            result = result.replace(LINE, SPACE);
            return result;
        }
        return source;
    }
}
