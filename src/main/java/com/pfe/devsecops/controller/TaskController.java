package com.pfe.devsecops.controller;

import com.pfe.devsecops.dto.TaskCreateDTO;
import com.pfe.devsecops.dto.TaskDTO;
import com.pfe.devsecops.dto.TaskStatusDTO;
import com.pfe.devsecops.dto.TaskUpdateDTO;
import com.pfe.devsecops.model.Task;
import com.pfe.devsecops.model.User;
import com.pfe.devsecops.repository.UserRepository;
import com.pfe.devsecops.service.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    @Autowired
    private TaskService taskService;

    @Autowired
    private UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<TaskDTO>> getAllTasks() {
        List<Task> tasks = taskService.getAllTasks();
        List<TaskDTO> result = new ArrayList<>();
        if (tasks != null) {
            for (Task task : tasks) {
                result.add(toDto(task));
            }
        }
        return ResponseEntity.ok(result);
    }

    // VULNERABILITY Z4 — IDOR : pas de vérification ownership
    // N'importe quel user authentifié peut voir la tâche de n'importe qui
    // en changeant l'id dans l'URL
    @GetMapping("/{id}")
    public ResponseEntity<TaskDTO> getTaskById(@PathVariable Long id) {
        return taskService.getTaskById(id)
                .map(task -> ResponseEntity.ok(toDto(task)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<TaskDTO> createTask(@RequestBody TaskCreateDTO taskCreateDTO) {
        Task task = new Task();
        task.setTitle(taskCreateDTO.getTitle());
        task.setDescription(taskCreateDTO.getDescription());
        if (taskCreateDTO.getStatus() != null) {
            task.setStatus(toEntityStatus(taskCreateDTO.getStatus()));
        }
        task.setPriority(taskCreateDTO.getPriority());

        Long userId = taskCreateDTO.getUserId();
        if (userId != null) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found: " + userId));
            task.setUser(user);
        }

        return ResponseEntity.ok(toDto(taskService.createTask(task)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TaskDTO> updateTask(@PathVariable Long id, @RequestBody TaskUpdateDTO taskUpdateDTO) {
        // Volontairement pas de userId : l'association existante Task.user reste inchangée
        Task task = new Task();
        task.setTitle(taskUpdateDTO.getTitle());
        task.setDescription(taskUpdateDTO.getDescription());
        if (taskUpdateDTO.getStatus() != null) {
            task.setStatus(toEntityStatus(taskUpdateDTO.getStatus()));
        }
        task.setPriority(taskUpdateDTO.getPriority());
        return ResponseEntity.ok(toDto(taskService.updateTask(id, task)));
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

    private TaskDTO toDto(Task task) {
        if (task == null) {
            return null;
        }
        TaskDTO dto = new TaskDTO();
        dto.setId(task.getId());
        dto.setTitle(task.getTitle());
        dto.setDescription(task.getDescription());
        dto.setStatus(toDtoStatus(task.getStatus()));
        dto.setPriority(task.getPriority());
        dto.setCreatedAt(task.getCreatedAt());
        dto.setUpdatedAt(task.getUpdatedAt());
        User user = task.getUser();
        dto.setUserId(user == null ? null : user.getId());
        return dto;
    }

    private TaskStatusDTO toDtoStatus(Task.TaskStatus status) {
        if (status == null) {
            return null;
        }
        switch (status) {
            case TODO:
                return TaskStatusDTO.TODO;
            case IN_PROGRESS:
                return TaskStatusDTO.IN_PROGRESS;
            case DONE:
                return TaskStatusDTO.DONE;
            case CANCELLED:
                return TaskStatusDTO.CANCELLED;
            default:
                throw new IllegalArgumentException("Unsupported task status: " + status);
        }
    }

    private Task.TaskStatus toEntityStatus(TaskStatusDTO status) {
        if (status == null) {
            return null;
        }
        switch (status) {
            case TODO:
                return Task.TaskStatus.TODO;
            case IN_PROGRESS:
                return Task.TaskStatus.IN_PROGRESS;
            case DONE:
                return Task.TaskStatus.DONE;
            case CANCELLED:
                return Task.TaskStatus.CANCELLED;
            default:
                throw new IllegalArgumentException("Unsupported task status: " + status);
        }
    }
}
