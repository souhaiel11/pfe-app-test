# pfe-app-test — Projet Cobaye DevSecOps

API REST de gestion de tâches utilisée comme **projet cible** pour valider la plateforme DevSecOps PFE 2026.

## Stack
- Java 11 + Spring Boot **2.7.0** (volontairement vulnérable)
- H2 Database embarquée
- Maven + JaCoCo + SonarQube + Trivy + OWASP + ZAP

---

## ⚠️ VULNÉRABILITÉS INTENTIONNELLES

### 🔴 SonarQube (SAST)

| ID | Fichier | Ligne | Type | Sévérité |
|---|---|---|---|---|
| S1 | `TaskService.java` | ~90 | SQL Injection (concaténation directe) | CRITICAL |
| S2 | `TaskService.java` | ~20-22 | Hardcoded passwords (admin123, root1234) | HIGH |
| S3 | `JwtUtil.java` | ~15 | JWT secret hardcodé | HIGH |
| S4 | `Task.java` + `TaskService.java` | ~55 | NullPointerException potentielle sur getUser() | HIGH |
| S5 | `TaskService.java` | ~103 | Resource leak — FileInputStream non fermé | MEDIUM |
| S6 | `TaskService.java` | ~115 | Méthode processTaskWorkflow() > 80 lignes | MEDIUM |
| S7 | `TaskService.java` | ~120,140,165 | Bloc de validation dupliqué 3 fois | MEDIUM |
| S8 | `TaskService.java` | ~195-197 | Variables inutilisées + TODO non résolu | LOW |

### 🟠 Trivy / OWASP Dependency Check (SCA)

| ID | CVE | Composant | Version | Sévérité |
|---|---|---|---|---|
| T1 | CVE-2022-22965 | spring-webmvc | 5.3.20 | **CRITICAL** |
| T2 | CVE-2022-22950 | spring-expression | 5.3.20 | HIGH |
| T3 | CVE-2021-42550 | logback-classic | 1.2.11 | HIGH |
| T4 | CVE-2022-25857 | snakeyaml | 1.29 | HIGH |
| T5 | CVE-2023-20860 | spring-security-core | 5.6.4 | HIGH |

### 🟡 ZAP (DAST)

| ID | Type | Description | Sévérité |
|---|---|---|---|
| Z1 | CORS | `Access-Control-Allow-Origin: *` — SecurityConfig.java | HIGH |
| Z2 | Missing Header | `X-Content-Type-Options` absent | MEDIUM |
| Z3 | Missing Header | `X-Frame-Options` absent | MEDIUM |
| Z4 | IDOR | `GET /api/tasks/{id}` sans vérification propriétaire | HIGH |
| Z5 | Missing Header | `Content-Security-Policy` absent | MEDIUM |

### 🔵 Jenkins (CI/CD)

| ID | Test | Raison | Résultat attendu |
|---|---|---|---|
| J1 | `TaskServiceTest.testDeleteTask_WhenExists_ShouldFail` | assertEquals(false, true) | ❌ FAIL |
| J2 | `AuthControllerTest.testLoginWithInvalidUser_ShouldFail` | assertEquals(200, 401) | ❌ FAIL |
| J3 | Coverage global | Tests incomplets | ~45% |

---

## 🎯 Résultat attendu sur la plateforme

```
Score DevSecOps  → 10-15 / 100
Niveau de risque → CRITICAL
Décision Judge   → BLOCK
CVEs Trivy       → 5+ (dont 1 CRITICAL)
Issues SonarQube → 8+ (dont 1 CRITICAL)
Alertes ZAP      → 5
Tests échoués    → 2/13
```

---

## Démarrage local

```bash
mvn clean spring-boot:run
# API disponible sur http://localhost:8080
# H2 Console : http://localhost:8080/h2-console
```

## Endpoints principaux

```
POST /api/auth/register  — Créer un compte
POST /api/auth/login     — Obtenir un JWT
GET  /api/tasks          — Lister toutes les tâches
POST /api/tasks          — Créer une tâche
GET  /api/tasks/{id}     — Détail tâche (IDOR ici)
GET  /api/tasks/search?title=xxx  — Recherche (SQL Injection ici)
```

---

*Projet PFE DevSecOps 2026 — Amri Souhaiel — ESPRIT / Vermeg*
