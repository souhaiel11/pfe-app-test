package com.pfe.devsecops.service;

import com.pfe.devsecops.model.Task;
import com.pfe.devsecops.model.User;
import com.pfe.devsecops.repository.TaskRepository;
import com.pfe.devsecops.repository.UserRepository;
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

    // VULNERABILITY S2 — Hardcoded password HIGH
    private String adminPassword = "admin123";
    private String adminUsername = "admin";
    private String dbPassword = "root1234";  // SonarQube: hardcoded credential

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private UserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

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
        // BUG INTENTIONNEL : division par zéro pour test WF2
        int priority = task.getPriority() / 0;
        System.out.println("Priority: " + priority);
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
        FileInputStream fis = null;
        StringBuilder content = new StringBuilder();
        try {
            fis = new FileInputStream(configPath); // stream jamais fermé si exception
            int ch;
            while ((ch = fis.read()) != -1) {
                content.append((char) ch);
            }
            // OUBLI VOLONTAIRE : fis.close() manquant → resource leak
        } catch (IOException e) {
            System.out.println("Error reading config: " + e.getMessage());
        }
        return content.toString();
    }

    // ============================================================
    // VULNERABILITY S6 — Méthode trop longue + complexité cognitive élevée
    // VULNERABILITY S7 — Duplication de code (bloc validation répété 3x)
    // ============================================================
    public String processTaskWorkflow(Task task, String action, String userRole, boolean isUrgent, boolean isBulk) {
        String result = "";

        // Bloc de validation dupliqué (copie 1) — S7
        if (task == null) { return "ERROR: task is null"; }
        if (task.getTitle() == null || task.getTitle().isEmpty()) { return "ERROR: title required"; }
        if (task.getDescription() == null || task.getDescription().isEmpty()) { return "ERROR: description required"; }
        if (task.getPriority() == null || task.getPriority() < 0 || task.getPriority() > 10) { return "ERROR: priority invalid"; }

        if (action.equals("CREATE")) {
            if (userRole.equals("ADMIN")) {
                if (isUrgent) {
                    if (isBulk) {
                        // Bloc de validation dupliqué (copie 2) — S7
                        if (task == null) { return "ERROR: task is null"; }
                        if (task.getTitle() == null || task.getTitle().isEmpty()) { return "ERROR: title required"; }
                        if (task.getDescription() == null || task.getDescription().isEmpty()) { return "ERROR: description required"; }
                        if (task.getPriority() == null || task.getPriority() < 0 || task.getPriority() > 10) { return "ERROR: priority invalid"; }
                        result = "BULK_URGENT_ADMIN_CREATE";
                        task.setPriority(10);
                        task.setStatus(Task.TaskStatus.IN_PROGRESS);
                        taskRepository.save(task);
                        System.out.println("Bulk urgent admin create: " + task.getTitle());
                    } else {
                        result = "URGENT_ADMIN_CREATE";
                        task.setPriority(9);
                        task.setStatus(Task.TaskStatus.IN_PROGRESS);
                        taskRepository.save(task);
                    }
                } else {
                    result = "NORMAL_ADMIN_CREATE";
                    task.setPriority(5);
                    taskRepository.save(task);
                }
            } else if (userRole.equals("USER")) {
                if (isUrgent) {
                    result = "URGENT_USER_CREATE";
                    task.setPriority(7);
                    task.setStatus(Task.TaskStatus.TODO);
                    taskRepository.save(task);
                } else {
                    result = "NORMAL_USER_CREATE";
                    task.setPriority(3);
                    taskRepository.save(task);
                }
            } else {
                return "ERROR: unknown role";
            }
        } else if (action.equals("UPDATE")) {
            if (userRole.equals("ADMIN")) {
                // Bloc de validation dupliqué (copie 3) — S7
                if (task == null) { return "ERROR: task is null"; }
                if (task.getTitle() == null || task.getTitle().isEmpty()) { return "ERROR: title required"; }
                if (task.getDescription() == null || task.getDescription().isEmpty()) { return "ERROR: description required"; }
                if (task.getPriority() == null || task.getPriority() < 0 || task.getPriority() > 10) { return "ERROR: priority invalid"; }
                result = "ADMIN_UPDATE";
                task.setUpdatedAt(LocalDateTime.now());
                taskRepository.save(task);
            } else if (userRole.equals("USER")) {
                if (isUrgent) {
                    result = "URGENT_USER_UPDATE";
                    task.setPriority(8);
                    task.setUpdatedAt(LocalDateTime.now());
                    taskRepository.save(task);
                } else {
                    result = "NORMAL_USER_UPDATE";
                    task.setUpdatedAt(LocalDateTime.now());
                    taskRepository.save(task);
                }
            }
        } else if (action.equals("DELETE")) {
            if (userRole.equals("ADMIN")) {
                taskRepository.deleteById(task.getId());
                result = "ADMIN_DELETE";
            } else {
                return "ERROR: insufficient permissions";
            }
        } else if (action.equals("COMPLETE")) {
            task.setStatus(Task.TaskStatus.DONE);
            task.setUpdatedAt(LocalDateTime.now());
            taskRepository.save(task);
            result = "TASK_COMPLETED";
        } else if (action.equals("CANCEL")) {
            task.setStatus(Task.TaskStatus.CANCELLED);
            task.setUpdatedAt(LocalDateTime.now());
            taskRepository.save(task);
            result = "TASK_CANCELLED";
        } else {
            // TODO: implémenter les autres actions (ARCHIVE, RESTORE, CLONE)
            // code commenté intentionnellement — S8 code smell
            /*
            task.setStatus(Task.TaskStatus.CANCELLED);
            taskRepository.save(task);
            result = "ARCHIVED";
            */
            result = "UNKNOWN_ACTION";
        }

        // Variable inutilisée — S8 code smell
        int unusedCounter = 0;
        String unusedMessage = "This variable is never used";

        return result;
    }

    // Validation admin basique
    public boolean validateAdmin(String username, String password) {
        // VULNERABILITY S2 — comparaison avec le hardcoded password
        return adminUsername.equals(username) && adminPassword.equals(password);
    }
}
