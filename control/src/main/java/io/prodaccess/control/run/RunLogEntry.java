package io.prodaccess.control.run;

/** A line of a run's log: a step boundary, a log call or an error. */
public record RunLogEntry(long id, String runId, long ts, String kind, String lvl, String message) {}
