package com.pfe.devsecops.service;

import com.pfe.devsecops.model.Task;
import com.pfe.devsecops.model.User;
import com.pfe.devsecops.repository.TaskRepository;
import com.pfe.devsecops.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    // Constants for validation messages
    private static final String ERROR_TASK_NULL = "ERROR: task is null";
    private static final String ERROR_TITLE_REQUIRED = "ERROR: title required";
    private static final String ERROR_DESCRIPTION_REQUIRED = "ERROR: description required";
    private static final String ERROR_PRIORITY_INVALID = "ERROR: priority invalid";
    private static final String ADMIN_ROLE = "ADMIN";
    private static final String USER_ROLE = "USER";

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

    // Constructor injection instead of @Autowired field injection
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
    // FileInputStream wrapped with try-with-resources
    // ============================================================
    public String readTaskConfig(String configPath) {
        StringBuilder content = new StringBuilder();
        try (FileInputStream fis = new FileInputStream(configPath)) {
            int ch;
            while ((ch = fis.read()) != -1) {
                content.append((char) ch);
            }
        } catch (IOException e) {
            log.warn("Error reading config: {}", e.getMessage());
        }
        return content.toString();
    }

    // ============================================================
    // Task Validation Helper
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

    // ============================================================
    // Role-based Action Handlers
    // ============================================================
    private String handleAdminCreate(Task task, boolean isUrgent, boolean isBulk) {
        if (isBulk && isUrgent) {
            task.setPriority(10);
            task.setStatus(Task.TaskStatus.IN_PROGRESS);
            taskRepository.save(task);
            log.info("Bulk urgent admin create: {}", task.getTitle());
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
    }

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

    private String handleAdminUpdate(Task task) {
        task.setUpdatedAt(LocalDateTime.now());
        taskRepository.save(task);
        return "ADMIN_UPDATE";
    }

    private String handleUserUpdate(Task task, boolean isUrgent) {
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

    // ============================================================
    // VULNERABILITY S6 — Refactored for reduced complexity
    // ============================================================
    public String processTaskWorkflow(Task task, String action, String userRole, boolean isUrgent, boolean isBulk) {
        String validationError = validateTask(task);
        if (validationError != null) {
            return validationError;
        }

        switch (action) {
            case "CREATE":
                if (ADMIN_ROLE.equals(userRole)) {
                    return handleAdminCreate(task, isUrgent, isBulk);
                } else if (USER_ROLE.equals(userRole)) {
                    return handleUserCreate(task, isUrgent);
                } else {
                    return "ERROR: unknown role";
                }

            case "UPDATE":
                if (ADMIN_ROLE.equals(userRole)) {
                    return handleAdminUpdate(task);
                } else if (USER_ROLE.equals(userRole)) {
                    return handleUserUpdate(task, isUrgent);
                } else {
                    return "ERROR: unknown role";
                }

            case "DELETE":
                if (ADMIN_ROLE.equals(userRole)) {
                    taskRepository.deleteById(task.getId());
                    return "ADMIN_DELETE";
                } else {
                    return "ERROR: insufficient permissions";
                }

            case "COMPLETE":
                task.setStatus(Task.TaskStatus.DONE);
                task.setUpdatedAt(LocalDateTime.now());
                taskRepository.save(task);
                return "TASK_COMPLETED";

            case "CANCEL":
                task.setStatus(Task.TaskStatus.CANCELLED);
                task.setUpdatedAt(LocalDateTime.now());
                taskRepository.save(task);
                return "TASK_CANCELLED";

            default:
                return "UNKNOWN_ACTION";
        }
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