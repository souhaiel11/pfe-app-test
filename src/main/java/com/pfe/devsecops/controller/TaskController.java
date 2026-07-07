package com.pfe.devsecops.controller;

import com.pfe.devsecops.model.Task;
import com.pfe.devsecops.dto.TaskRequestDTO;
import com.pfe.devsecops.dto.TaskResponseDTO;
import com.pfe.devsecops.dto.TaskDTO;
import com.pfe.devsecops.service.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private static final Logger logger = LoggerFactory.getLogger(TaskController.class);
    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    public ResponseEntity<List<Task>> getAllTasks() {
        return ResponseEntity.ok(taskService.getAllTasks());
    }

    // TODO: VULNERABILITY Z4 — IDOR : Implement ownership verification
    // Add security check: verify that current user is the task owner
    // Implementation: Get current user from SecurityContextHolder and compare with task.getUser().getId()
    @GetMapping("/{id}")
    public ResponseEntity<Task> getTaskById(@PathVariable Long id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        // FIXME: Add ownership check before returning task
        return taskService.getTaskById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<TaskResponseDTO> createTask(@RequestBody TaskDTO taskDTO) {
        Task task = new Task();
        task.setTitle(taskDTO.getTitle());
        task.setDescription(taskDTO.getDescription());
        task.setStatus(taskDTO.getStatus());
        Task createdTask = taskService.createTask(task);
        return ResponseEntity.ok(convertToResponseDTO(createdTask));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TaskResponseDTO> updateTask(@PathVariable Long id, @RequestBody TaskDTO taskDTO) {
        Task task = new Task();
        task.setTitle(taskDTO.getTitle());
        task.setDescription(taskDTO.getDescription());
        task.setStatus(taskDTO.getStatus());
        Task updatedTask = taskService.updateTask(id, task);
        return ResponseEntity.ok(convertToResponseDTO(updatedTask));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable Long id) {
        taskService.deleteTask(id);
        return ResponseEntity.noContent().build();
    }

    // TODO: VULNERABILITY S1 — SQL injection risk
    // Implement parameterized queries in TaskService.searchTasksByTitle()
    // Use JPA @Query with @Param instead of string concatenation
    @GetMapping("/search")
    public ResponseEntity<List<Task>> searchTasks(@RequestParam String title) {
        // FIXME: Ensure TaskService uses parameterized queries
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

    private TaskResponseDTO convertToResponseDTO(Task task) {
        TaskResponseDTO dto = new TaskResponseDTO();
        dto.setId(task.getId());
        dto.setTitle(task.getTitle());
        dto.setDescription(task.getDescription());
        dto.setStatus(task.getStatus());
        return dto;
    }
}