package com.pfe.devsecops.dto;

import java.time.LocalDateTime;

/**
 * API-facing representation of a task.
 * <p>
 * This type is intentionally decoupled from the persistence layer: it does not
 * import, reference or reuse any JPA entity class, nested entity type or
 * persistence annotation. The task status is represented by the independent
 * DTO-level enum {@link TaskStatusDTO} and the task owner is represented only
 * by its identifier.
 */
public class TaskDTO {

    private Long id;

    private String title;

    private String description;

    private TaskStatusDTO status;

    private Integer priority;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** Identifier of the owning user, or {@code null} when the task has no owner. */
    private Long userId;

    public TaskDTO() {
        // default constructor for serialization frameworks
    }

    public TaskDTO(Long id,
                   String title,
                   String description,
                   TaskStatusDTO status,
                   Integer priority,
                   LocalDateTime createdAt,
                   LocalDateTime updatedAt,
                   Long userId) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.status = status;
        this.priority = priority;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.userId = userId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}
