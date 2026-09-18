package com.pfe.devsecops.dto;

import java.time.LocalDateTime;

/**
 * Persistence-decoupled representation of a task used at the HTTP boundary.
 *
 * <p>This type intentionally contains no persistence imports, annotations or
 * references to persistence entities. The task status is carried as a plain
 * String whose accepted values are TODO, IN_PROGRESS, DONE and CANCELLED;
 * the service layer performs the explicit, type-safe conversion to and from
 * the persistent status representation.</p>
 */
public class TaskDTO {

    private Long id;

    private String title;

    private String description;

    /** Task status name: TODO, IN_PROGRESS, DONE or CANCELLED. */
    private String status;

    private Integer priority;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Long userId;

    public TaskDTO() {
        // Default constructor for JSON deserialization.
    }

    public TaskDTO(Long id,
                   String title,
                   String description,
                   String status,
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
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
