package io.prodaccess.control.run;

/** Submit -> (write ? PENDING_APPROVAL -> QUEUED | REJECTED : QUEUED) -> RUNNING -> terminal. */
public enum RunStatus {
    PENDING_APPROVAL, QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED, TIMED_OUT, REJECTED, LOST
}
