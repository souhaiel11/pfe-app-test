package com.pfe.devsecops.dto;

/**
 * API-facing representation of a task status.
 *
 * <p>This enum is intentionally independent from the persistence layer: it does not
 * import, extend or reference the JPA entity or any nested persistence type. It
 * mirrors every value of the persistent task status domain so that the public API
 * contract keeps the same closed value domain (and therefore the same request
 * deserialization/rejection behavior) as before.</p>
 */
public enum TaskStatusDTO {
    TODO,
    IN_PROGRESS,
    DONE,
    CANCELLED
}
