package com.pfe.devsecops.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfe.devsecops.config.SecurityConfig;
import com.pfe.devsecops.dto.TaskDTO;
import com.pfe.devsecops.model.Task;
import com.pfe.devsecops.security.JwtRequestFilter;
import com.pfe.devsecops.security.JwtUtil;
import com.pfe.devsecops.service.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// R21-AV regression: proves TaskDTO<->Task status mapping (fixed after the
// S4684 candidate broke compilation with an enum/String mismatch) actually
// round-trips through the real controller-service boundary, not just in
// isolation on TaskDTO itself (TaskDTOTest). Same @WebMvcTest pattern as
// AuthControllerTest: SecurityConfig/JwtRequestFilter imported explicitly so
// the real production security chain (not the slice test default) governs
// these requests.
@WebMvcTest(TaskController.class)
@Import({ SecurityConfig.class, JwtRequestFilter.class })
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TaskService taskService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    @WithMockUser
    void getTaskById_serializesEnumStatusAsItsName() throws Exception {
        Task task = new Task();
        task.setId(1L);
        task.setTitle("t");
        task.setDescription("d");
        task.setStatus(Task.TaskStatus.IN_PROGRESS);
        when(taskService.getTaskById(1L)).thenReturn(Optional.of(task));

        mockMvc.perform(get("/api/tasks/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                // S4684 remediation: the JPA relationship (user) must never
                // leak into the API-facing representation.
                .andExpect(jsonPath("$.user").doesNotExist());
    }

    @Test
    @WithMockUser
    void createTask_convertsRequestStatusStringIntoEntityEnum() throws Exception {
        TaskDTO request = new TaskDTO();
        request.setTitle("t");
        request.setDescription("d");
        request.setStatus("DONE");

        when(taskService.createTask(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"));
    }

    @Test
    @WithMockUser
    void updateTask_convertsRequestStatusStringIntoEntityEnum() throws Exception {
        TaskDTO request = new TaskDTO();
        request.setTitle("t");
        request.setDescription("d");
        request.setStatus("CANCELLED");

        when(taskService.updateTask(eq(1L), any(Task.class))).thenAnswer(inv -> inv.getArgument(1));

        mockMvc.perform(put("/api/tasks/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @WithMockUser
    void searchTasks_serializesEnumStatusAsItsNameForEachResult() throws Exception {
        Task task = new Task();
        task.setId(2L);
        task.setTitle("found");
        task.setDescription("d");
        task.setStatus(Task.TaskStatus.TODO);
        when(taskService.searchTasksByTitle("found")).thenReturn(List.of(task));

        mockMvc.perform(get("/api/tasks/search").param("title", "found"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("TODO"));
    }
}
