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

        APP_NAME = 'pfe-app-test'
        IMAGE_NAME = 'pfe-app-test'

        BACKEND_URL = 'http://172.31.172.61:3001'
        N8N_WEBHOOK_URL = 'http://n8n:5678/webhook/jenkins-event'
        SONAR_HOST_URL = 'http://sonarqube:9000'

        K8S_NAMESPACE = 'pfe-devsecops'
        KUBECONFIG = '/var/jenkins_home/.kube/config'

        ZAP_IMAGE = 'zaproxy/zap-stable:latest'
        ZAP_TARGET_URL = 'http://app-test:8080'
    }

    stages {

        stage('Init') {
            steps {
                script {
                    env.IMAGE_TAG = "${env.BUILD_NUMBER}"
                    env.REPORT_BASE = "/shared/reports/${env.APP_NAME}/${env.BUILD_NUMBER}"
                    env.N8N_REPORT_BASE = "/home/node/.n8n-files/reports/${env.APP_NAME}/${env.BUILD_NUMBER}"
                }

                sh '''
                    mkdir -p "$REPORT_BASE"

                    echo "============================================"
                    echo " Job              : $JOB_NAME"
                    echo " Build            : #$BUILD_NUMBER"
                    echo " App              : $APP_NAME"
                    echo " Image            : $IMAGE_NAME:$IMAGE_TAG"
                    echo " Jenkins reports  : $REPORT_BASE"
                    echo " n8n reports      : $N8N_REPORT_BASE"
                    echo " Kubeconfig       : $KUBECONFIG"
                    echo " ZAP target       : $ZAP_TARGET_URL"
                    echo "============================================"
                '''
            }
        }

        stage('Checkout') {
            steps {
                echo '=== STAGE 1: Checkout ==='

                checkout scm

                sh '''
                    echo "Commit: $(git rev-parse --short HEAD || true)"
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
            options {
                timeout(time: 35, unit: 'MINUTES')
            }

            steps {
                echo '=== STAGE 5: Trivy Security Scan ==='

                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    sh '''
                        BUILD="$BUILD_NUMBER"
                        TMP_DIR="/tmp/trivy-test-$BUILD"

                        rm -rf "$TMP_DIR"
                        mkdir -p "$TMP_DIR"
                        mkdir -p "$REPORT_BASE"

                        echo "=== Create persistent Trivy cache ==="
                        docker volume create trivy-cache || true

                        echo "=== Save Docker image ==="
                        docker save "$IMAGE_NAME:$IMAGE_TAG" -o "$TMP_DIR/image.tar"
                        ls -lh "$TMP_DIR/image.tar"

                        echo "=== Try to update Trivy vulnerability DB cache ==="
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

                        cp "$TMP_DIR/trivy-report.json" "$REPORT_BASE/trivy-report.json"

                        echo "=== Final Trivy report ==="
                        ls -lh "$REPORT_BASE/trivy-report.json" || true
                        head -c 500 "$REPORT_BASE/trivy-report.json" || true
                        echo ""
                    '''
                }
            }
        }

        stage('OWASP Dependency Check') {
    options {
        timeout(time: 75, unit: 'MINUTES')
    }

    steps {
        echo '=== STAGE 6: OWASP Dependency Check ==='

        catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
            sh '''
                MVN="/var/jenkins_home/tools/hudson.tasks.Maven_MavenInstallation/M3/bin/mvn"
                ODC_VERSION="12.2.2"
                ODC_DATA="/var/jenkins_home/dependency-check-data-v12"

                if [ ! -x "$MVN" ]; then
                  MVN=$(find /var/jenkins_home/tools -type f -name mvn | head -1)
                fi

                mkdir -p "$REPORT_BASE"
                mkdir -p "$ODC_DATA"

                echo "=== Maven check ==="
                "$MVN" -v || true

                echo "=== OWASP Dependency-Check version ==="
                echo "$ODC_VERSION"

                echo "=== Step 1: Update OWASP NVD cache WITHOUT API key ==="
                {
                  timeout 60m "$MVN" org.owasp:dependency-check-maven:$ODC_VERSION:update-only \
                    -DdataDirectory="$ODC_DATA" \
                    -DnvdApiDelay=10000 \
                    -DnvdMaxRetryCount=30 \
                    -DnvdValidForHours=168 \
                    -DretireJsAnalyzerEnabled=false \
                    -DnodeAuditAnalyzerEnabled=false \
                    -DossindexAnalyzerEnabled=false \
                    -DknownExploitedEnabled=false \
                    -DhostedSuppressionsEnabled=false \
                    -B || true

                  echo "=== Step 2: Run OWASP scan using local cache ==="

                  timeout 20m "$MVN" org.owasp:dependency-check-maven:$ODC_VERSION:check \
                    -Dformat=ALL \
                    -DfailBuildOnCVSS=11 \
                    -DfailOnError=false \
                    -DdataDirectory="$ODC_DATA" \
                    -DautoUpdate=false \
                    -DskipTests=true \
                    -DretireJsAnalyzerEnabled=false \
                    -DnodeAuditAnalyzerEnabled=false \
                    -DossindexAnalyzerEnabled=false \
                    -DknownExploitedEnabled=false \
                    -DhostedSuppressionsEnabled=false \
                    -B || true
                } > "$REPORT_BASE/owasp.log" 2>&1

                echo "=== OWASP log tail ==="
                tail -120 "$REPORT_BASE/owasp.log" || true

                echo "=== Copy OWASP reports ==="

                if [ -f target/dependency-check-report.json ]; then
                  cp target/dependency-check-report.json "$REPORT_BASE/dependency-check-report.json"
                else
                  echo '{"dependencies":[],"status":"owasp_report_missing"}' > "$REPORT_BASE/dependency-check-report.json"
                fi

                if [ -f target/dependency-check-report.html ]; then
                  cp target/dependency-check-report.html "$REPORT_BASE/dependency-check-report.html"
                fi

                if [ -f target/dependency-check-report.xml ]; then
                  cp target/dependency-check-report.xml "$REPORT_BASE/dependency-check-report.xml"
                fi

                echo "=== Final OWASP reports ==="
                ls -lh "$REPORT_BASE"/dependency-check-report.* "$REPORT_BASE/owasp.log" || true
                head -c 500 "$REPORT_BASE/dependency-check-report.json" || true
                echo ""
            '''
        }
    }
}
        stage('Kubernetes Target Check') {
            steps {
                echo '=== STAGE 7: Kubernetes Target Check ==='

                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    sh '''
                        export KUBECONFIG="$KUBECONFIG"

                        mkdir -p "$REPORT_BASE"

                        echo "=== kubectl version ==="
                        kubectl version --client || true

                        echo "=== Kubernetes services ==="
                        kubectl get svc -n "$K8S_NAMESPACE" || true

                        echo "=== Kubernetes pods ==="
                        kubectl get pods -n "$K8S_NAMESPACE" || true

                        echo "=== Check app-test service ==="
                        kubectl get svc app-test -n "$K8S_NAMESPACE" || true
                    '''
                }
            }
        }

        stage('ZAP DAST Scan') {
            options {
                timeout(time: 30, unit: 'MINUTES')
            }

            steps {
                echo '=== STAGE 8: OWASP ZAP DAST Scan inside Kubernetes ==='

                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    sh '''
                        export KUBECONFIG="$KUBECONFIG"

                        BUILD="$BUILD_NUMBER"
                        ZAP_POD="zap-scan-$BUILD"

                        mkdir -p "$REPORT_BASE"

                        echo "=== Check Kubernetes access ==="
                        kubectl get svc -n "$K8S_NAMESPACE" || {
                          echo '{"site":[],"status":"zap_k8s_unreachable"}' > "$REPORT_BASE/zap-report.json"
                          exit 0
                        }

                        echo "=== Clean old ZAP pod ==="
                        kubectl delete pod "$ZAP_POD" \
                          -n "$K8S_NAMESPACE" \
                          --ignore-not-found=true || true

                        echo "=== Run ZAP daemon pod ==="
                        kubectl run "$ZAP_POD" \
                          -n "$K8S_NAMESPACE" \
                          --image="$ZAP_IMAGE" \
                          --image-pull-policy=IfNotPresent \
                          --restart=Never \
                          --command -- sh -lc '
                            mkdir -p /zap/wrk
                            cd /zap/wrk

                            echo "=== Start ZAP daemon ==="
                            /zap/zap.sh \
                              -daemon \
                              -host 0.0.0.0 \
                              -port 8090 \
                              -config api.disablekey=true \
                              -config api.addrs.addr.name=.* \
                              -config api.addrs.addr.regex=true \
                              -config database.recoverylog=false \
                              > /zap/wrk/zap.log 2>&1 &

                            echo "=== Wait ZAP API ==="
                            python3 - <<PY
import urllib.request, time, sys

base = "http://127.0.0.1:8090"

for i in range(120):
    try:
        r = urllib.request.urlopen(base + "/JSON/core/view/version/", timeout=3)
        print("ZAP_READY", r.read().decode()[:100])
        sys.exit(0)
    except Exception:
        time.sleep(2)

print("ZAP_NOT_READY")
sys.exit(1)
PY

                            echo "=== Access target and generate reports ==="
                            python3 - <<PY
import urllib.request, urllib.parse, time, json

base = "http://127.0.0.1:8090"
target = "http://app-test:8080"

try:
    urllib.request.urlopen(
        base + "/JSON/core/action/accessUrl/?" + urllib.parse.urlencode({
            "url": target,
            "followRedirects": "true"
        }),
        timeout=30
    ).read()
    print("TARGET_ACCESSED")
except Exception as e:
    print("TARGET_ACCESS_ERROR", e)

time.sleep(10)

try:
    report = urllib.request.urlopen(
        base + "/OTHER/core/other/jsonreport/",
        timeout=60
    ).read()
    open("/zap/wrk/zap-report.json", "wb").write(report)
    print("JSON_REPORT_CREATED")
except Exception as e:
    print("JSON_REPORT_ERROR", e)
    fallback = {
        "site": [
            {
                "name": target,
                "alerts": []
            }
        ],
        "status": "zap_report_api_fallback"
    }
    open("/zap/wrk/zap-report.json", "w").write(json.dumps(fallback))

try:
    html = urllib.request.urlopen(
        base + "/OTHER/core/other/htmlreport/",
        timeout=60
    ).read()
    open("/zap/wrk/zap-report.html", "wb").write(html)
    print("HTML_REPORT_CREATED")
except Exception as e:
    print("HTML_REPORT_ERROR", e)

try:
    alerts = urllib.request.urlopen(
        base + "/JSON/core/view/alerts/?" + urllib.parse.urlencode({
            "baseurl": target
        }),
        timeout=30
    ).read()
    open("/zap/wrk/zap-alerts.json", "wb").write(alerts)
    print("ALERTS_REPORT_CREATED")
except Exception as e:
    print("ALERTS_REPORT_ERROR", e)
PY

                            echo "=== Files ==="
                            ls -lh /zap/wrk || true

                            touch /zap/wrk/zap.done
                            sleep 3600
                          '

                        echo "=== Wait ZAP report ==="
                        for i in $(seq 1 120); do
                          if kubectl exec "$ZAP_POD" -n "$K8S_NAMESPACE" -- test -f /zap/wrk/zap-report.json 2>/dev/null; then
                            echo "ZAP report created"
                            break
                          fi

                          echo "Waiting ZAP report... $i"
                          sleep 10
                        done

                        echo "=== ZAP pod logs ==="
                        kubectl logs "$ZAP_POD" -n "$K8S_NAMESPACE" || true

                        echo "=== Copy ZAP reports ==="
                        kubectl cp "$K8S_NAMESPACE/$ZAP_POD:/zap/wrk/zap-report.json" "$REPORT_BASE/zap-report.json" || true
                        kubectl cp "$K8S_NAMESPACE/$ZAP_POD:/zap/wrk/zap-report.html" "$REPORT_BASE/zap-report.html" || true
                        kubectl cp "$K8S_NAMESPACE/$ZAP_POD:/zap/wrk/zap-alerts.json" "$REPORT_BASE/zap-alerts.json" || true
                        kubectl cp "$K8S_NAMESPACE/$ZAP_POD:/zap/wrk/zap.log" "$REPORT_BASE/zap.log" || true

                        if [ ! -s "$REPORT_BASE/zap-report.json" ]; then
                          echo '{"site":[],"status":"zap_report_missing"}' > "$REPORT_BASE/zap-report.json"
                        fi

                        echo "=== Final ZAP report ==="
                        ls -lh "$REPORT_BASE"/zap* || true
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

                        kubernetes: [
                            namespace: env.K8S_NAMESPACE,
                            target   : env.ZAP_TARGET_URL
                        ]
                    ]

                    def payload = groovy.json.JsonOutput.prettyPrint(
                        groovy.json.JsonOutput.toJson(payloadObject)
                    )

                    writeFile file: 'jenkins-webhook-payload.json', text: payload

                    sh '''
                        mkdir -p "$REPORT_BASE"
                        cp jenkins-webhook-payload.json "$REPORT_BASE/payload.json" || true

                        echo "=== Shared reports final ==="
                        ls -la "$REPORT_BASE" || true

                        echo "=== Notify n8n ==="
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
