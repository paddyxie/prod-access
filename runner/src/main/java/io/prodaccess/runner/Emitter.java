package io.prodaccess.runner;

import io.prodaccess.common.json.Json;
import io.prodaccess.common.protocol.RunEvent;
import java.io.PrintStream;

/** The only writer of stdout: one JSON event per line. Everything else the JVM prints goes to stderr. */
final class Emitter {
    private static final int MAX_TEXT = 8 * 1024;
    private final PrintStream out;

    Emitter(PrintStream out) {
        this.out = out;
    }

    synchronized void emit(RunEvent e) {
        out.println(Json.write(e));
        out.flush();
    }

    static String clip(String s) {
        if (s == null || s.length() <= MAX_TEXT) return s;
        return s.substring(0, MAX_TEXT) + "…[truncated " + (s.length() - MAX_TEXT) + " chars]";
    }
}
