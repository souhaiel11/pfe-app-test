package com.pfe.devsecops.dto;

import com.pfe.devsecops.model.Task;

import java.time.LocalDate;

/**
 * Plain data transfer object representing a Task resource exposed through the
 * REST API. This class intentionally contains no JPA annotations and no
 * entity relationships so that persistence-layer implementation details are
 * never leaked to API consumers.
 */
public class TaskDTO {

    private Long id;
    private String title;
    private String description;
    private String status;
    private LocalDate dueDate;

    public TaskDTO() {
    }

    public TaskDTO(Long id, String title, String description, String status, LocalDate dueDate) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.status = status;
        this.dueDate = dueDate;
    }

    /**
     * Maps a persisted {@link Task} entity into a {@link TaskDTO} suitable
     * for returning from the REST API layer.
     *
     * @param task the entity to map, must not be {@code null}
     * @return a populated TaskDTO instance
     */
    public static TaskDTO fromEntity(Task task) {
        if (task == null) {
            return null;
        }
        TaskDTO dto = new TaskDTO();
        dto.setId(task.getId());
        dto.setTitle(task.getTitle());
        dto.setDescription(task.getDescription());
        dto.setStatus(task.getStatus());
        dto.setDueDate(task.getDueDate());
        return dto;
    }

    /**
     * Maps this {@link TaskDTO} into a new {@link Task} entity instance so
     * that controllers can accept the API-facing DTO while still delegating
     * persistence-related work to the service layer using the entity type.
     * The identifier is intentionally copied as-is; callers responsible for
     * update flows should overwrite it with the path-resolved identifier
     * before invoking the service layer where applicable.
     *
     * @return a new Task entity populated from this DTO's fields
     */
    public Task toEntity() {
        Task task = new Task();
        task.setId(this.id);
        task.setTitle(this.title);
        task.setDescription(this.description);
        task.setStatus(this.status);
        task.setDueDate(this.dueDate);
        return task;
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

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }
}
