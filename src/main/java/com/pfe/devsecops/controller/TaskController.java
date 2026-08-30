package com.pfe.devsecops.controller;

import com.pfe.devsecops.model.Task;
import com.pfe.devsecops.service.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

// TODO: Create TaskDTO class with id, title, description, status fields and getters/setters
// This DTO replaces Task entity in API responses to prevent ORM entity exposure (SonarQube S4684)

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private static final Logger logger = LoggerFactory.getLogger(TaskController.class);
    private static final int MAX_SEARCH_LENGTH = 255;
    private static final String SEARCH_PARAM_VALIDATION_ERROR = "Search parameter exceeds maximum length";

    private final TaskService taskService;

    @Autowired
    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    public ResponseEntity<List<Task>> getAllTasks() {
        return ResponseEntity.ok(taskService.getAllTasks());
    }

    // VULNERABILITY Z4 — IDOR : pas de vérification ownership
    // N'importe quel user authentifié peut voir la tâche de n'importe qui
    // en changeant l'id dans l'URL
    // TODO: Implement ownership verification - check that current user owns the task before returning
    // Correct pattern: if (!task.getUser().getId().equals(currentUser.getId())) throw new AccessDeniedException()
    @GetMapping("/{id}")
    public ResponseEntity<Task> getTaskById(@PathVariable Long id) {
        // MANQUE : vérification que l'user courant est le propriétaire de la tâche
        // Correct : if (!task.getUser().getId().equals(currentUser.getId())) throw 403
        return taskService.getTaskById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Task> createTask(@RequestBody Task task) {
        // TODO: Replace Task entity with TaskDTO in request body (SonarQube S4684)
        // Map TaskDTO to Task entity internally before persisting
        return ResponseEntity.ok(taskService.createTask(task));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Task> updateTask(@PathVariable Long id, @RequestBody Task task) {
        // TODO: Replace Task entity with TaskDTO in request/response (SonarQube S4684)
        // Map TaskDTO to Task entity internally, then map response back to TaskDTO
        return ResponseEntity.ok(taskService.updateTask(id, task));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable Long id) {
        taskService.deleteTask(id);
        return ResponseEntity.noContent().build();
    }

    // VULNERABILITY S1 — endpoint qui expose la SQL injection
    @GetMapping("/search")
    public ResponseEntity<List<Task>> searchTasks(@RequestParam String title) {
        // Input validation to prevent SQL injection and resource exhaustion
        if (title == null || title.trim().isEmpty()) {
            logger.warn("Search attempted with empty title parameter");
            return ResponseEntity.badRequest().build();
        }
        if (title.length() > MAX_SEARCH_LENGTH) {
            logger.warn("Search parameter exceeds maximum length: {}", title.length());
            return ResponseEntity.badRequest().build();
        }
        // title is now validated; TaskService should use parameterized queries
        // TODO: Ensure TaskService.searchTasksByTitle uses PreparedStatement or JPA parameterized queries
        return ResponseEntity.ok(taskService.searchTasksByTitle(title));
    }

    @PostMapping("/{id}/process")
    public ResponseEntity<String> processTask(@PathVariable Long id,
                                               @RequestParam String action,
                                               @RequestParam(defaultValue = "USER") String role,
                                               @RequestParam(defaultValue = "false") boolean urgent,
                                               @RequestParam(defaultValue = "false") boolean bulk) {
        Task task = taskService.getTaskById(id)
                .orElseThrow(() -> new RuntimeException("Task not found"));
        return ResponseEntity.ok(taskService.processTaskWorkflow(task, action, role, urgent, bulk));
    }
}

// TODO: Update pom.xml dependencies to fix OWASP CVEs:
// - jackson-databind: upgrade from 2.13.3 to 2.15.2+ (fixes CVE-2022-42003, CVE-2022-42004, CVE-2026-54512, CVE-2026-54513)
// - h2: upgrade from 2.1.212 to 2.2.220+ (fixes CVE-2022-45868)
// - logback: upgrade to 1.4.11+ (fixes CVE-2023-6378)

// TODO: Update Dockerfile base image to patch Trivy container CVEs:
// - libexpat: CVE-2026-56131, CVE-2026-56407, CVE-2026-56408
// - p11-kit: CVE-2026-2100
// Use latest Alpine/Debian base image with security patches applied