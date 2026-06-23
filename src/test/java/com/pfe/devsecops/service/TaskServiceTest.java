package com.pfe.devsecops.service;

import com.pfe.devsecops.model.Task;
import com.pfe.devsecops.model.User;
import com.pfe.devsecops.repository.TaskRepository;
import com.pfe.devsecops.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @InjectMocks
    private TaskService taskService;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private UserRepository userRepository;

    private Task sampleTask;
    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = new User();
        sampleUser.setId(1L);
        sampleUser.setUsername("testuser");
        sampleUser.setPassword("encoded");
        sampleUser.setRole(User.Role.USER);

        sampleTask = new Task();
        sampleTask.setId(1L);
        sampleTask.setTitle("Test Task");
        sampleTask.setDescription("Test Description");
        sampleTask.setStatus(Task.TaskStatus.TODO);
        sampleTask.setPriority(5);
        sampleTask.setUser(sampleUser);
    }

    // ✅ Test 1 — passe
    @Test
    void testGetAllTasks_ReturnsListSuccessfully() {
        when(taskRepository.findAll()).thenReturn(Arrays.asList(sampleTask));
        List<Task> tasks = taskService.getAllTasks();
        assertNotNull(tasks);
        assertEquals(1, tasks.size());
        assertEquals("Test Task", tasks.get(0).getTitle());
    }

    // ✅ Test 2 — passe
    @Test
    void testGetTaskById_WhenExists_ReturnsTask() {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(sampleTask));
        Optional<Task> result = taskService.getTaskById(1L);
        assertTrue(result.isPresent());
        assertEquals("Test Task", result.get().getTitle());
    }

    // ✅ Test 3 — passe
    @Test
    void testGetTaskById_WhenNotExists_ReturnsEmpty() {
        when(taskRepository.findById(99L)).thenReturn(Optional.empty());
        Optional<Task> result = taskService.getTaskById(99L);
        assertFalse(result.isPresent());
    }

    // ✅ Test 4 — passe
    @Test
    void testGetTasksByUser_ReturnsUserTasks() {
        when(taskRepository.findByUserId(1L)).thenReturn(Arrays.asList(sampleTask));
        List<Task> tasks = taskService.getTasksByUser(1L);
        assertEquals(1, tasks.size());
    }

    // ✅ Test 5 — passe
    @Test
    void testUpdateTask_WhenExists_UpdatesSuccessfully() {
        Task updatedTask = new Task();
        updatedTask.setTitle("Updated Title");
        updatedTask.setDescription("Updated Description");
        updatedTask.setPriority(8);
        updatedTask.setStatus(Task.TaskStatus.IN_PROGRESS);

        when(taskRepository.findById(1L)).thenReturn(Optional.of(sampleTask));
        when(taskRepository.save(any(Task.class))).thenReturn(sampleTask);

        Task result = taskService.updateTask(1L, updatedTask);
        assertNotNull(result);
        verify(taskRepository, times(1)).save(any(Task.class));
    }

    // ✅ Test 6 — passe
    @Test
    void testValidateAdmin_WithCorrectCredentials_ReturnsTrue() {
        boolean result = taskService.validateAdmin("admin", "admin123");
        assertTrue(result);
    }

    // ✅ Test 7 — passe
    @Test
    void testValidateAdmin_WithWrongPassword_ReturnsFalse() {
        boolean result = taskService.validateAdmin("admin", "wrongpassword");
        assertFalse(result);
    }

    // ✅ Test 8 — passe
    @Test
    void testProcessTaskWorkflow_WithNullTask_ReturnsError() {
        String result = taskService.processTaskWorkflow(null, "CREATE", "ADMIN", false, false);
        assertEquals("ERROR: task is null", result);
    }

    // ❌ Test 9 — ÉCHOUE INTENTIONNELLEMENT (J1)
    // deleteTask retourne true quand la tâche existe, mais on assert false → FAIL
    @Test
    void testDeleteTask_WhenExists_ShouldFail() {
        when(taskRepository.existsById(1L)).thenReturn(true);
        doNothing().when(taskRepository).deleteById(1L);

        boolean result = taskService.deleteTask(1L);
        // INTENTIONNELLEMENT FAUX : deleteTask retourne true, on attend false → test FAIL
        assertEquals(false, result, "This test is intentionally failing for DevSecOps demo");
    }

    // ✅ Test 10 — passe
    @Test
    void testDeleteTask_WhenNotExists_ReturnsFalse() {
        when(taskRepository.existsById(99L)).thenReturn(false);
        boolean result = taskService.deleteTask(99L);
        assertFalse(result);
    }
}
