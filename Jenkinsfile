pipeline {
    agent any
    environment {
        APP_NAME = 'pfe-app-test'
        IMAGE_NAME = "pfe-app-test"
        IMAGE_TAG = "${BUILD_NUMBER}"
        SONAR_HOST_URL = 'http://172.31.172.61:9000'
        BACKEND_WEBHOOK_URL = 'http://172.31.172.61:3001/api/webhooks/jenkins'
        K8S_NAMESPACE = 'pfe-devsecops'
        ZAP_TARGET_URL = 'http://192.168.49.2:30003'
    }
    tools {
        maven 'M3'
        
    }
    stages {
        // Stage 1 — Checkout
        stage('Checkout') {
            steps {
                echo '=== STAGE 1: Checkout ==='
                checkout scm
                sh 'echo "Branch: $(git branch --show-current)"'
                sh 'echo "Commit: $(git rev-parse --short HEAD)"'
            }
        }
        // Stage 2 — Build
        stage('Build') {
            steps {
                echo '=== STAGE 2: Maven Build ==='
                sh 'mvn clean compile -B'
            }
            post {
                failure {
                    echo 'Build FAILED'
                }
            }
        }
        // Stage 3 — Tests + JaCoCo Coverage
        stage('Test') {
            steps {
                echo '=== STAGE 3: Tests + Coverage JaCoCo ==='
                sh 'mvn test -B'
            }
            post {
                always {
                    junit '**/target/surefire-reports/*.xml'
                    jacoco(
                        execPattern: '**/target/jacoco.exec',
                        classPattern: '**/target/classes',
                        sourcePattern: '**/src/main/java',
                        exclusionPattern: '**/test/**'
                    )
                }
                failure {
                    echo 'Tests FAILED — rapport envoyé au webhook'
                }
            }
        }
        // Stage 4 — SonarQube SAST
        stage('SonarQube Analysis') {
            steps {
                echo '=== STAGE 4: SonarQube SAST ==='
                withSonarQubeEnv('SonarQube') {
                    sh """
                        mvn sonar:sonar \
                          -Dsonar.projectKey=${APP_NAME} \
                          -Dsonar.projectName='PFE App Test' \
                          -Dsonar.host.url=${SONAR_HOST_URL} \
                          -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml \
                          -B
                    """
                }
            }
            post {
                failure {
                    echo 'SonarQube scan FAILED'
                }
            }
        }
        // Stage 5 — Trivy Container Scan
        stage('Trivy Scan') {
            steps {
                echo '=== STAGE 5: Trivy Security Scan ==='
                sh """
                    # Build image pour scan
                    docker build -t ${IMAGE_NAME}:${IMAGE_TAG} .
                    # Scan Trivy sur l'image avec rapport JSON
                    trivy image \
                      --format json \
                      --output trivy-report.json \
                      --severity CRITICAL,HIGH,MEDIUM \
                      --no-progress \
                      ${IMAGE_NAME}:${IMAGE_TAG} || true
                    echo "Trivy scan terminé — rapport : trivy-report.json"
                    cat trivy-report.json | head -50
                """
            }
            post {
                always {
                    archiveArtifacts artifacts: 'trivy-report.json', allowEmptyArchive: true
                }
            }
        }
        // Stage 6 — OWASP Dependency Check
        stage('OWASP Dependency Check') {
            steps {
                echo '=== STAGE 6: OWASP Dependency Check ==='
                sh """
                    mvn org.owasp:dependency-check-maven:check \
                      -DnvdApiKey=${NVD_API_KEY} \
                      -DfailBuildOnCVSS=10 \
                      -Dformat=ALL \
                      -B || true
                """
            }
            post {
                always {
                    archiveArtifacts artifacts: 'target/dependency-check-report.*', allowEmptyArchive: true
                    publishHTML([
                        allowMissing: true,
                        alwaysLinkToLastBuild: true,
                        keepAll: true,
                        reportDir: 'target',
                        reportFiles: 'dependency-check-report.html',
                        reportName: 'OWASP Report'
                    ])
                }
            }
        }
        // Stage 7 — Docker Build & Push Nexus
        stage('Docker Build & Push') {
            steps {
                echo '=== STAGE 7: Docker Build & Push ==='
                sh """
                    docker build -t ${IMAGE_NAME}:${IMAGE_TAG} .
                    docker tag ${IMAGE_NAME}:${IMAGE_TAG} ${IMAGE_NAME}:latest
                    echo "Image buildée: ${IMAGE_NAME}:${IMAGE_TAG}"
                """
            }
        }
        // Stage 8 — Deploy to Minikube
        stage('Deploy to Minikube') {
            steps {
                echo '=== STAGE 8: Deploy Kubernetes ==='
                sh """
                    # Charger l'image dans Minikube
                    minikube image load ${IMAGE_NAME}:${IMAGE_TAG} -p minikube || true
                    # Appliquer les manifests Kubernetes
                    kubectl apply -f k8s/ -n ${K8S_NAMESPACE} || true
                    # Mettre à jour l'image du deployment
                    kubectl set image deployment/${APP_NAME} \
                      ${APP_NAME}=${IMAGE_NAME}:${IMAGE_TAG} \
                      -n ${K8S_NAMESPACE} || true
                    # Attendre le rollout
                    kubectl rollout status deployment/${APP_NAME} \
                      -n ${K8S_NAMESPACE} \
                      --timeout=120s || true
                """
            }
        }
        // Stage 9 — ZAP DAST Scan
        stage('ZAP DAST Scan') {
            steps {
                echo '=== STAGE 9: OWASP ZAP DAST ==='
                sh """
                    # Attendre que l'app soit disponible
                    sleep 15
                    # ZAP Baseline Scan
                    docker run --rm \
                      --network=host \
                      -v \$(pwd):/zap/wrk/:rw \
                      ghcr.io/zaproxy/zaproxy:stable \
                      zap-baseline.py \
                        -t ${ZAP_TARGET_URL} \
                        -J zap-report.json \
                        -r zap-report.html \
                        -I || true
                """
            }
            post {
                always {
                    archiveArtifacts artifacts: 'zap-report.*', allowEmptyArchive: true
                }
            }
        }
        // Stage 10 — Notify Webhook DevSecOps Platform
        stage('Notify Platform') {
            steps {
                echo '=== STAGE 10: Notification webhook → Backend ==='
                script {
                    def buildStatus = currentBuild.currentResult ?: 'SUCCESS'
                    def trivyReport = ''
                    def zapReport = ''
                    try {
                        trivyReport = readFile("${env.WORKSPACE}/trivy-report.json")
                    } catch (e) {
                        trivyReport = '{"Results":[]}'
                    }
                    try {
                        zapReport = readFile("${env.WORKSPACE}/zap-report.json")
                    } catch (e) {
                        zapReport = '{"site":[]}'
                    }
                    def payload = """
                    {
                        "projectName": "${APP_NAME}",
                        "buildNumber": ${BUILD_NUMBER},
                        "buildStatus": "${buildStatus}",
                        "branch": "${GIT_BRANCH ?: 'main'}",
                        "commit": "${GIT_COMMIT ?: 'unknown'}",
                        "buildUrl": "${BUILD_URL}",
                        "sonarUrl": "${SONAR_HOST_URL}/dashboard?id=${APP_NAME}",
                        "trivyReportUrl": "${BUILD_URL}artifact/trivy-report.json",
                        "zapReportUrl": "${BUILD_URL}artifact/zap-report.json",
                        "timestamp": "${new Date().format('yyyy-MM-dd HH:mm:ss')}"
                    }
                    """
                    sh """
                        curl -X POST ${BACKEND_WEBHOOK_URL} \
                          -H 'Content-Type: application/json' \
                          -H 'X-Jenkins-Token: devsecops-secret-2024' \
                          -d '${payload.trim()}' \
                          --max-time 10 || true
                    """
                }
            }
        }
    }
    post {
        always {
            echo "=== BUILD ${currentBuild.currentResult} — Build #${BUILD_NUMBER} ==="
            cleanWs()
        }
        success {
            echo 'Pipeline terminé avec succès'
        }
        failure {
            echo 'Pipeline FAILED — vérifier les logs'
        }
    }
}
