package io.prodaccess.control.task;

import io.prodaccess.control.web.ApiException;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Derives the tool set a script uses from {@code api.<role>.<tool>} references.
 * Conservative on purpose: any other use of {@code api} (aliasing, computed access) is rejected, so the
 * derived set is complete. The runtime enforces the same set independently.
 * It is a lexical scan, not a parser: an {@code api.x.y} inside a string literal also counts.
 */
final class ToolRefAnalyzer {
    private static final Pattern COMMENTS = Pattern.compile("//[^\\n]*|/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern REF = Pattern.compile("(?<![\\w$.])api\\.([A-Za-z_$][\\w$]*)\\.([A-Za-z_$][\\w$]*)");
    private static final Pattern ANY_API = Pattern.compile("(?<![\\w$.])api(?![\\w$])");

    private ToolRefAnalyzer() {}

    static Set<String> analyze(String script) {
        String code = COMMENTS.matcher(script).replaceAll(" ");
        Set<String> tools = new TreeSet<>();
        Matcher m = REF.matcher(code);
        int accounted = 0;
        while (m.find()) {
            tools.add(m.group(1) + "." + m.group(2));
            accounted++;
        }
        int total = 0;
        Matcher any = ANY_API.matcher(code);
        while (any.find()) total++;
        if (total != accounted) {
            throw ApiException.bad("`api` may only be used as api.<role>.<tool>(...); aliasing or computed access is not supported");
        }
        return tools;
    }
}
