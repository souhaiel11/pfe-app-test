package com.pfe.devsecops.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;

/**
 * Persistence-decoupled representation of a task used at the HTTP boundary.
 *
 * <p>This type intentionally contains no persistence imports, annotations or
 * references to persistence entities. The task status is carried as a plain
 * String whose accepted values are the members declared by TaskStatus; the
 * service layer performs the explicit, type-safe conversion to and from the
 * persistent status representation.</p>
 *
 * <p>The status field additionally tracks whether it was actually present in
 * the incoming payload, so the service layer can distinguish an omitted
 * status (leave the current value untouched) from an explicit null status.</p>
 *
 * <p>Instances are created either with the no-arg constructor plus setters
 * (used by the JSON binder) or through {@link #builder()}, which provides a
 * fluent construction API for all fields.</p>
 */
public class TaskDTO {

    private Long id;

    private String title;

    private String description;

    /** Task status name: one of the members declared by TaskStatus. */
    private String status;

    /**
     * True only when the status property was explicitly supplied (the setter is
     * not invoked by the JSON binder for an absent property). Never serialized.
     */
    private boolean statusPresent;

    private Integer priority;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Long userId;

    public TaskDTO() {
        // Default constructor for JSON deserialization.
    }

    /**
     * @return a new fluent builder for this type.
     */
    public static Builder builder() {
        return new Builder();
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
        this.statusPresent = true;
    }

    /**
     * @return true when the status property was explicitly provided by the caller.
     */
    @JsonIgnore
    public boolean isStatusPresent() {
        return statusPresent;
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

    /**
     * Fluent builder replacing the former wide all-args constructor. Supplying
     * the status through {@link #status(String)} marks the status as explicitly
     * present, exactly like the setter does.
     */
    public static final class Builder {

        private final TaskDTO instance = new TaskDTO();

        private Builder() {
            // Instantiated through TaskDTO.builder().
        }

        public Builder id(Long id) {
            instance.setId(id);
            return this;
        }

        public Builder title(String title) {
            instance.setTitle(title);
            return this;
        }

        public Builder description(String description) {
            instance.setDescription(description);
            return this;
        }

        public Builder status(String status) {
            instance.setStatus(status);
            return this;
        }

        public Builder priority(Integer priority) {
            instance.setPriority(priority);
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            instance.setCreatedAt(createdAt);
            return this;
        }

        public Builder updatedAt(LocalDateTime updatedAt) {
            instance.setUpdatedAt(updatedAt);
            return this;
        }

        public Builder userId(Long userId) {
            instance.setUserId(userId);
            return this;
        }

        public TaskDTO build() {
            return instance;
        }
    }
}
