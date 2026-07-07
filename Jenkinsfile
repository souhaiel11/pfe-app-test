pipeline {
    agent any

    tools { maven 'M3' }

    environment {
        N8N_API_KEY = credentials('N8N_API_KEY')
        NVD_API_KEY = credentials('NVD_API_KEY')
        SONAR_TOKEN = credentials('SONAR_TOKEN')
    }

    stages {

        stage('Init') {
            steps {
                script {
                    env.APP_NAME        = 'pfe-app-test'
                    env.BACKEND_URL     = 'http://172.31.172.61:3001'
                    env.N8N_WEBHOOK_URL = 'http://n8n:5678/webhook/jenkins-event'
                    env.SONAR_HOST_URL  = 'http://sonarqube:9000'
                    env.ZAP_TARGET_URL  = 'http://192.168.49.2:30003'
                    env.K8S_NAMESPACE   = 'pfe-devsecops'
                    env.IMAGE_NAME      = 'pfe-app-test'
                    env.IMAGE_TAG       = "${env.BUILD_NUMBER}"
                    echo "============================================"
                    echo " Job     : ${env.JOB_NAME}"
                    echo " Build   : #${env.BUILD_NUMBER}"
                    echo " Backend : ${env.BACKEND_URL}"
                    echo "============================================"
                }
            }
        }

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
                          -Dsonar.projectKey=${env.APP_NAME} \
                          -Dsonar.projectName='PFE App Test' \
                          -Dsonar.host.url=${env.SONAR_HOST_URL} \
                          -Dsonar.token=${env.SONAR_TOKEN} \
                          -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml \
                          -B
                    """
                }
            }
            post {
                failure { echo 'SonarQube FAILED' }
            }
        }

        stage('Trivy Scan') {
            steps {
                echo '=== STAGE 5: Trivy Security Scan ==='
                sh "docker build -t ${env.IMAGE_NAME}:${env.IMAGE_TAG} . || true"
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
                        ${env.IMAGE_NAME}:${env.IMAGE_TAG} || true
                    echo "Trivy terminé"
                    [ -f trivy-report.json ] && head -5 trivy-report.json || echo "Rapport absent"
                """
            }
            post {
                always {
                    archiveArtifacts artifacts: 'trivy-report.json', allowEmptyArchive: true
                }
            }
        }

        stage('OWASP Dependency Check') {
            steps {
                echo '=== STAGE 6: OWASP Dependency Check ==='
                sh """
                    mvn org.owasp:dependency-check-maven:check \
                      -DfailBuildOnCVSS=10 \
                      -DnvdApiKey=${env.NVD_API_KEY} \
                      -Dformat=ALL \
                      -B || true
                """
            }
            post {
                always {
                    archiveArtifacts artifacts: 'target/dependency-check-report.*', allowEmptyArchive: true
                }
            }
        }

        stage('Docker Build') {
            steps {
                echo '=== STAGE 7: Docker Build ==='
                sh """
                    docker build -t ${env.IMAGE_NAME}:${env.IMAGE_TAG} . || true
                    docker tag ${env.IMAGE_NAME}:${env.IMAGE_TAG} ${env.IMAGE_NAME}:latest || true
                """
            }
        }

        stage('Deploy to Minikube') {
            steps {
                echo '=== STAGE 8: Deploy Kubernetes ==='
                sh """
                    kubectl apply -f k8s/ -n ${env.K8S_NAMESPACE} || true
                    kubectl set image deployment/${env.APP_NAME} \
                      ${env.APP_NAME}=${env.IMAGE_NAME}:${env.IMAGE_TAG} \
                      -n ${env.K8S_NAMESPACE} || true
                    kubectl rollout status deployment/${env.APP_NAME} \
                      -n ${env.K8S_NAMESPACE} --timeout=120s || true
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
                        -t ${env.ZAP_TARGET_URL} \
                        -J zap-report.json \
                        -r zap-report.html \
                        -I || true
                    [ -f \$(pwd)/zap-work/zap-report.json ] && cp \$(pwd)/zap-work/zap-report.json . || true
                    [ -f \$(pwd)/zap-work/zap-report.html ] && cp \$(pwd)/zap-work/zap-report.html . || true
                """
            }
            post {
                always {
                    archiveArtifacts artifacts: 'zap-report.*', allowEmptyArchive: true
                }
            }
        }
    }

    post {
        always {
            echo "=== BUILD ${currentBuild.currentResult} — #${BUILD_NUMBER} ==="
            script {
                try {
                    def buildStatus = currentBuild.currentResult ?: 'FAILURE'
                    def event    = buildStatus == 'SUCCESS'  ? 'pipeline_success'
                                 : buildStatus == 'UNSTABLE' ? 'pipeline_unstable'
                                 : 'pipeline_failed'
                    def severity = buildStatus == 'FAILURE'  ? 'HIGH'
                                 : buildStatus == 'UNSTABLE' ? 'MEDIUM' : 'LOW'
                    def branch   = env.GIT_BRANCH?.replaceAll('origin/', '') ?: 'main'
                    def commit   = env.GIT_COMMIT?.take(8) ?: 'unknown'

                    def trivyCritical = 0; def trivyHigh = 0
                    try {
                        def td = new groovy.json.JsonSlurper().parseText(
                            readFile("${env.WORKSPACE}/trivy-report.json")
                        )
                        td.Results?.each { r -> r.Vulnerabilities?.each { v ->
                            if (v.Severity == 'CRITICAL') trivyCritical++
                            if (v.Severity == 'HIGH')     trivyHigh++
                        }}
                    } catch(ex) {}

                    def zapHigh = 0; def zapMedium = 0; def zapLow = 0
                    try {
                        def zd = new groovy.json.JsonSlurper().parseText(
                            readFile("${env.WORKSPACE}/zap-report.json")
                        )
                        zd.site?.each { s -> s.alerts?.each { a ->
                            def risk = a.riskdesc?.split(' ')[0]
                            if (risk == 'High')   zapHigh++
                            if (risk == 'Medium') zapMedium++
                            if (risk == 'Low')    zapLow++
                        }}
                    } catch(ex) {}

                    def owaspCritical = 0; def owaspHigh = 0
                    try {
                        def od = new groovy.json.JsonSlurper().parseText(
                            readFile("${env.WORKSPACE}/target/dependency-check-report.json")
                        )
                        od.dependencies?.each { dep -> dep.vulnerabilities?.each { v ->
                            def sev = v.severity?.toUpperCase()
                            if (sev == 'CRITICAL') owaspCritical++
                            if (sev == 'HIGH')     owaspHigh++
                        }}
                    } catch(ex) {}

                    def payload = """{
                        "event"        : "${event}",
                        "job"          : "${env.JOB_NAME}",
                        "build_number" : "${env.BUILD_NUMBER}",
                        "build_url"    : "${env.BUILD_URL}",
                        "logs_url"     : "${env.BUILD_URL}consoleText",
                        "branch"       : "${branch}",
                        "commit"       : "${commit}",
                        "status"       : "${buildStatus}",
                        "severity"     : "${severity}",
                        "duration_ms"  : ${currentBuild.duration},
                        "sonar" : {
                            "project_key"  : "${env.APP_NAME}",
                            "dashboard_url": "${env.SONAR_HOST_URL}/dashboard?id=${env.APP_NAME}"
                        },
                        "trivy" : {
                            "critical"   : ${trivyCritical},
                            "high"       : ${trivyHigh},
                            "report_url" : "${env.BUILD_URL}artifact/trivy-report.json"
                        },
                        "zap" : {
                            "alerts_high"   : ${zapHigh},
                            "alerts_medium" : ${zapMedium},
                            "alerts_low"    : ${zapLow},
                            "target_url"    : "${env.ZAP_TARGET_URL}",
                            "report_url"    : "${env.BUILD_URL}artifact/zap-report.json"
                        },
                        "owasp" : {
                            "critical"   : ${owaspCritical},
                            "high"       : ${owaspHigh},
                            "report_url" : "${env.BUILD_URL}artifact/target/dependency-check-report.json"
                        },
                        "docker" : { "image" : "${env.IMAGE_NAME}:${env.IMAGE_TAG}" },
                        "deploy" : { "namespace" : "${env.K8S_NAMESPACE}", "app_url" : "${env.ZAP_TARGET_URL}" }
                    }"""

                    sh """
                        curl -s -X POST '${env.N8N_WEBHOOK_URL}' \
                          -H 'Content-Type: application/json' \
                          -H 'X-API-Key: ${env.N8N_API_KEY}' \
                          -d '${payload.replaceAll("'", "\\\\'")}' \
                          --max-time 15 || true
                        echo "✅ n8n notifié: ${event}"
                    """
                } catch(ex) {
                    echo "Webhook failed: ${ex.message}"
                }
            }
            deleteDir()
        }
        success  { echo '✅ Pipeline pfe-app-test SUCCESS' }
        unstable { echo '⚠️ Pipeline pfe-app-test UNSTABLE' }
        failure  { echo '❌ Pipeline pfe-app-test FAILED' }
    }
}

