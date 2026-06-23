package com.pfe.devsecops.repository;

import com.pfe.devsecops.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByUserId(Long userId);

    List<Task> findByStatus(Task.TaskStatus status);

    // VULNERABILITY S1 — SQL Injection CRITICAL
    // Concaténation directe du paramètre dans la requête native
    @Query(value = "SELECT * FROM tasks WHERE title = '" + "' OR '1'='1", nativeQuery = true)
    List<Task> findByTitleUnsafe(String title);

    // Version encore plus explicite pour SonarQube
    default List<Task> searchByTitleDynamic(String title) {
        // Cette méthode simule ce que ferait un dev naïf avec EntityManager
        // La vraie injection est dans le service via EntityManager
        return findAll();
    }
}
