package com.pfe.devsecops.controller;

import com.pfe.devsecops.dto.TaskDTO;
import com.pfe.devsecops.model.Task;
import com.pfe.devsecops.service.TaskService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    @Autowired
    private TaskService taskService;

    @GetMapping
    public ResponseEntity<List<TaskDTO>> getAllTasks() {
        List<TaskDTO> taskDTOs = taskService.getAllTasks().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(taskDTOs);
    }

    // VULNERABILITY Z4 — IDOR : pas de vérification ownership
    // N'importe quel user authentifié peut voir la tâche de n'importe qui
    // en changeant l'id dans l'URL
    @GetMapping("/{id}")
    public ResponseEntity<TaskDTO> getTaskById(@PathVariable Long id) {
        // MANQUE : vérification que l'user courant est le propriétaire de la tâche
        // Correct : if (!task.getUser().getId().equals(currentUser.getId())) throw 403
        return taskService.getTaskById(id)
                .map(this::convertToDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<TaskDTO> createTask(@RequestBody TaskDTO taskDTO) {
        Task task = convertToEntity(taskDTO);
        Task createdTask = taskService.createTask(task);
        return ResponseEntity.ok(convertToDto(createdTask));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TaskDTO> updateTask(@PathVariable Long id, @RequestBody TaskDTO taskDTO) {
        Task task = convertToEntity(taskDTO);
        Task updatedTask = taskService.updateTask(id, task);
        return ResponseEntity.ok(convertToDto(updatedTask));
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

    /**
     * Converts an API-facing TaskDTO into the internal JPA Task entity.
     * Only non-relational, non-sensitive fields declared on TaskDTO are copied,
     * keeping the persistence model decoupled from the REST contract.
     */
    private Task convertToEntity(TaskDTO taskDTO) {
        Task task = new Task();
        BeanUtils.copyProperties(taskDTO, task);
        return task;
    }

    /**
     * Converts an internal JPA Task entity into the API-facing TaskDTO,
     * ensuring the REST layer never exposes the raw persistent entity.
     */
    private TaskDTO convertToDto(Task task) {
        TaskDTO taskDTO = new TaskDTO();
        BeanUtils.copyProperties(task, taskDTO);
        return taskDTO;
    }
}
