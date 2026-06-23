package com.pfe.devsecops.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "tasks")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    private TaskStatus status = TaskStatus.TODO;

    private Integer priority;

    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt;

    // VULNERABILITY S4 : user peut être null → NullPointerException si on appelle getUser().getUsername()
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    public enum TaskStatus {
        TODO, IN_PROGRESS, DONE, CANCELLED
    }

    // Méthode qui retourne le nom du propriétaire SANS vérification null — SonarQube Bug HIGH
    public String getOwnerName() {
        return user.getUsername(); // NPE si user == null
    }
}
