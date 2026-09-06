package com.pfe.devsecops.dto;

import com.pfe.devsecops.model.Task;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TaskDTOTest {

    @Test
    void fromEntity_mapsEachStatusEnumToItsName() {
        for (Task.TaskStatus status : Task.TaskStatus.values()) {
            Task task = new Task();
            task.setStatus(status);
            assertEquals(status.name(), TaskDTO.fromEntity(task).getStatus());
        }
    }

    @Test
    void fromEntity_nullEntityStatus_mapsToNullDtoStatus() {
        Task task = new Task();
        task.setStatus(null);
        assertNull(TaskDTO.fromEntity(task).getStatus());
    }

    @Test
    void toEntity_mapsEachValidStatusStringToEnum() {
        for (Task.TaskStatus status : Task.TaskStatus.values()) {
            TaskDTO dto = new TaskDTO();
            dto.setStatus(status.name());
            assertEquals(status, dto.toEntity().getStatus());
        }
    }

    @Test
    void toEntity_nullDtoStatus_leavesEntityDefaultStatus() {
        TaskDTO dto = new TaskDTO();
        dto.setStatus(null);
        assertEquals(Task.TaskStatus.TODO, dto.toEntity().getStatus());
    }

    @Test
    void toEntity_invalidStatusString_throwsExplicitly() {
        TaskDTO dto = new TaskDTO();
        dto.setStatus("NOT_A_REAL_STATUS");
        assertThrows(IllegalArgumentException.class, dto::toEntity);
    }
}
