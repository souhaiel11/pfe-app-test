package com.pfe.devsecops.dto;

/**
 * API-facing request payload for updating an existing task.
 *
 * <p>Intentionally decoupled from the persistence layer: it references no JPA
 * entity, no nested entity type and no persistence annotations. The owning user
 * is deliberately not exposed here, because the update operation leaves the
 * existing task/user association untouched.</p>
 */
public class TaskUpdateDTO {

    private String title;

    private String description;

    private TaskStatusDTO status;

    private Integer priority;

    public TaskUpdateDTO() {
        // default constructor for deserialization
    }

    public TaskUpdateDTO(String title, String description, TaskStatusDTO status, Integer priority) {
        this.title = title;
        this.description = description;
        this.status = status;
        this.priority = priority;
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
}
