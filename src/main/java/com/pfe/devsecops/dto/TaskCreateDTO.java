package com.pfe.devsecops.dto;

/**
 * API-facing request payload for task creation.
 *
 * <p>This type is intentionally independent from the persistence layer: it does not
 * reference the JPA entity, its nested types or any persistence annotation. The task
 * status is represented by the DTO-level {@link TaskStatusDTO} enum and the owning user
 * is represented only by its identifier, which is resolved by the controller through the
 * project's user repository before the entity is persisted.</p>
 */
public class TaskCreateDTO {

    private String title;

    private String description;

    private TaskStatusDTO status;

    private Integer priority;

    /**
     * Identifier of the owning user. May be {@code null}, in which case the created task
     * is left without an owner, matching the existing nullable association semantics.
     */
    private Long userId;

    public TaskCreateDTO() {
        // Default constructor required for JSON deserialization.
    }

    public TaskCreateDTO(String title,
                         String description,
                         TaskStatusDTO status,
                         Integer priority,
                         Long userId) {
        this.title = title;
        this.description = description;
        this.status = status;
        this.priority = priority;
        this.userId = userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public TaskStatusDTO getStatus() {
        return status;
    }

    public void setStatus(TaskStatusDTO status) {
        this.status = status;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}
