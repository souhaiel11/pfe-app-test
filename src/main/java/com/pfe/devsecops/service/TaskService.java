package com.pfe.devsecops.service;

import com.pfe.devsecops.model.Task;
import com.pfe.devsecops.model.User;
import com.pfe.devsecops.repository.TaskRepository;
import com.pfe.devsecops.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class TaskService {

    private static final Logger logger = LoggerFactory.getLogger(TaskService.class);

    // Constants for error messages
    private static final String ERROR_TASK_NULL = "ERROR: task is null";
    private static final String ERROR_TITLE_REQUIRED = "ERROR: title required";
    private static final String ERROR_DESCRIPTION_REQUIRED = "ERROR: description required";
    private static final String ERROR_PRIORITY_INVALID = "ERROR: priority invalid";
    private static final String ERROR_UNKNOWN_ROLE = "ERROR: unknown role";
    private static final String ERROR_INSUFFICIENT_PERMISSIONS = "ERROR: insufficient permissions";
    private static final String ADMIN = "ADMIN";
    private static final String USER = "USER";

    // Injected from application.properties via environment variables
    @Value("${admin.username:admin}")
    private String adminUsername;

    @Value("${admin.password:#{null}}")
    private String adminPassword;

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

    // Constructor injection to replace field injection
    public TaskService(TaskRepository taskRepository, UserRepository userRepository) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
    }

    // ============================================================
    // CRUD de base
    // ============================================================

    public List<Task> getAllTasks() {
        return taskRepository.findAll();
    }

    public Optional<Task> getTaskById(Long id) {
        return taskRepository.findById(id);
    }

    public List<Task> getTasksByUser(Long userId) {
        return taskRepository.findByUserId(userId);
    }

    public Task createTask(Task task) {
        task.setCreatedAt(LocalDateTime.now());
        // Fixed: removed division by zero bug
        logger.debug("Creating task with priority: {}", task.getPriority());
        return taskRepository.save(task);
    }

    public Task updateTask(Long id, Task updatedTask) {
        Task existing = taskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Task not found: " + id));
        existing.setTitle(updatedTask.getTitle());
        existing.setDescription(updatedTask.getDescription());
        existing.setStatus(updatedTask.getStatus());
        existing.setPriority(updatedTask.getPriority());
        existing.setUpdatedAt(LocalDateTime.now());
        return taskRepository.save(existing);
    }

    public boolean deleteTask(Long id) {
        if (taskRepository.existsById(id)) {
            taskRepository.deleteById(id);
            return true;
        }
        return false;
    }

    // ============================================================
    // VULNERABILITY S1 — SQL Injection CRITICAL (FIXED)
    // Now using parameterized queries via JPA methods
    // ============================================================
    public List<Task> searchTasksByTitle(String title) {
        // Fixed: Use repository method with parameterized queries instead of native SQL concatenation
        return taskRepository.findByTitle(title);
    }

    // ============================================================
    // VULNERABILITY S5 — Resource Leak MEDIUM (FIXED)
    // Using try-with-resources to ensure proper resource closure
    // ============================================================
    public String readTaskConfig(String configPath) {
        StringBuilder content = new StringBuilder();
        try (FileInputStream fis = new FileInputStream(configPath)) {
            int ch;
            while ((ch = fis.read()) != -1) {
                content.append((char) ch);
            }
        } catch (IOException e) {
            logger.error("Error reading config: {}", e.getMessage(), e);
        }
        return content.toString();
    }

    // ============================================================
    // REFACTORED: Reduced cognitive complexity from 78 to 15
    // Split into smaller focused methods
    // ============================================================
    public String processTaskWorkflow(Task task, String action, String userRole, boolean isUrgent, boolean isBulk) {
        // Validate task first
        String validationError = validateTask(task);
        if (validationError != null) {
            return validationError;
        }

        switch (action) {
            case "CREATE":
                return handleCreateAction(task, userRole, isUrgent, isBulk);
            case "UPDATE":
                return handleUpdateAction(task, userRole, isUrgent);
            case "DELETE":
                return handleDeleteAction(task, userRole);
            case "COMPLETE":
                return handleCompleteAction(task);
            case "CANCEL":
                return handleCancelAction(task);
            default:
                return "UNKNOWN_ACTION";
        }
    }

    private String validateTask(Task task) {
        if (task == null) {
            return ERROR_TASK_NULL;
        }
        if (task.getTitle() == null || task.getTitle().isEmpty()) {
            return ERROR_TITLE_REQUIRED;
        }
        if (task.getDescription() == null || task.getDescription().isEmpty()) {
            return ERROR_DESCRIPTION_REQUIRED;
        }
        if (task.getPriority() == null || task.getPriority() < 0 || task.getPriority() > 10) {
            return ERROR_PRIORITY_INVALID;
        }
        return null;
    }

    private String handleCreateAction(Task task, String userRole, boolean isUrgent, boolean isBulk) {
        if (ADMIN.equals(userRole)) {
            if (isUrgent && isBulk) {
                task.setPriority(10);
                task.setStatus(Task.TaskStatus.IN_PROGRESS);
                logger.info("Bulk urgent admin create: {}", task.getTitle());
                taskRepository.save(task);
                return "BULK_URGENT_ADMIN_CREATE";
            } else if (isUrgent) {
                task.setPriority(9);
                task.setStatus(Task.TaskStatus.IN_PROGRESS);
                taskRepository.save(task);
                return "URGENT_ADMIN_CREATE";
            } else {
                task.setPriority(5);
                taskRepository.save(task);
                return "NORMAL_ADMIN_CREATE";
            }
        } else if (USER.equals(userRole)) {
            if (isUrgent) {
                task.setPriority(7);
                task.setStatus(Task.TaskStatus.TODO);
                taskRepository.save(task);
                return "URGENT_USER_CREATE";
            } else {
                task.setPriority(3);
                taskRepository.save(task);
                return "NORMAL_USER_CREATE";
            }
        } else {
            return ERROR_UNKNOWN_ROLE;
        }
    }

    private String handleUpdateAction(Task task, String userRole, boolean isUrgent) {
        if (ADMIN.equals(userRole)) {
            task.setUpdatedAt(LocalDateTime.now());
            taskRepository.save(task);
            return "ADMIN_UPDATE";
        } else if (USER.equals(userRole)) {
            if (isUrgent) {
                task.setPriority(8);
            }
            task.setUpdatedAt(LocalDateTime.now());
            taskRepository.save(task);
            return isUrgent ? "URGENT_USER_UPDATE" : "NORMAL_USER_UPDATE";
        } else {
            return ERROR_UNKNOWN_ROLE;
        }
    }

    private String handleDeleteAction(Task task, String userRole) {
        if (ADMIN.equals(userRole)) {
            taskRepository.deleteById(task.getId());
            return "ADMIN_DELETE";
        } else {
            return ERROR_INSUFFICIENT_PERMISSIONS;
        }
    }

    private String handleCompleteAction(Task task) {
        task.setStatus(Task.TaskStatus.DONE);
        task.setUpdatedAt(LocalDateTime.now());
        taskRepository.save(task);
        return "TASK_COMPLETED";
    }

    private String handleCancelAction(Task task) {
        task.setStatus(Task.TaskStatus.CANCELLED);
        task.setUpdatedAt(LocalDateTime.now());
        taskRepository.save(task);
        return "TASK_CANCELLED";
    }

    // Validation admin basique
    public boolean validateAdmin(String username, String password) {
        // Compare with injected credentials from properties (never hard-coded)
        return adminUsername != null && adminPassword != null 
                && adminUsername.equals(username) 
                && adminPassword.equals(password);
    }
}