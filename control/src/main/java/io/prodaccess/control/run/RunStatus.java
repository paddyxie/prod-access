package io.prodaccess.control.run;

/** SCRIPT: QUEUED -> RUNNING -> terminal. CALL: RUNNING -> SUCCEEDED | FAILED. */
public enum RunStatus {
    QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED, TIMED_OUT, LOST
}
