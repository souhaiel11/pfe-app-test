# Stage 1 — Build avec Maven
FROM maven:3.8.6-openjdk-11 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2 — Runtime avec JRE Alpine léger
FROM eclipse-temurin:11-jre-alpine
WORKDIR /app

# Métadonnées
LABEL maintainer="Amri Souhaiel <amri.souhaiel@esprit.tn>"
LABEL project="pfe-app-test"
LABEL version="1.0.0"

# Utilisateur non-root (bonne pratique — mais CVEs dans les dépendances restent)
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /app/target/pfe-app-test-1.0.0.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD wget -q --spider http://localhost:8080/api/auth/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
