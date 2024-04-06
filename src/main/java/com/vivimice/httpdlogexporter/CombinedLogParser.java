package com.vivimice.httpdlogexporter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import jakarta.annotation.Nonnull;

public class CombinedLogParser {
    
    private static final Pattern fieldPattern = Pattern.compile("%((\\!?\\d+(,\\d+)*)?((>?[%a-zA-Z])|(\\{.+?\\}(.|\\^..))))");

    @Nonnull
    private final Pattern pattern;
    @Nonnull
    private final Map<String, Integer> fieldGroupMap;

    private CombinedLogParser(@Nonnull Pattern pattern, @Nonnull Map<String, Integer> fieldGroupMap) {
        this.pattern = pattern;
        this.fieldGroupMap = fieldGroupMap;
    }

    public Set<String> getFieldNames() {
        return Collections.unmodifiableSet(fieldGroupMap.keySet());
    }

    @Nonnull
    public Map<String, String> parse(@Nonnull String line) {
        var matcher = pattern.matcher(line);
        if (!matcher.matches()) {
            System.err.println("log not match: " + line);
            return null;
        }

        var map = new HashMap<String, String>();
        fieldGroupMap.forEach((name, group) -> {
            map.put(name, unescape(matcher.group(group)));
        });
        return map;
    }

    private String unescape(String value) {
        var valueBuilder = new StringBuilder();
        var escape = false;
        for (char ch : value.toCharArray()) {
            if (escape) {
                valueBuilder.append((switch (ch) {
                    case 'b' -> '\b';
                    case 't' -> '\t';
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 'f' -> '\f'; 
                    default -> ch;
                }));
                escape = false;
            } else {
                if (ch == '\\') {
                    escape = true;
                } else {
                    valueBuilder.append(ch);
                }
            }
        }

        return valueBuilder.toString();
    }

    // See: https://httpd.apache.org/docs/2.4/mod/mod_log_config.html#formats
    @Nonnull
    public static CombinedLogParser of(String format) {
        var patternBuilder = new StringBuilder();
        var fieldGroupMap = new HashMap<String, Integer>();
        var matcher = fieldPattern.matcher(format);
        var pos = 0;
        var group = 1;
        while (true) {
            var found = matcher.find(pos);
            if (!found) {
                String tail = format.substring(pos);
                appendLiteral(patternBuilder, tail);
                break;
            }

            String head = format.substring(pos, matcher.start());
            appendLiteral(patternBuilder, head);

            String name = matcher.group(1);
            fieldGroupMap.put(name, group++);
            patternBuilder.append("(.+?)");

            pos = matcher.end();
        }

        return new CombinedLogParser(Pattern.compile(patternBuilder.toString()), fieldGroupMap);
    }

    private static void appendLiteral(
        @Nonnull StringBuilder patternBuilder, 
        @Nonnull String literal
    ) {
        if (literal.length() == 0) {
            return;
        }

        patternBuilder.append("\\Q")
            .append(literal
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\'", "\'")
                .replace("\\\\", "\\"))
            .append("\\E");
    }

}
