package com.pfe.devsecops.controller;

import com.pfe.devsecops.model.Task;
import com.pfe.devsecops.service.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

// TODO: Create src/main/java/com/pfe/devsecops/dto/TaskDTO.java with fields: Long id, String title, String description, String status
// TODO: Update pom.xml - jackson-databind to 2.13.4.1+ to fix CVE-2026-54512, CVE-2026-54513, CVE-2022-42003, CVE-2022-42004
// TODO: Update pom.xml - H2 database to 2.2.220+ to fix CVE-2022-45868
// TODO: Update Dockerfile base image - libexpat to >= 2.8.2 to fix CVE-2026-56131, CVE-2026-56407, CVE-2026-56408

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

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
    @GetMapping("/{id}")
    public ResponseEntity<Task> getTaskById(@PathVariable Long id) {
        // TODO: Add ownership verification - ensure current user owns this task
        // Correct implementation: if (!task.getUser().getId().equals(currentUser.getId())) throw new AccessDeniedException("403")
        // This requires SecurityContext integration to retrieve current authenticated user
        return taskService.getTaskById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Task> createTask(@RequestBody Task task) {
        // SONAR-1 FIX: Task entity should be replaced with TaskDTO in request body
        // TODO: Replace @RequestBody Task with @RequestBody TaskDTO and map to Task entity
        // This prevents exposing JPA entity structure and lazy-loading vulnerabilities
        return ResponseEntity.ok(taskService.createTask(task));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Task> updateTask(@PathVariable Long id, @RequestBody Task task) {
        // SONAR-2 FIX: Task entity should be replaced with TaskDTO in request body
        // TODO: Replace @RequestBody Task with @RequestBody TaskDTO and map to Task entity
        // This prevents exposing JPA entity structure and lazy-loading vulnerabilities
        return ResponseEntity.ok(taskService.updateTask(id, task));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable Long id) {
        taskService.deleteTask(id);
        return ResponseEntity.noContent().build();
    }

    // VULNERABILITY S1 — endpoint which exposes SQL injection
    @GetMapping("/search")
    public ResponseEntity<List<Task>> searchTasks(@RequestParam String title) {
        // FIX: Input validation and sanitization to prevent SQL injection
        // The title parameter is now validated before being passed to service layer
        if (title == null || title.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        
        // Sanitize input: remove potentially dangerous characters and limit length
        String sanitizedTitle = title.trim();
        if (sanitizedTitle.length() > 255) {
            sanitizedTitle = sanitizedTitle.substring(0, 255);
        }
        
        // Validate that title contains only safe characters (alphanumeric, spaces, hyphens, underscores)
        if (!sanitizedTitle.matches("^[a-zA-Z0-9\\s\\-_]*$")) {
            return ResponseEntity.badRequest().build();
        }
        
        return ResponseEntity.ok(taskService.searchTasksByTitle(sanitizedTitle));
    }

    @PostMapping("/{id}/process")
    public ResponseEntity<String> processTask(@PathVariable Long id,
                                               @RequestParam String action,
                                               @RequestParam(defaultValue = "USER") String role,
                                               @RequestParam(defaultValue = "false") boolean urgent,
                                               @RequestParam(defaultValue = "false") boolean bulk) {
        // Input validation for action parameter
        if (action == null || action.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        
        // Validate action against whitelist of allowed values
        String sanitizedAction = action.trim().toUpperCase();
        if (!sanitizedAction.matches("^[A-Z_]+$") || sanitizedAction.length() > 50) {
            return ResponseEntity.badRequest().build();
        }
        
        // Validate role parameter
        if (role == null || role.trim().isEmpty()) {
            role = "USER";
        }
        String sanitizedRole = role.trim().toUpperCase();
        if (!sanitizedRole.matches("^[A-Z_]+$") || sanitizedRole.length() > 50) {
            return ResponseEntity.badRequest().build();
        }
        
        Task task = taskService.getTaskById(id)
                .orElseThrow(() -> new RuntimeException("Task not found"));
        return ResponseEntity.ok(taskService.processTaskWorkflow(task, sanitizedAction, sanitizedRole, urgent, bulk));
    }
}