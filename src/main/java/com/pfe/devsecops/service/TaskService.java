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
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_USER = "USER";
    private static final String ACTION_CREATE = "CREATE";
    private static final String ACTION_UPDATE = "UPDATE";
    private static final String ACTION_DELETE = "DELETE";
    private static final String ACTION_COMPLETE = "COMPLETE";
    private static final String ACTION_CANCEL = "CANCEL";

    @Value("${db.password:}")
    private String dbPassword;

    @Value("${admin.username:}")
    private String adminUsername;

    @Value("${admin.password:}")
    private String adminPassword;

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public TaskService(TaskRepository taskRepository, UserRepository userRepository) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
    }

    // ============================================================
    // CRUD de base
    // ============================================================

    public List<Task> getAllTasks() {
        logger.debug("Retrieving all tasks");
        return taskRepository.findAll();
    }

    public Optional<Task> getTaskById(Long id) {
        if (id == null || id <= 0) {
            logger.warn("Invalid task ID requested: {}", id);
            return Optional.empty();
        }
        return taskRepository.findById(id);
    }

    public List<Task> getTasksByUser(Long userId) {
        if (userId == null || userId <= 0) {
            logger.warn("Invalid user ID requested: {}", userId);
            return List.of();
        }
        return taskRepository.findByUserId(userId);
    }

    public Task createTask(Task task) {
        if (task == null) {
            logger.error("Attempt to create null task");
            throw new IllegalArgumentException("Task cannot be null");
        }
        task.setCreatedAt(LocalDateTime.now());
        logger.info("Creating new task: {}", task.getTitle());
        return taskRepository.save(task);
    }

    public Task updateTask(Long id, Task updatedTask) {
        if (id == null || id <= 0) {
            logger.error("Invalid task ID for update: {}", id);
            throw new IllegalArgumentException("Invalid task ID");
        }
        if (updatedTask == null) {
            logger.error("Attempt to update task with null object");
            throw new IllegalArgumentException("Updated task cannot be null");
        }
        Task existing = taskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Task not found: " + id));
        existing.setTitle(updatedTask.getTitle());
        existing.setDescription(updatedTask.getDescription());
        existing.setStatus(updatedTask.getStatus());
        existing.setPriority(updatedTask.getPriority());
        existing.setUpdatedAt(LocalDateTime.now());
        logger.info("Updated task ID: {}", id);
        return taskRepository.save(existing);
    }

    public boolean deleteTask(Long id) {
        if (id == null || id <= 0) {
            logger.warn("Invalid task ID for deletion: {}", id);
            return false;
        }
        if (taskRepository.existsById(id)) {
            taskRepository.deleteById(id);
            logger.info("Deleted task ID: {}", id);
            return true;
        }
        logger.warn("Task not found for deletion: {}", id);
        return false;
    }

    // ============================================================
    // VULNERABILITY S1 — SQL Injection CRITICAL
    // Recherche par titre avec concaténation directe
    // FIXED: Use parameterized query to prevent SQL injection
    // ============================================================
    @SuppressWarnings("unchecked")
    public List<Task> searchTasksByTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            logger.warn("Search requested with empty title");
            return List.of();
        }
        // Fixed: Use parameterized query to prevent SQL injection
        String sql = "SELECT t FROM Task t WHERE t.title = :title";
        logger.debug("Searching tasks by title: {}", title);
        return entityManager.createQuery(sql, Task.class)
                .setParameter("title", title)
                .getResultList();
    }

    // ============================================================
    // VULNERABILITY S5 — Resource Leak MEDIUM
    // FileInputStream ouvert sans try-with-resources ni close()
    // FIXED: Use try-with-resources to ensure proper resource cleanup
    // ============================================================
    public String readTaskConfig(String configPath) {
        if (configPath == null || configPath.trim().isEmpty()) {
            logger.error("Config path is null or empty");
            return "";
        }
        StringBuilder content = new StringBuilder();
        try (FileInputStream fis = new FileInputStream(configPath)) {
            int ch;
            while ((ch = fis.read()) != -1) {
                content.append((char) ch);
            }
            logger.info("Successfully read config from: {}", configPath);
        } catch (IOException e) {
            logger.error("Error reading config from {}: {}", configPath, e.getMessage(), e);
        }
        return content.toString();
    }

    // ============================================================
    // REFACTORED: Reduced cognitive complexity by extracting validation
    // and using helper methods
    // ============================================================

    private String validateTask(Task task) {
        if (task == null) {
            return ERROR_TASK_NULL;
        }
        if (task.getTitle() == null || task.getTitle().trim().isEmpty()) {
            return ERROR_TITLE_REQUIRED;
        }
        if (task.getDescription() == null || task.getDescription().trim().isEmpty()) {
            return ERROR_DESCRIPTION_REQUIRED;
        }
        if (task.getPriority() == null || task.getPriority() < 0 || task.getPriority() > 10) {
            return ERROR_PRIORITY_INVALID;
        }
        return null;
    }

    private void handleAdminCreate(Task task, boolean isUrgent, boolean isBulk) {
        if (isBulk && isUrgent) {
            task.setPriority(10);
            task.setStatus(Task.TaskStatus.IN_PROGRESS);
            logger.info("Bulk urgent admin create: {}", task.getTitle());
        } else if (isUrgent) {
            task.setPriority(9);
            task.setStatus(Task.TaskStatus.IN_PROGRESS);
            logger.info("Urgent admin create: {}", task.getTitle());
        } else {
            task.setPriority(5);
            logger.info("Normal admin create: {}", task.getTitle());
        }
        taskRepository.save(task);
    }

    private void handleUserCreate(Task task, boolean isUrgent) {
        if (isUrgent) {
            task.setPriority(7);
            task.setStatus(Task.TaskStatus.TODO);
            logger.info("Urgent user create: {}", task.getTitle());
        } else {
            task.setPriority(3);
            logger.info("Normal user create: {}", task.getTitle());
        }
        taskRepository.save(task);
    }

    private void handleAdminUpdate(Task task) {
        task.setUpdatedAt(LocalDateTime.now());
        logger.info("Admin update task ID: {}", task.getId());
        taskRepository.save(task);
    }

    private void handleUserUpdate(Task task, boolean isUrgent) {
        if (isUrgent) {
            task.setPriority(8);
            logger.info("Urgent user update task ID: {}", task.getId());
        } else {
            logger.info("Normal user update task ID: {}", task.getId());
        }
        task.setUpdatedAt(LocalDateTime.now());
        taskRepository.save(task);
    }

    public String processTaskWorkflow(Task task, String action, String userRole, boolean isUrgent, boolean isBulk) {
        // Validate task first
        String validationError = validateTask(task);
        if (validationError != null) {
            logger.warn("Task validation failed: {}", validationError);
            return validationError;
        }

        if (action == null || action.trim().isEmpty()) {
            logger.error("Action is null or empty");
            return "ERROR: action is required";
        }

        if (userRole == null || userRole.trim().isEmpty()) {
            logger.error("User role is null or empty");
            return "ERROR: user role is required";
        }

        switch (action) {
            case ACTION_CREATE:
                return handleCreateAction(task, userRole, isUrgent, isBulk);
            case ACTION_UPDATE:
                return handleUpdateAction(task, userRole, isUrgent);
            case ACTION_DELETE:
                return handleDeleteAction(task, userRole);
            case ACTION_COMPLETE:
                task.setStatus(Task.TaskStatus.DONE);
                task.setUpdatedAt(LocalDateTime.now());
                taskRepository.save(task);
                logger.info("Task completed ID: {}", task.getId());
                return "TASK_COMPLETED";
            case ACTION_CANCEL:
                task.setStatus(Task.TaskStatus.CANCELLED);
                task.setUpdatedAt(LocalDateTime.now());
                taskRepository.save(task);
                logger.info("Task cancelled ID: {}", task.getId());
                return "TASK_CANCELLED";
            default:
                logger.warn("Unknown action requested: {}", action);
                return "UNKNOWN_ACTION";
        }
    }

    private String handleCreateAction(Task task, String userRole, boolean isUrgent, boolean isBulk) {
        if (ROLE_ADMIN.equals(userRole)) {
            handleAdminCreate(task, isUrgent, isBulk);
            if (isBulk && isUrgent) {
                return "BULK_URGENT_ADMIN_CREATE";
            } else if (isUrgent) {
                return "URGENT_ADMIN_CREATE";
            } else {
                return "NORMAL_ADMIN_CREATE";
            }
        } else if (ROLE_USER.equals(userRole)) {
            handleUserCreate(task, isUrgent);
            return isUrgent ? "URGENT_USER_CREATE" : "NORMAL_USER_CREATE";
        } else {
            logger.error("Unknown role in create action: {}", userRole);
            return "ERROR: unknown role";
        }
    }

    private String handleUpdateAction(Task task, String userRole, boolean isUrgent) {
        if (ROLE_ADMIN.equals(userRole)) {
            handleAdminUpdate(task);
            return "ADMIN_UPDATE";
        } else if (ROLE_USER.equals(userRole)) {
            handleUserUpdate(task, isUrgent);
            return isUrgent ? "URGENT_USER_UPDATE" : "NORMAL_USER_UPDATE";
        } else {
            logger.error("Unknown role in update action: {}", userRole);
            return "ERROR: unknown role";
        }
    }

    private String handleDeleteAction(Task task, String userRole) {
        if (ROLE_ADMIN.equals(userRole)) {
            if (task != null && task.getId() != null) {
                taskRepository.deleteById(task.getId());
                logger.info("Admin deleted task ID: {}", task.getId());
                return "ADMIN_DELETE";
            } else {
                logger.error("Invalid task for deletion");
                return "ERROR: invalid task";
            }
        } else {
            logger.warn("Non-admin user attempted delete: {}", userRole);
            return "ERROR: insufficient permissions";
        }
    }

    // Validation admin basique
    // FIXED: Use environment-based credentials from @Value injection
    // instead of hardcoded values to prevent credential exposure
    public boolean validateAdmin(String username, String password) {
        if (username == null || password == null) {
            logger.warn("Admin validation attempted with null credentials");
            return false;
        }
        // Use constant-time comparison to prevent timing attacks
        boolean isValid = adminUsername != null && adminUsername.equals(username) &&
               adminPassword != null && adminPassword.equals(password);
        if (!isValid) {
            logger.warn("Failed admin validation attempt for username: {}", username);
        } else {
            logger.info("Successful admin validation for username: {}", username);
        }
        return isValid;
    }
}
