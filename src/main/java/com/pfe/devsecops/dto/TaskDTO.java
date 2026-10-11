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
 * <p>Instances are built either through the no-args constructor plus setters
 * (the path used by the JSON binder) or through {@link #builder()}, which
 * applies the very same setters and therefore keeps identical semantics.</p>
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
     * @return a new fluent builder for this DTO.
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
     * Fluent builder that populates a {@link TaskDTO} exclusively through its
     * public setters, so the status presence tracking behaves exactly as it
     * does for programmatic setter usage.
     */
    public static final class Builder {

        private final TaskDTO target = new TaskDTO();

        private Builder() {
            // Instantiated through TaskDTO.builder().
        }

        public Builder id(Long id) {
            target.setId(id);
            return this;
        }

        public Builder title(String title) {
            target.setTitle(title);
            return this;
        }

        public Builder description(String description) {
            target.setDescription(description);
            return this;
        }

        /**
         * Supplies the status name and marks it as explicitly provided.
         *
         * @param status one of the members declared by TaskStatus.
         */
        public Builder status(String status) {
            target.setStatus(status);
            return this;
        }

        public Builder priority(Integer priority) {
            target.setPriority(priority);
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            target.setCreatedAt(createdAt);
            return this;
        }

        public Builder updatedAt(LocalDateTime updatedAt) {
            target.setUpdatedAt(updatedAt);
            return this;
        }

        public Builder userId(Long userId) {
            target.setUserId(userId);
            return this;
        }

        public TaskDTO build() {
            return target;
        }
    }
}
