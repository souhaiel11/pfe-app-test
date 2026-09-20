package com.pfe.devsecops.controller;

import com.pfe.devsecops.config.SecurityConfig;
import com.pfe.devsecops.model.Task;
import com.pfe.devsecops.repository.TaskRepository;
import com.pfe.devsecops.repository.UserRepository;
import com.pfe.devsecops.security.JwtRequestFilter;
import com.pfe.devsecops.security.JwtUtil;
import com.pfe.devsecops.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Permanent regression coverage for PUT /api/tasks/{id} status update
 * semantics on an omitted vs. explicit-null "status" JSON field (see PR #34
 * / incident 73733ec0). Exercises the REAL TaskController and TaskService
 * through Spring's actual HTTP/Jackson/security stack (mocking only the
 * persistence repositories), same pattern already established by
 * AuthControllerTest (@WebMvcTest + explicit SecurityConfig/JwtRequestFilter
 * import), so an omitted key and an explicit null are genuinely distinct
 * inputs at the real HTTP boundary, never modeled as the same case.
 *
 * Authoritative contract (proven by a differential harness against the
 * ORIGINAL pre-migration baseline commit, not assumed):
 *   ABSENT status        -> persisted status resets to TaskStatus.TODO
 *   explicit "status":null -> persisted status becomes null
 *   valid status value    -> persisted as the parsed enum value
 *   invalid status value  -> request rejected (400), existing task untouched
 */
@WebMvcTest(TaskController.class)
@Import({ SecurityConfig.class, JwtRequestFilter.class, TaskService.class })
@WithMockUser
class TaskControllerUpdateStatusSemanticsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TaskRepository taskRepository;
    @MockBean
    private UserRepository userRepository;
    @MockBean
    private JwtUtil jwtUtil;
    @MockBean
    private UserDetailsService userDetailsService;
    @MockBean
    private javax.persistence.EntityManagerFactory entityManagerFactory;

    private Task existing;

    @BeforeEach
    void setUp() {
        existing = new Task();
        existing.setId(1L);
        existing.setTitle("orig-title");
        existing.setDescription("orig-desc");
        existing.setStatus(Task.TaskStatus.IN_PROGRESS);
        existing.setPriority(5);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // A. ABSENT
    @Test
    void semantic_v1__DEFAULT_VALUE_SEMANTICS_DEFECT__Task__status__TaskDTO__status__case_ABSENT__resetsToBaselineDefault() throws Exception {
        String json = "{\"title\":\"t2\",\"description\":\"d2\",\"priority\":7}";
        mockMvc.perform(put("/api/tasks/1").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());
        assertEquals(Task.TaskStatus.TODO, existing.getStatus(),
                "an omitted status must reset to the baseline-equivalent default TODO, not preserve the previous value");
    }

    // B. EXPLICIT NULL
    @Test
    void semantic_v1__DEFAULT_VALUE_SEMANTICS_DEFECT__Task__status__TaskDTO__status__case_EXPLICIT_NULL__setsNull() throws Exception {
        String json = "{\"title\":\"t2\",\"description\":\"d2\",\"priority\":7,\"status\":null}";
        mockMvc.perform(put("/api/tasks/1").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());
        assertNull(existing.getStatus(), "an explicit null status must be persisted as null");
    }

    // C. VALID
    @Test
    void semantic_v1__DEFAULT_VALUE_SEMANTICS_DEFECT__Task__status__TaskDTO__status__case_EXPLICIT_VALUE__isPersisted() throws Exception {
        String json = "{\"title\":\"t2\",\"description\":\"d2\",\"priority\":7,\"status\":\"DONE\"}";
        mockMvc.perform(put("/api/tasks/1").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());
        assertEquals(Task.TaskStatus.DONE, existing.getStatus());
    }

    // D. INVALID
    @Test
    void semantic_v1__DEFAULT_VALUE_SEMANTICS_DEFECT__Task__status__TaskDTO__status__case_INVALID_VALUE__isRejected() throws Exception {
        String json = "{\"title\":\"t2\",\"description\":\"d2\",\"priority\":7,\"status\":\"BOGUS\"}";
        MvcResult result = mockMvc.perform(put("/api/tasks/1").contentType(MediaType.APPLICATION_JSON).content(json))
                .andReturn();
        assertEquals(400, result.getResponse().getStatus(), "an invalid status value must be rejected with 400");
        assertEquals(Task.TaskStatus.IN_PROGRESS, existing.getStatus(), "a rejected update must not mutate the existing task");
    }
}
