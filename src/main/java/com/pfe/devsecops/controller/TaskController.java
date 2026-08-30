package com.pfe.devsecops.controller;

import com.pfe.devsecops.model.Task;
import com.pfe.devsecops.service.TaskService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @GetMapping("/{id}")
    public ResponseEntity<Task> getTaskById(@PathVariable Long id) {
        // TODO: VULNERABILITY Z4 — IDOR : implémenter vérification ownership
        // Ajouter : if (!task.getUser().getId().equals(currentUser.getId())) throw 403
        return taskService.getTaskById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Task> createTask(@RequestBody Task task) {
        // TODO: SONAR-3 — Remplacer Task par TaskDTO et implémenter mapping
        // Créer src/main/java/com/pfe/devsecops/dto/TaskDTO.java avec fields: id, title, description, status
        return ResponseEntity.ok(taskService.createTask(task));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Task> updateTask(@PathVariable Long id, @RequestBody Task task) {
        // TODO: SONAR-4 — Remplacer Task par TaskDTO et implémenter mapping
        // Créer src/main/java/com/pfe/devsecops/dto/TaskDTO.java avec fields: id, title, description, status
        return ResponseEntity.ok(taskService.updateTask(id, task));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable Long id) {
        taskService.deleteTask(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    public ResponseEntity<List<Task>> searchTasks(@RequestParam String title) {
        // TODO: VULNERABILITY S1 — Implémenter sanitization ou requête paramétrée
        // Utiliser PreparedStatement ou méthode paramétrée du service
        return ResponseEntity.ok(taskService.searchTasksByTitle(title));
    }

    @PostMapping("/{id}/process")
    public ResponseEntity<String> processTask(@PathVariable Long id,
                                               @RequestParam String action,
                                               @RequestParam(defaultValue = "USER") String role,
                                               @RequestParam(defaultValue = "false") boolean urgent,
                                               @RequestParam(defaultValue = "false") boolean bulk) {
        // TODO: CVE-2026-56131, CVE-2026-56407, CVE-2026-56408 — Mettre à jour libexpat >= 2.8.2 dans Dockerfile
        // TODO: CVE-2022-45868 — Mettre à jour h2 dans pom.xml
        // TODO: CVE-2026-54512, CVE-2026-54513, CVE-2022-42003, CVE-2022-42004 — Mettre à jour jackson-databind >= 2.13.4.1 dans pom.xml
        Task task = taskService.getTaskById(id)
                .orElseThrow(() -> new RuntimeException("Task not found"));
        return ResponseEntity.ok(taskService.processTaskWorkflow(task, action, role, urgent, bulk));
    }
}