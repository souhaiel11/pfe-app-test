package com.pfe.devsecops.service;

import com.pfe.devsecops.model.Task;
import com.pfe.devsecops.model.User;
import com.pfe.devsecops.repository.TaskRepository;
import com.pfe.devsecops.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    // Error message constants
    private static final String ERROR_TASK_NULL = "ERROR: task is null";
    private static final String ERROR_TITLE_REQUIRED = "ERROR: title required";
    private static final String ERROR_DESCRIPTION_REQUIRED = "ERROR: description required";
    private static final String ERROR_PRIORITY_INVALID = "ERROR: priority invalid";
    private static final String ERROR_UNKNOWN_ROLE = "ERROR: unknown role";
    private static final String ERROR_INSUFFICIENT_PERMISSIONS = "ERROR: insufficient permissions";

    // Role constants
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_USER = "USER";

    // Action constants
    private static final String ACTION_CREATE = "CREATE";
    private static final String ACTION_UPDATE = "UPDATE";
    private static final String ACTION_DELETE = "DELETE";
    private static final String ACTION_COMPLETE = "COMPLETE";
    private static final String ACTION_CANCEL = "CANCEL";

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

    // Constructor injection
    @Autowired
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
        // SQL Injection : le paramètre title est directement concatené dans la query
        String sql = "SELECT * FROM tasks WHERE title = '" + title + "'";
        return entityManager.createNativeQuery(sql, Task.class).getResultList();
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
            log.error("Error reading config: {}", e.getMessage());
        }
        return content.toString();
    }

    // ============================================================
    // VULNERABILITY S6 — Méthode trop longue + complexité cognitive élevée
    // VULNERABILITY S7 — Duplication de code (bloc validation répété 3x)
    // ============================================================

    /**
     * Validates task basic properties
     */
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

    /**
     * Handles CREATE action
     */
    private String handleCreateAction(Task task, String userRole, boolean isUrgent, boolean isBulk) {
        if (ROLE_ADMIN.equals(userRole)) {
            return handleAdminCreate(task, isUrgent, isBulk);
        } else if (ROLE_USER.equals(userRole)) {
            return handleUserCreate(task, isUrgent);
        }
        return ERROR_UNKNOWN_ROLE;
    }

    /**
     * Handles admin CREATE action
     */
    private String handleAdminCreate(Task task, boolean isUrgent, boolean isBulk) {
        if (isUrgent) {
            if (isBulk) {
                task.setPriority(10);
                task.setStatus(Task.TaskStatus.IN_PROGRESS);
                taskRepository.save(task);
                log.info("Bulk urgent admin create: {}", task.getTitle());
                return "BULK_URGENT_ADMIN_CREATE";
            } else {
                task.setPriority(9);
                task.setStatus(Task.TaskStatus.IN_PROGRESS);
                taskRepository.save(task);
                return "URGENT_ADMIN_CREATE";
            }
        } else {
            task.setPriority(5);
            taskRepository.save(task);
            return "NORMAL_ADMIN_CREATE";
        }
    }

    /**
     * Handles user CREATE action
     */
    private String handleUserCreate(Task task, boolean isUrgent) {
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
    }

    /**
     * Handles UPDATE action
     */
    private String handleUpdateAction(Task task, String userRole, boolean isUrgent) {
        if (ROLE_ADMIN.equals(userRole)) {
            task.setUpdatedAt(LocalDateTime.now());
            taskRepository.save(task);
            return "ADMIN_UPDATE";
        } else if (ROLE_USER.equals(userRole)) {
            if (isUrgent) {
                task.setPriority(8);
                task.setUpdatedAt(LocalDateTime.now());
                taskRepository.save(task);
                return "URGENT_USER_UPDATE";
            } else {
                task.setUpdatedAt(LocalDateTime.now());
                taskRepository.save(task);
                return "NORMAL_USER_UPDATE";
            }
        }
        return ERROR_UNKNOWN_ROLE;
    }

    /**
     * Handles DELETE action
     */
    private String handleDeleteAction(Task task, String userRole) {
        if (!ROLE_ADMIN.equals(userRole)) {
            return ERROR_INSUFFICIENT_PERMISSIONS;
        }
        taskRepository.deleteById(task.getId());
        return "ADMIN_DELETE";
    }

    /**
     * Main workflow processor
     */
    public String processTaskWorkflow(Task task, String action, String userRole, boolean isUrgent, boolean isBulk) {
        // Validate task first
        String validationError = validateTask(task);
        if (validationError != null) {
            return validationError;
        }

        // Route to appropriate action handler
        if (ACTION_CREATE.equals(action)) {
            return handleCreateAction(task, userRole, isUrgent, isBulk);
        } else if (ACTION_UPDATE.equals(action)) {
            return handleUpdateAction(task, userRole, isUrgent);
        } else if (ACTION_DELETE.equals(action)) {
            return handleDeleteAction(task, userRole);
        } else if (ACTION_COMPLETE.equals(action)) {
            task.setStatus(Task.TaskStatus.DONE);
            task.setUpdatedAt(LocalDateTime.now());
            taskRepository.save(task);
            return "TASK_COMPLETED";
        } else if (ACTION_CANCEL.equals(action)) {
            task.setStatus(Task.TaskStatus.CANCELLED);
            task.setUpdatedAt(LocalDateTime.now());
            taskRepository.save(task);
            return "TASK_CANCELLED";
        }
        return "UNKNOWN_ACTION";
    }

    // Validation admin basique
    public boolean validateAdmin(String username, String password) {
        String adminUsername = System.getenv("ADMIN_USERNAME");
        String adminPassword = System.getenv("ADMIN_PASSWORD");
        
        if (adminUsername == null || adminPassword == null) {
            log.warn("Admin credentials not configured in environment variables");
            return false;
        }
        
        return adminUsername.equals(username) && adminPassword.equals(password);
    }
}