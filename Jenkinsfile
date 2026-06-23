pipeline {
    agent any
    environment {
        APP_NAME        = 'pfe-app-test'
        IMAGE_NAME      = 'pfe-app-test'
        IMAGE_TAG       = "${BUILD_NUMBER}"
        SONAR_HOST_URL  = 'http://sonarqube:9000'
        N8N_WEBHOOK_URL = 'http://n8n:5678/webhook/jenkins-event'
        N8N_API_KEY     = 'devsecops-secret-2024'
        PROJECT_ID      = '54192eca-43da-4d8f-9b49-30c143983fdd'
        BACKEND_URL     = 'http://172.31.172.61:3001'
        K8S_NAMESPACE   = 'pfe-devsecops'
        ZAP_TARGET_URL  = 'http://192.168.49.2:30003'
    }
    tools {
        maven 'M3'
    }
    stages {
        stage('Checkout') {
            steps {
                echo '=== STAGE 1: Checkout ==='
                checkout scm
                sh 'echo "Branch: $(git branch --show-current)"'
                sh 'echo "Commit: $(git rev-parse --short HEAD)"'
            }
        }
        stage('Build') {
            steps {
                echo '=== STAGE 2: Maven Build ==='
                sh 'mvn clean compile -B'
            }
            post {
                failure { echo 'Build FAILED' }
            }
        }
        stage('Test') {
            steps {
                echo '=== STAGE 3: Tests + Coverage JaCoCo ==='
                sh 'mvn test jacoco:report -B || true'
            }
            post {
                always {
                    junit allowEmptyResults: true,
                          testResults: '**/target/surefire-reports/*.xml'
                }
            }
        }
        stage('SonarQube Analysis') {
            steps {
                echo '=== STAGE 4: SonarQube SAST ==='
                withSonarQubeEnv('sq1') {
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
                failure { echo 'SonarQube scan FAILED' }
            }
        }
        stage('Trivy Scan') {
            steps {
                echo '=== STAGE 5: Trivy Security Scan ==='
                sh "docker build -t ${IMAGE_NAME}:${IMAGE_TAG} ."
                sh """
                    docker run --rm \
                      -v /var/run/docker.sock:/var/run/docker.sock \
                      -v \$(pwd):/workspace \
                      aquasec/trivy:latest image \
                        --exit-code 0 \
                        --format json \
                        --output /workspace/trivy-report.json \
                        --severity CRITICAL,HIGH,MEDIUM \
                        --no-progress \
                        ${IMAGE_NAME}:${IMAGE_TAG} || true
                    echo "Trivy scan terminé"
                    [ -f trivy-report.json ] && head -20 trivy-report.json || echo "Rapport absent"
                """
            }
            post {
                always {
                    archiveArtifacts artifacts: 'trivy-report.json',
                                     allowEmptyArchive: true
                }
            }
        }
        stage('OWASP Dependency Check') {
            steps {
                echo '=== STAGE 6: OWASP Dependency Check ==='
                sh """
                    mvn org.owasp:dependency-check-maven:check \
                      -DfailBuildOnCVSS=10 \
                      -DnvdApiKey=14EB33B7-AB3A-F111-836A-129478FCB64D \
                      -Dformat=ALL \
                      -B || true
                """
            }
            post {
                always {
                    archiveArtifacts artifacts: 'target/dependency-check-report.*',
                                     allowEmptyArchive: true
                }
            }
        }
        stage('Docker Build') {
            steps {
                echo '=== STAGE 7: Docker Build ==='
                sh """
                    docker build -t ${IMAGE_NAME}:${IMAGE_TAG} .
                    docker tag ${IMAGE_NAME}:${IMAGE_TAG} ${IMAGE_NAME}:latest
                    echo "Image buildée: ${IMAGE_NAME}:${IMAGE_TAG}"
                """
            }
        }
        stage('Deploy to Minikube') {
            steps {
                echo '=== STAGE 8: Deploy Kubernetes ==='
                sh """
                    kubectl apply -f k8s/ -n ${K8S_NAMESPACE} || true
                    kubectl set image deployment/${APP_NAME} \
                      ${APP_NAME}=${IMAGE_NAME}:${IMAGE_TAG} \
                      -n ${K8S_NAMESPACE} || true
                    kubectl rollout status deployment/${APP_NAME} \
                      -n ${K8S_NAMESPACE} \
                      --timeout=120s || true
                """
            }
        }
        stage('ZAP DAST Scan') {
            steps {
                echo '=== STAGE 9: OWASP ZAP DAST ==='
                sh """
                    sleep 15
                    mkdir -p \$(pwd)/zap-work
                    chmod 777 \$(pwd)/zap-work
                    docker run --rm \
                      --network=host \
                      -v \$(pwd)/zap-work:/zap/wrk/:rw \
                      --user root \
                      ghcr.io/zaproxy/zaproxy:stable \
                      zap-baseline.py \
                        -t ${ZAP_TARGET_URL} \
                        -J zap-report.json \
                        -r zap-report.html \
                        -I || true
                    [ -f \$(pwd)/zap-work/zap-report.json ] && \
                      cp \$(pwd)/zap-work/zap-report.json . || true
                    [ -f \$(pwd)/zap-work/zap-report.html ] && \
                      cp \$(pwd)/zap-work/zap-report.html . || true
                """
            }
            post {
                always {
                    archiveArtifacts artifacts: 'zap-report.*',
                                     allowEmptyArchive: true
                }
            }
        }
        stage('Notify Platform') {
            steps {
                echo '=== STAGE 10: Notification webhook → n8n ==='
                script {
                    def buildStatus = currentBuild.currentResult ?: 'SUCCESS'
                    def event    = buildStatus == 'SUCCESS'  ? 'pipeline_success'
                                 : buildStatus == 'UNSTABLE' ? 'pipeline_unstable'
                                 : 'pipeline_failed'
                    def severity = buildStatus == 'FAILURE'  ? 'HIGH'
                                 : buildStatus == 'UNSTABLE' ? 'MEDIUM' : 'LOW'
                    def branch   = env.GIT_BRANCH?.replaceAll('origin/', '') ?: 'main'
                    def commit   = env.GIT_COMMIT?.take(8) ?: 'unknown'

                    // Lire rapports
                    def trivyReport = '{"Results":[]}'
                    def zapReport   = '{"site":[]}'
                    try { trivyReport = readFile("${env.WORKSPACE}/trivy-report.json").trim() } catch(e) {}
                    try { zapReport   = readFile("${env.WORKSPACE}/zap-report.json").trim()   } catch(e) {}

                    // Parser Trivy
                    def trivyCritical = 0; def trivyHigh = 0
                    try {
                        def td = new groovy.json.JsonSlurper().parseText(trivyReport)
                        td.Results?.each { r -> r.Vulnerabilities?.each { v ->
                            if (v.Severity == 'CRITICAL') trivyCritical++
                            if (v.Severity == 'HIGH')     trivyHigh++
                        }}
                    } catch(e) {}

                    // Parser ZAP
                    def zapHigh = 0; def zapMedium = 0; def zapLow = 0
                    try {
                        def zd = new groovy.json.JsonSlurper().parseText(zapReport)
                        zd.site?.each { s -> s.alerts?.each { a ->
                            def risk = a.riskdesc?.split(' ')[0]
                            if (risk == 'High')   zapHigh++
                            if (risk == 'Medium') zapMedium++
                            if (risk == 'Low')    zapLow++
                        }}
                    } catch(e) {}

                    def payload = """{
                        "event"        : "${event}",
                        "project_id"   : "${env.PROJECT_ID}",
                        "backend_url"  : "${env.BACKEND_URL}",
                        "job"          : "${env.JOB_NAME}",
                        "build_number" : "${env.BUILD_NUMBER}",
                        "build_url"    : "${env.BUILD_URL}",
                        "logs_url"     : "${env.BUILD_URL}consoleText",
                        "branch"       : "${branch}",
                        "commit"       : "${commit}",
                        "status"       : "${buildStatus}",
                        "severity"     : "${severity}",
                        "duration_ms"  : ${currentBuild.duration},
                        "tests"  : { "status": "UNKNOWN", "total": 0, "failures": 0, "skipped": 0 },
                        "sonar"  : {
                            "project_key"  : "${env.APP_NAME}",
                            "dashboard_url": "http://172.31.172.61:9000/dashboard?id=${env.APP_NAME}"
                        },
                        "trivy"  : {
                            "critical"   : ${trivyCritical},
                            "high"       : ${trivyHigh},
                            "report_url" : "${env.BUILD_URL}artifact/trivy-report.json"
                        },
                        "owasp"  : { "status": "SKIPPED", "critical": 0, "high": 0 },
                        "zap"    : {
                            "alerts_high"   : ${zapHigh},
                            "alerts_medium" : ${zapMedium},
                            "alerts_low"    : ${zapLow},
                            "target_url"    : "${env.ZAP_TARGET_URL}",
                            "report_url"    : "${env.BUILD_URL}artifact/zap-report.json"
                        },
                        "docker" : { "image": "${env.IMAGE_NAME}:${env.IMAGE_TAG}" },
                        "deploy" : { "namespace": "${env.K8S_NAMESPACE}" }
                    }"""

                    sh """
                        curl -s -X POST '${env.N8N_WEBHOOK_URL}' \
                          -H 'Content-Type: application/json' \
                          -H 'X-API-Key: ${env.N8N_API_KEY}' \
                          -d '${payload.replaceAll("'", "\\\\'")}' \
                          --max-time 15 || true
                        echo "n8n notifié: ${event}"
                    """
                }
            }
        }
    }
    post {
        always {
            echo "=== BUILD ${currentBuild.currentResult} — Build #${BUILD_NUMBER} ==="
            deleteDir()
        }
        success  { echo '✅ Pipeline pfe-app-test SUCCESS' }
        unstable { echo '⚠️ Pipeline pfe-app-test UNSTABLE' }
        failure  { echo '❌ Pipeline pfe-app-test FAILED'   }
    }
}
