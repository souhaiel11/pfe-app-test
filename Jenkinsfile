pipeline {
    agent any

    tools {
        maven 'M3'
    }

    options {
        disableConcurrentBuilds()
    }

    environment {
        N8N_API_KEY = credentials('N8N_API_KEY')
        NVD_API_KEY = credentials('NVD_API_KEY')
        SONAR_TOKEN = credentials('SONAR_TOKEN')
    }

    stages {

        stage('Init') {
            steps {
                script {
                    env.APP_NAME = 'pfe-app-test'
                    env.IMAGE_NAME = 'pfe-app-test'
                    env.IMAGE_TAG = "${env.BUILD_NUMBER}"

                    env.BACKEND_URL = 'http://172.31.172.61:3001'
                    env.N8N_WEBHOOK_URL = 'http://n8n:5678/webhook/jenkins-event'
                    env.SONAR_HOST_URL = 'http://sonarqube:9000'

                    env.K8S_NAMESPACE = 'pfe-devsecops'
                    env.ZAP_IMAGE = 'zaproxy/zap-stable:latest'
                    env.ZAP_TARGET_URL = 'http://app-test:8080'

                    env.REPORT_BASE = "/shared/reports/${env.APP_NAME}/${env.BUILD_NUMBER}"
                    env.N8N_REPORT_BASE = "/home/node/.n8n-files/reports/${env.APP_NAME}/${env.BUILD_NUMBER}"

                    sh '''
                        mkdir -p "$REPORT_BASE"
                        echo "Report base Jenkins: $REPORT_BASE"
                        echo "Report base n8n    : $N8N_REPORT_BASE"
                    '''

                    echo "============================================"
                    echo " Job     : ${env.JOB_NAME}"
                    echo " Build   : #${env.BUILD_NUMBER}"
                    echo " App     : ${env.APP_NAME}"
                    echo " Image   : ${env.IMAGE_NAME}:${env.IMAGE_TAG}"
                    echo " Reports : ${env.REPORT_BASE}"
                    echo "============================================"
                }
            }
        }

        stage('Checkout') {
            steps {
                echo '=== STAGE 1: Checkout ==='
                checkout scm

                sh '''
                    echo "Commit: $(git rev-parse --short HEAD)"
                    echo "Branch : $(git rev-parse --abbrev-ref HEAD || true)"
                '''
            }
        }

        stage('Build Without Tests') {
            steps {
                echo '=== STAGE 2: Maven Build Without Tests ==='

                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    sh '''
                        mvn clean package -B \
                          -DskipTests=true \
                          -Djacoco.skip=true || true
                    '''
                }
            }
        }

        stage('SonarQube Analysis') {
            steps {
                echo '=== STAGE 3: SonarQube SAST ==='

                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    withSonarQubeEnv('sq1') {
                        sh '''
                            mvn sonar:sonar -B \
                              -DskipTests=true \
                              -Djacoco.skip=true \
                              -Dsonar.projectKey="$APP_NAME" \
                              -Dsonar.projectName="PFE App Test" \
                              -Dsonar.host.url="$SONAR_HOST_URL" \
                              -Dsonar.token="$SONAR_TOKEN" || true
                        '''
                    }
                }
            }
        }

        stage('Docker Build') {
            steps {
                echo '=== STAGE 4: Docker Build ==='

                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    sh '''
                        docker build -t "$IMAGE_NAME:$IMAGE_TAG" . || true
                        docker tag "$IMAGE_NAME:$IMAGE_TAG" "$IMAGE_NAME:latest" || true

                        echo "=== Docker image created ==="
                        docker images | grep "$IMAGE_NAME" || true
                    '''
                }
            }
        }

