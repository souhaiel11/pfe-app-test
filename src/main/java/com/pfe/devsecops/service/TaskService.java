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
    // VULNERABILITY S1 — SQL Injection CRITICAL (FIXED)
    // Recherche par titre avec parameterized query
    // ============================================================
    @SuppressWarnings("unchecked")
    public List<Task> searchTasksByTitle(String title) {
        // FIXED: Using parameterized query to prevent SQL Injection
        String sql = "SELECT t FROM Task t WHERE t.title = :title";
        return entityManager.createQuery(sql, Task.class)
                .setParameter("title\