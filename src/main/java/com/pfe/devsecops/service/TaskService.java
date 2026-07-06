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
    // VULNERABILITY S1 — SQL Injection CRITICAL
    // Recherche par titre avec concaténation directe
    // ============================================================
    @SuppressWarnings("unchecked")
    public List<Task> searchTasksByTitle(String title) {
        // Fixed: Use parameterized query to prevent SQL injection
        String sql = "SELECT t FROM Task t WHERE t.title = :title";
        return entityManager.createQuery(sql, Task.class)
                .setParameter("title", title)
                .getResultList();
    }

    // ============================================================
    // VULNERABILITY S5 — Resource Leak MEDIUM
    // FileInputStream ouvert sans try-with-resources ni close()
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
    // REFACTORED: Reduced cognitive complexity by extracting validation
    // and using helper methods
    // ============================================================

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

    private void handleAdminCreate(Task task, boolean isUrgent, boolean isBulk) {
        if (isBulk && isUrgent) {
            task.setPriority(10);
            task.setStatus(Task.TaskStatus.IN_PROGRESS);
            logger.info("Bulk urgent admin create: {}", task.getTitle());
        } else if (isUrgent) {
            task.setPriority(9);
            task.setStatus(Task.TaskStatus.IN_PROGRESS);
        } else {
            task.setPriority(5);
        }
        taskRepository.save(task);
    }

    private void handleUserCreate(Task task, boolean isUrgent) {
        if (isUrgent) {
            task.setPriority(7);
            task.setStatus(Task.TaskStatus.TODO);
        } else {
            task.setPriority(3);
        }
        taskRepository.save(task);
    }

    private void handleAdminUpdate(Task task) {
        task.setUpdatedAt(LocalDateTime.now());
        taskRepository.save(task);
    }

    private void handleUserUpdate(Task task, boolean isUrgent) {
        if (isUrgent) {
            task.setPriority(8);
        }
        task.setUpdatedAt(LocalDateTime.now());
        taskRepository.save(task);
    }

    public String processTaskWorkflow(Task task, String action, String userRole, boolean isUrgent, boolean isBulk) {
        // Validate task first
        String validationError = validateTask(task);
        if (validationError != null) {
            return validationError;
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
                return "TASK_COMPLETED";
            case ACTION_CANCEL:
                task.setStatus(Task.TaskStatus.CANCELLED);
                task.setUpdatedAt(LocalDateTime.now());
                taskRepository.save(task);
                return "TASK_CANCELLED";
            default:
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
            return "ERROR: unknown role";
        }
    }

    private String handleDeleteAction(Task task, String userRole) {
        if (ROLE_ADMIN.equals(userRole)) {
            taskRepository.deleteById(task.getId());
            return "ADMIN_DELETE";
        } else {
            return "ERROR: insufficient permissions";
        }
    }

    // Validation admin basique
    // TODO: Refactor to use DTO pattern to prevent entity exposure in REST endpoints
    public boolean validateAdmin(String username, String password) {
        // Fixed: Use environment-based credentials instead of hardcoded values
        // Credentials are injected via @Value from application.properties or environment variables
        return adminUsername != null && adminUsername.equals(username) &&
               adminPassword != null && adminPassword.equals(password);
    }
}