stage('Trivy Scan') {
    steps {
        echo '=== STAGE 5: Trivy Security Scan ==='

        catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
            sh '''
                BUILD="$BUILD_NUMBER"
                TMP_DIR="/tmp/trivy-test-$BUILD"

                rm -rf "$TMP_DIR"
                mkdir -p "$TMP_DIR"

                echo "=== Create persistent Trivy cache ==="
                docker volume create trivy-cache || true

                echo "=== Save Docker image ==="
                docker save "$IMAGE_NAME:$IMAGE_TAG" -o "$TMP_DIR/image.tar"
                ls -lh "$TMP_DIR/image.tar"

                echo "=== Try to update Trivy DB cache ==="
                docker run --rm \
                  -v trivy-cache:/root/.cache \
                  aquasec/trivy:latest image \
                  --download-db-only || true

                echo "=== Create Trivy container with cache ==="
                TRIVY_CID=$(docker create \
                  -v trivy-cache:/root/.cache \
                  --entrypoint sh \
                  aquasec/trivy:latest \
                  -c "sleep 1800")

                docker start "$TRIVY_CID" >/dev/null

                echo "=== Copy image.tar to Trivy container ==="
                docker cp "$TMP_DIR/image.tar" "$TRIVY_CID:/image.tar"

                echo "=== Run Trivy scan ==="
                docker exec "$TRIVY_CID" trivy image \
                  --input /image.tar \
                  --scanners vuln \
                  --skip-db-update \
                  --skip-java-db-update \
                  --exit-code 0 \
                  --format json \
                  --severity CRITICAL,HIGH,MEDIUM \
                  --no-progress \
                  --timeout 30m \
                  --output /trivy-report.json || true

                echo "=== Copy Trivy report ==="
                docker cp "$TRIVY_CID:/trivy-report.json" "$TMP_DIR/trivy-report.json" || true
                docker rm -f "$TRIVY_CID" >/dev/null 2>&1 || true

                if [ ! -s "$TMP_DIR/trivy-report.json" ]; then
                  echo '{"SchemaVersion":2,"Results":[],"status":"trivy_report_missing"}' > "$TMP_DIR/trivy-report.json"
                fi

                mkdir -p "$REPORT_BASE"
                cp "$TMP_DIR/trivy-report.json" "$REPORT_BASE/trivy-report.json"

                echo "=== Final Trivy report ==="
                ls -lh "$REPORT_BASE/trivy-report.json"
                head -c 500 "$REPORT_BASE/trivy-report.json" || true
                echo ""
            '''
        }
    }
}

        stage('OWASP Dependency Check') {
            steps {
                echo '=== STAGE 6: OWASP Dependency Check ==='

                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    sh '''
                        MVN="/var/jenkins_home/tools/hudson.tasks.Maven_MavenInstallation/M3/bin/mvn"

                        if [ ! -x "$MVN" ]; then
                          MVN=$(find /var/jenkins_home/tools -type f -name mvn | head -1)
                        fi

                        echo "=== Maven check ==="
                        "$MVN" -v

                        echo "=== Prepare OWASP folders ==="
                        mkdir -p "$REPORT_BASE"
                        mkdir -p /var/jenkins_home/dependency-check-data

                        echo "=== Run OWASP Dependency Check ==="
                        timeout 30m "$MVN" org.owasp:dependency-check-maven:check \
                          -Dformat=ALL \
                          -DfailBuildOnCVSS=11 \
                          -DfailOnError=false \
                          -DdataDirectory=/var/jenkins_home/dependency-check-data \
                          -DnvdApiKey="$NVD_API_KEY" \
                          -DnvdApiDelay=6000 \
                          -DskipTests=true \
                          -DretireJsAnalyzerEnabled=false \
                          -DnodeAuditAnalyzerEnabled=false \
                          -DossindexAnalyzerEnabled=false \
                          -DknownExploitedEnabled=false \
                          -DhostedSuppressionsEnabled=false \
                          -B || true

                        echo "=== Try OWASP offline mode if report missing ==="
                        if [ ! -f target/dependency-check-report.json ]; then
                          timeout 10m "$MVN" org.owasp:dependency-check-maven:check \
                            -Dformat=ALL \
                            -DfailBuildOnCVSS=11 \
                            -DfailOnError=false \
                            -DdataDirectory=/var/jenkins_home/dependency-check-data \
                            -DautoUpdate=false \
                            -DskipTests=true \
                            -DretireJsAnalyzerEnabled=false \
                            -DnodeAuditAnalyzerEnabled=false \
                            -DossindexAnalyzerEnabled=false \
                            -DknownExploitedEnabled=false \
                            -DhostedSuppressionsEnabled=false \
                            -B || true
                        fi

                        echo "=== Copy OWASP reports ==="

                        if [ -f target/dependency-check-report.json ]; then
                          cp target/dependency-check-report.json "$REPORT_BASE/dependency-check-report.json"
                        else
                          echo '{"dependencies":[],"status":"owasp_report_missing"}' > "$REPORT_BASE/dependency-check-report.json"
                        fi

                        if [ -f target/dependency-check-report.html ]; then
                          cp target/dependency-check-report.html "$REPORT_BASE/dependency-check-report.html"
                        fi

                        echo "=== Final OWASP reports ==="
                        ls -lh "$REPORT_BASE"/dependency-check-report.* || true
                        head -c 500 "$REPORT_BASE/dependency-check-report.json" || true
                        echo ""
                    '''
                }
            }
        }

        stage('Deploy to Minikube') {
            steps {
                echo '=== STAGE 7: Deploy Kubernetes ==='

                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    sh '''
                        if ! command -v kubectl >/dev/null 2>&1; then
                          echo "kubectl not found in Jenkins. Skipping deploy."
                          exit 0
                        fi

                        kubectl apply -f k8s/ -n "$K8S_NAMESPACE" || true

                        kubectl set image deployment/app-test \
                          app-test="$IMAGE_NAME:$IMAGE_TAG" \
                          -n "$K8S_NAMESPACE" || true

                        kubectl rollout status deployment/app-test \
                          -n "$K8S_NAMESPACE" \
                          --timeout=120s || true
                    '''
                }
            }
        }

        stage('ZAP DAST Scan') {
            options {
                timeout(time: 20, unit: 'MINUTES')
            }

            steps {
                echo '=== STAGE 8: OWASP ZAP DAST Scan inside Kubernetes ==='

                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    sh '''
                        BUILD="$BUILD_NUMBER"
                        ZAP_POD="zap-scan-$BUILD"

                        mkdir -p "$REPORT_BASE"

                        if ! command -v kubectl >/dev/null 2>&1; then
                          echo "kubectl not found in Jenkins. ZAP Kubernetes scan skipped."
                          echo '{"site":[],"status":"zap_kubectl_missing"}' > "$REPORT_BASE/zap-report.json"
                          exit 0
                        fi

                        echo "=== Check Kubernetes access ==="
                        kubectl get svc -n "$K8S_NAMESPACE" || {
                          echo '{"site":[],"status":"zap_k8s_unreachable"}' > "$REPORT_BASE/zap-report.json"
                          exit 0
                        }

                        echo "=== Clean old ZAP pod ==="
                        kubectl delete pod "$ZAP_POD" \
                          -n "$K8S_NAMESPACE" \
                          --ignore-not-found=true || true

                        echo "=== Run ZAP pod inside Kubernetes ==="
                        kubectl run "$ZAP_POD" \
                          -n "$K8S_NAMESPACE" \
                          --image="$ZAP_IMAGE" \
                          --image-pull-policy=IfNotPresent \
                          --restart=Never \
                          --command -- sh -lc '
                            mkdir -p /zap/wrk

                            zap-baseline.py \
                              -t http://app-test:8080 \
                              -J zap-report.json \
                              -r zap-report.html \
                              -w zap-report.md \
                              -I || true

                            touch /zap/wrk/zap.done
                            sleep 3600
                          '

                        echo "=== Wait for ZAP pod ready ==="
                        kubectl wait --for=condition=Ready pod/"$ZAP_POD" \
                          -n "$K8S_NAMESPACE" \
                          --timeout=180s || true

                        echo "=== Wait for ZAP scan completion ==="
                        for i in $(seq 1 90); do
                          if kubectl exec "$ZAP_POD" -n "$K8S_NAMESPACE" -- test -f /zap/wrk/zap.done 2>/dev/null; then
                            echo "ZAP scan finished"
                            break
                          fi

                          echo "Waiting ZAP scan... $i"
                          sleep 10
                        done

                        echo "=== ZAP logs ==="
                        kubectl logs "$ZAP_POD" -n "$K8S_NAMESPACE" || true

                        echo "=== Copy ZAP reports ==="
                        kubectl cp "$K8S_NAMESPACE/$ZAP_POD:/zap/wrk/zap-report.json" "$REPORT_BASE/zap-report.json" || true
                        kubectl cp "$K8S_NAMESPACE/$ZAP_POD:/zap/wrk/zap-report.html" "$REPORT_BASE/zap-report.html" || true
                        kubectl cp "$K8S_NAMESPACE/$ZAP_POD:/zap/wrk/zap-report.md" "$REPORT_BASE/zap-report.md" || true

                        if [ ! -s "$REPORT_BASE/zap-report.json" ]; then
                          echo '{"site":[],"status":"zap_report_missing"}' > "$REPORT_BASE/zap-report.json"
                        fi

                        echo "=== Final ZAP reports ==="
                        ls -lh "$REPORT_BASE"/zap-report.* || true
                        head -c 500 "$REPORT_BASE/zap-report.json" || true
                        echo ""

                        echo "=== Cleanup ZAP pod ==="
                        kubectl delete pod "$ZAP_POD" \
                          -n "$K8S_NAMESPACE" \
                          --ignore-not-found=true || true
                    '''
                }
            }
        }
    }

    post {
        always {
            echo '=== FINAL REPORTING ==='

            script {
                try {
                    def buildStatus = currentBuild.currentResult ?: 'SUCCESS'

                    def event = buildStatus == 'SUCCESS'  ? 'pipeline_success'
                              : buildStatus == 'UNSTABLE' ? 'pipeline_unstable'
                              : 'pipeline_failed'

                    def severity = buildStatus == 'FAILURE'  ? 'HIGH'
                                 : buildStatus == 'UNSTABLE' ? 'MEDIUM'
                                 : 'LOW'

                    def branch = env.GIT_BRANCH?.replaceAll('origin/', '') ?: 'main'
                    def commit = env.GIT_COMMIT?.take(8) ?: 'unknown'

                    def trivyAvailable = sh(
                        script: "test -s '${env.REPORT_BASE}/trivy-report.json'",
                        returnStatus: true
                    ) == 0

                    def zapAvailable = sh(
                        script: "test -s '${env.REPORT_BASE}/zap-report.json'",
                        returnStatus: true
                    ) == 0

                    def owaspAvailable = sh(
                        script: "test -s '${env.REPORT_BASE}/dependency-check-report.json'",
                        returnStatus: true
                    ) == 0

                    def payloadObject = [
                        event       : event,
                        job         : env.JOB_NAME,
                        build_number: env.BUILD_NUMBER,
                        build_url   : env.BUILD_URL,
                        logs_url    : "${env.BUILD_URL}consoleText",
                        branch      : branch,
                        commit      : commit,
                        status      : buildStatus,
                        severity    : severity,
                        duration_ms : currentBuild.duration,

                        reports: [
                            jenkinsBasePath: env.REPORT_BASE,
                            basePath       : env.N8N_REPORT_BASE,

                            trivyPath: "${env.N8N_REPORT_BASE}/trivy-report.json",
                            zapPath  : "${env.N8N_REPORT_BASE}/zap-report.json",
                            owaspPath: "${env.N8N_REPORT_BASE}/dependency-check-report.json",

                            available: [
                                trivy: trivyAvailable,
                                zap  : zapAvailable,
                                owasp: owaspAvailable
                            ]
                        ],

                        sonar: [
                            project_key  : env.APP_NAME,
                            dashboard_url: "${env.SONAR_HOST_URL}/dashboard?id=${env.APP_NAME}"
                        ],

                        trivy: [
                            report_path: "${env.N8N_REPORT_BASE}/trivy-report.json"
                        ],

                        zap: [
                            target_url : env.ZAP_TARGET_URL,
                            report_path: "${env.N8N_REPORT_BASE}/zap-report.json"
                        ],

                        owasp: [
                            report_path: "${env.N8N_REPORT_BASE}/dependency-check-report.json"
                        ],

                        docker: [
                            image: "${env.IMAGE_NAME}:${env.IMAGE_TAG}"
                        ],

                        deploy: [
                            namespace: env.K8S_NAMESPACE,
                            app_url  : env.ZAP_TARGET_URL
                        ]
                    ]

                    def payload = groovy.json.JsonOutput.prettyPrint(
                        groovy.json.JsonOutput.toJson(payloadObject)
                    )

                    writeFile file: 'jenkins-webhook-payload.json', text: payload

                    sh '''
                        cp jenkins-webhook-payload.json "$REPORT_BASE/payload.json" || true

                        echo "=== Shared reports final ==="
                        ls -la "$REPORT_BASE" || true

                        curl -s -X POST "$N8N_WEBHOOK_URL" \
                          -H "Content-Type: application/json" \
                          -H "X-API-Key: $N8N_API_KEY" \
                          --data-binary @jenkins-webhook-payload.json \
                          --max-time 15 || true

                        echo "n8n notified"
                    '''

                } catch (ex) {
                    echo "Final reporting failed: ${ex.message}"
                }
            }

            deleteDir()
        }

        success {
            echo 'Pipeline SUCCESS'
        }

        unstable {
            echo 'Pipeline UNSTABLE'
        }

        failure {
            echo 'Pipeline FAILED'
        }
    }
}
