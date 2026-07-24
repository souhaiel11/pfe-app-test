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
                .setParameter("title\