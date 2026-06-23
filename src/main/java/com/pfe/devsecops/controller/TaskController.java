package com.pfe.devsecops.controller;

import com.pfe.devsecops.model.Task;
import com.pfe.devsecops.service.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    @Autowired
    private TaskService taskService;

    @GetMapping
    public ResponseEntity<List<Task>> getAllTasks() {
        return ResponseEntity.ok(taskService.getAllTasks());
    }

    // VULNERABILITY Z4 — IDOR : pas de vérification ownership
    // N'importe quel user authentifié peut voir la tâche de n'importe qui
    // en changeant l'id dans l'URL
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
        return ResponseEntity.ok(taskService.createTask(task));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Task> updateTask(@PathVariable Long id, @RequestBody Task task) {
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
        // title est passé directement sans sanitization → SQL injection
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
