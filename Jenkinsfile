pipeline {
    agent any

    tools {
        maven 'M3'
    }

    options {
        disableConcurrentBuilds()
        timeout(time: 3, unit: 'HOURS')
        buildDiscarder(logRotator(numToKeepStr: '15'))
    }

    parameters {
        booleanParam(
            name: 'JENKINS_HARD_GATE',
            defaultValue: false,
            description: 'Si true, Jenkins echoue lui-meme sur CVE critique. Par defaut false : enforcement delegue a la plateforme.'
        )
        string(
            name: 'CVSS_FAIL_THRESHOLD',
            defaultValue: '9.0',
            description: 'Seuil CVSS de blocage, utilise uniquement si JENKINS_HARD_GATE=true.'
        )
    }

    environment {
        N8N_API_KEY = credentials('N8N_API_KEY')
        NVD_API_KEY = credentials('NVD_API_KEY')
        SONAR_TOKEN = credentials('SONAR_TOKEN')

        APP_NAME   = 'pfe-app-test'
        IMAGE_NAME = 'pfe-app-test'

        BACKEND_URL     = 'http://pfe-backend:3001'
        N8N_WEBHOOK_URL = 'http://n8n:5678/webhook/jenkins-event'
        SONAR_HOST_URL  = 'http://sonarqube:9000'

        K8S_NAMESPACE = 'pfe-devsecops'
        KUBECONFIG    = '/var/jenkins_home/.kube/config'

        ZAP_IMAGE      = 'zaproxy/zap-stable:latest'
        ZAP_TARGET_URL = 'http://app-test:8080'

        IS_PR         = "${env.CHANGE_ID ? 'true' : 'false'}"
        BUILD_CONTEXT = "${env.CHANGE_ID ? 'pull_request' : 'branch'}"
    }

    stages {

        stage('Init') {
            steps {
                script {
                    env.IMAGE_TAG       = "${env.BUILD_NUMBER}"
                    env.REPORT_BASE     = "/shared/reports/${env.APP_NAME}/${env.BUILD_NUMBER}"
                    env.N8N_REPORT_BASE = "/home/node/.n8n-files/reports/${env.APP_NAME}/${env.BUILD_NUMBER}"
                }
                sh '''
                    set -e
                    mkdir -p "$REPORT_BASE"

                    echo "============================================"
                    echo " Job              : $JOB_NAME"
                    echo " Build            : #$BUILD_NUMBER"
                    echo " App              : $APP_NAME"
                    echo " Image            : $IMAGE_NAME:$IMAGE_TAG"
                    echo " Context          : $BUILD_CONTEXT"
                    echo " Is PR            : $IS_PR"
                    echo " Hard gate        : $JENKINS_HARD_GATE (seuil $CVSS_FAIL_THRESHOLD)"
                    echo " Jenkins reports  : $REPORT_BASE"
                    echo " n8n reports      : $N8N_REPORT_BASE"
                    echo " Backend URL      : $BACKEND_URL"
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
                    echo "Commit : $(git rev-parse --short HEAD || echo unknown)"
                    echo "Branch : $(git rev-parse --abbrev-ref HEAD || echo unknown)"
                '''
            }
        }

        stage('Build') {
            steps {
                echo '=== STAGE 2: Maven Build (tests skippes) ==='
                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    sh '''
                        set -e
                        mvn clean package -B -DskipTests=true -Djacoco.skip=true
                    '''
                }
            }
        }

        stage('SonarQube Analysis') {
            steps {
                echo '=== STAGE 3: SonarQube SAST ==='
                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    withSonarQubeEnv('sq1') {
                        script {
                            def prArgs = ''
                            if (env.CHANGE_ID) {
                                echo "SonarQube : mode Pull Request #${env.CHANGE_ID}"
                                prArgs = " -Dsonar.pullrequest.key=${env.CHANGE_ID}" +
                                         " -Dsonar.pullrequest.branch=${env.CHANGE_BRANCH}" +
                                         " -Dsonar.pullrequest.base=${env.CHANGE_TARGET}"
                            } else {
                                echo "SonarQube : mode branche standard"
                            }
                            sh """
                                set -e
                                mvn sonar:sonar -B \
                                  -DskipTests=true \
                                  -Djacoco.skip=true \
                                  -Dsonar.projectKey="\$APP_NAME" \
                                  -Dsonar.projectName="PFE App Test" \
                                  -Dsonar.host.url="\$SONAR_HOST_URL" \
                                  -Dsonar.token="\$SONAR_TOKEN" \
                                  ${prArgs}
                            """
                        }
                    }
                }
            }
        }

        stage('Docker Build') {
            steps {
                echo '=== STAGE 4: Docker Build ==='
                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    sh '''
                        set -e
                        docker build -t "$IMAGE_NAME:$IMAGE_TAG" .
                        docker tag "$IMAGE_NAME:$IMAGE_TAG" "$IMAGE_NAME:latest"

                        echo "=== Docker image creee ==="
                        docker images | grep "$IMAGE_NAME" || true
                    '''
                }
            }
        }

        stage('Trivy Scan') {
            options { timeout(time: 35, unit: 'MINUTES') }
            steps {
                echo '=== STAGE 5: Trivy (vulnerabilites + misconfig) ==='
                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    sh '''
                        set -e
                        BUILD="$BUILD_NUMBER"
                        TMP_DIR="/tmp/trivy-test-$BUILD"

                        rm -rf "$TMP_DIR"
                        mkdir -p "$TMP_DIR" "$REPORT_BASE"

                        echo "=== Cache Trivy persistant ==="
                        docker volume create trivy-cache >/dev/null || true

                        echo "=== Export de l image ==="
                        docker save "$IMAGE_NAME:$IMAGE_TAG" -o "$TMP_DIR/image.tar"

                        echo "=== Mise a jour de la base Trivy ==="
                        docker run --rm -v trivy-cache:/root/.cache \
                          aquasec/trivy:latest image --download-db-only || true

                        echo "=== Conteneur Trivy ==="
                        TRIVY_CID=$(docker create -v trivy-cache:/root/.cache \
                          --entrypoint sh aquasec/trivy:latest -c "sleep 1800")
                        docker start "$TRIVY_CID" >/dev/null
                        docker cp "$TMP_DIR/image.tar" "$TRIVY_CID:/image.tar"

                        echo "=== Scan Trivy ==="
                        docker exec "$TRIVY_CID" trivy image \
                          --input /image.tar \
                          --scanners vuln,misconfig \
                          --skip-db-update \
                          --skip-java-db-update \
                          --exit-code 0 \
                          --format json \
                          --severity CRITICAL,HIGH,MEDIUM \
                          --no-progress \
                          --timeout 30m \
                          --output /trivy-report.json || true

                        docker cp "$TRIVY_CID:/trivy-report.json" "$TMP_DIR/trivy-report.json" || true
                        docker rm -f "$TRIVY_CID" >/dev/null 2>&1 || true

                        if [ ! -s "$TMP_DIR/trivy-report.json" ]; then
                          echo '{"SchemaVersion":2,"Results":[],"status":"trivy_report_missing"}' > "$TMP_DIR/trivy-report.json"
                        fi

                        cp "$TMP_DIR/trivy-report.json" "$REPORT_BASE/trivy-report.json"
                        rm -rf "$TMP_DIR"

                        echo "=== Rapport Trivy final ==="
                        ls -lh "$REPORT_BASE/trivy-report.json" || true
                    '''
                }
            }
        }

        stage('OWASP Dependency Check') {
            options { timeout(time: 40, unit: 'MINUTES') }
            steps {
                echo '=== STAGE 6: OWASP Dependency-Check (SCA) ==='
                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    sh '''
                        set -e
                        ODC_VERSION="12.2.2"
                        ODC_DATA="/var/jenkins_home/dependency-check-data-v12"

                        mkdir -p "$REPORT_BASE" "$ODC_DATA"

                        if [ "$JENKINS_HARD_GATE" = "true" ]; then
                          FAIL_CVSS="$CVSS_FAIL_THRESHOLD"
                        else
                          FAIL_CVSS="11"
                        fi
                        echo "OWASP failBuildOnCVSS = $FAIL_CVSS"

                        {
                          echo "=== Etape 1 : mise a jour NVD (avec cle API) ==="
                          timeout 20m mvn org.owasp:dependency-check-maven:$ODC_VERSION:update-only \
                            -DdataDirectory="$ODC_DATA" \
                            -DnvdApiKey="$NVD_API_KEY" \
                            -DnvdApiDelay=2000 \
                            -DnvdMaxRetryCount=15 \
                            -DnvdValidForHours=168 \
                            -DretireJsAnalyzerEnabled=false \
                            -DnodeAuditAnalyzerEnabled=false \
                            -DossindexAnalyzerEnabled=false \
                            -B || true

                          echo "=== Etape 2 : scan des dependances (cache local) ==="
                          timeout 20m mvn org.owasp:dependency-check-maven:$ODC_VERSION:check \
                            -Dformat=ALL \
                            -DfailBuildOnCVSS="$FAIL_CVSS" \
                            -DfailOnError=false \
                            -DdataDirectory="$ODC_DATA" \
                            -DautoUpdate=false \
                            -DretireJsAnalyzerEnabled=false \
                            -DnodeAuditAnalyzerEnabled=false \
                            -DossindexAnalyzerEnabled=false \
                            -B || true
                        } > "$REPORT_BASE/owasp.log" 2>&1

                        echo "=== Fin de log OWASP ==="
                        tail -80 "$REPORT_BASE/owasp.log" || true

                        if [ -f target/dependency-check-report.json ]; then
                          cp target/dependency-check-report.json "$REPORT_BASE/dependency-check-report.json"
                        else
                          echo '{"dependencies":[],"status":"owasp_report_missing"}' > "$REPORT_BASE/dependency-check-report.json"
                        fi
                        [ -f target/dependency-check-report.html ] && cp target/dependency-check-report.html "$REPORT_BASE/" || true
                        [ -f target/dependency-check-report.xml ]  && cp target/dependency-check-report.xml  "$REPORT_BASE/" || true

                        echo "=== Rapports OWASP finaux ==="
                        ls -lh "$REPORT_BASE"/dependency-check-report.* || true
                    '''
                }
            }
        }

        stage('Kubernetes Target Check') {
            when { expression { env.CHANGE_ID == null } }
            steps {
                echo '=== STAGE 7: Verification cible Kubernetes ==='
                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    sh '''
                        set +e
                        export KUBECONFIG="$KUBECONFIG"
                        mkdir -p "$REPORT_BASE"

                        echo "=== kubectl (client) ==="
                        kubectl version --client
                        echo "=== Services ==="
                        kubectl get svc  -n "$K8S_NAMESPACE"
                        echo "=== Pods ==="
                        kubectl get pods -n "$K8S_NAMESPACE"
                        echo "=== Service app-test ==="
                        kubectl get svc app-test -n "$K8S_NAMESPACE"
                        true
                    '''
                }
            }
        }

        stage('ZAP DAST Scan') {
            when { expression { env.CHANGE_ID == null } }
            options { timeout(time: 40, unit: 'MINUTES') }
            steps {
                echo '=== STAGE 8: OWASP ZAP DAST (spider + active scan) ==='
                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    sh '''
                        set +e
                        export KUBECONFIG="$KUBECONFIG"
                        BUILD="$BUILD_NUMBER"
                        ZAP_POD="zap-scan-$BUILD"
                        mkdir -p "$REPORT_BASE"

                        echo "=== Acces Kubernetes ==="
                        if ! kubectl get svc -n "$K8S_NAMESPACE" >/dev/null 2>&1; then
                          echo '{"site":[],"status":"zap_k8s_unreachable"}' > "$REPORT_BASE/zap-report.json"
                          exit 0
                        fi

                        echo "=== Nettoyage ancien pod ==="
                        kubectl delete pod "$ZAP_POD" -n "$K8S_NAMESPACE" --ignore-not-found=true || true

                        echo "=== Lancement du pod ZAP (spider puis active scan) ==="
                        kubectl run "$ZAP_POD" \
                          -n "$K8S_NAMESPACE" \
                          --image="$ZAP_IMAGE" \
                          --image-pull-policy=IfNotPresent \
                          --restart=Never \
                          --command -- sh -lc '
                            mkdir -p /zap/wrk && cd /zap/wrk

                            /zap/zap.sh -daemon -host 0.0.0.0 -port 8090 \
                              -config api.disablekey=true \
                              -config api.addrs.addr.name=.* \
                              -config api.addrs.addr.regex=true \
                              -config database.recoverylog=false \
                              > /zap/wrk/zap.log 2>&1 &

                            python3 - <<PY
import urllib.request, urllib.parse, time, json, sys

base   = "http://127.0.0.1:8090"
target = "http://app-test:8080"

def call(path, params=None, timeout=30):
    url = base + path
    if params:
        url += "?" + urllib.parse.urlencode(params)
    return urllib.request.urlopen(url, timeout=timeout).read().decode()

for _ in range(120):
    try:
        call("/JSON/core/view/version/", timeout=3); print("ZAP_READY"); break
    except Exception:
        time.sleep(2)
else:
    print("ZAP_NOT_READY"); sys.exit(1)

try:
    call("/JSON/core/action/accessUrl/", {"url": target, "followRedirects": "true"})
    print("TARGET_ACCESSED")
except Exception as e:
    print("TARGET_ACCESS_ERROR", e)

try:
    sid = json.loads(call("/JSON/spider/action/scan/", {"url": target, "recurse": "true"}))["scan"]
    while True:
        st = json.loads(call("/JSON/spider/view/status/", {"scanId": sid}))["status"]
        if int(st) >= 100: break
        time.sleep(3)
    print("SPIDER_DONE")
except Exception as e:
    print("SPIDER_ERROR", e)

try:
    aid = json.loads(call("/JSON/ascan/action/scan/", {"url": target, "recurse": "true"}))["scan"]
    while True:
        st = json.loads(call("/JSON/ascan/view/status/", {"scanId": aid}))["status"]
        if int(st) >= 100: break
        time.sleep(5)
    print("ACTIVE_SCAN_DONE")
except Exception as e:
    print("ACTIVE_SCAN_ERROR", e)

try:
    open("/zap/wrk/zap-report.json","w").write(call("/OTHER/core/other/jsonreport/", timeout=60))
    print("JSON_REPORT_CREATED")
except Exception as e:
    print("JSON_REPORT_ERROR", e)
    open("/zap/wrk/zap-report.json","w").write(json.dumps({"site":[{"name":target,"alerts":[]}],"status":"zap_report_api_fallback"}))
try:
    open("/zap/wrk/zap-report.html","w").write(call("/OTHER/core/other/htmlreport/", timeout=60))
    print("HTML_REPORT_CREATED")
except Exception as e:
    print("HTML_REPORT_ERROR", e)
PY

                            ls -lh /zap/wrk || true
                            touch /zap/wrk/zap.done
                            sleep 3600
                          '

                        echo "=== Attente du rapport ZAP ==="
                        for i in $(seq 1 180); do
                          if kubectl exec "$ZAP_POD" -n "$K8S_NAMESPACE" -- test -f /zap/wrk/zap.done 2>/dev/null; then
                            echo "ZAP termine"; break
                          fi
                          echo "Attente ZAP... $i"; sleep 10
                        done

                        echo "=== Logs ZAP ==="
                        kubectl logs "$ZAP_POD" -n "$K8S_NAMESPACE" || true

                        echo "=== Recuperation des rapports ==="
                        kubectl cp "$K8S_NAMESPACE/$ZAP_POD:/zap/wrk/zap-report.json" "$REPORT_BASE/zap-report.json" || true
                        kubectl cp "$K8S_NAMESPACE/$ZAP_POD:/zap/wrk/zap-report.html" "$REPORT_BASE/zap-report.html" || true
                        kubectl cp "$K8S_NAMESPACE/$ZAP_POD:/zap/wrk/zap.log"        "$REPORT_BASE/zap.log"        || true

                        if [ ! -s "$REPORT_BASE/zap-report.json" ]; then
                          echo '{"site":[],"status":"zap_report_missing"}' > "$REPORT_BASE/zap-report.json"
                        fi

                        echo "=== Nettoyage pod ZAP ==="
                        kubectl delete pod "$ZAP_POD" -n "$K8S_NAMESPACE" --ignore-not-found=true || true
                        true
                    '''
                }
            }
        }
    }

    post {
        always {
            echo '=== REPORTING FINAL ==='
            script {
                try {
                    def buildStatus = currentBuild.currentResult ?: 'SUCCESS'

                    def event
                    if (env.CHANGE_ID) {
                        event = 'pr_validation'
                    } else {
                        event = buildStatus == 'SUCCESS'  ? 'pipeline_success'
                              : buildStatus == 'UNSTABLE' ? 'pipeline_unstable'
                              :                             'pipeline_failed'
                    }

                    def severityHint = buildStatus == 'FAILURE'  ? 'HIGH'
                                     : buildStatus == 'UNSTABLE' ? 'MEDIUM'
                                     :                             'LOW'

                    def branch = env.GIT_BRANCH?.replaceAll('origin/', '') ?: 'main'
                    def commit = env.GIT_COMMIT?.take(8) ?: 'unknown'

                    def exists = { p -> sh(script: "test -s '${p}'", returnStatus: true) == 0 }
                    def trivyAvailable = exists("${env.REPORT_BASE}/trivy-report.json")
                    def zapAvailable   = exists("${env.REPORT_BASE}/zap-report.json")
                    def owaspAvailable = exists("${env.REPORT_BASE}/dependency-check-report.json")

                    def payloadObject = [
                        event         : event,
                        job           : env.JOB_NAME,
                        build_number  : env.BUILD_NUMBER,
                        build_url     : env.BUILD_URL,
                        logs_url      : "${env.BUILD_URL}consoleText",
                        branch        : branch,
                        commit        : commit,
                        status        : buildStatus,
                        severity_hint : severityHint,
                        duration_ms   : currentBuild.duration,
                        pull_request  : env.CHANGE_ID ? [
                            number: env.CHANGE_ID,
                            branch: env.CHANGE_BRANCH,
                            target: env.CHANGE_TARGET,
                            url   : env.CHANGE_URL,
                            title : env.CHANGE_TITLE
                        ] : null,
                        reports: [
                            jenkinsBasePath: env.REPORT_BASE,
                            basePath       : env.N8N_REPORT_BASE,
                            trivyPath      : "${env.N8N_REPORT_BASE}/trivy-report.json",
                            zapPath        : "${env.N8N_REPORT_BASE}/zap-report.json",
                            owaspPath      : "${env.N8N_REPORT_BASE}/dependency-check-report.json",
                            available      : [ trivy: trivyAvailable, zap: zapAvailable, owasp: owaspAvailable ]
                        ],
                        sonar: [
                            project_key  : env.APP_NAME,
                            dashboard_url: "${env.SONAR_HOST_URL}/dashboard?id=${env.APP_NAME}"
                        ],
                        docker    : [ image: "${env.IMAGE_NAME}:${env.IMAGE_TAG}" ],
                        kubernetes: [ namespace: env.K8S_NAMESPACE, target: env.ZAP_TARGET_URL ]
                    ]

                    def payload = groovy.json.JsonOutput.prettyPrint(
                        groovy.json.JsonOutput.toJson(payloadObject)
                    )
                    writeFile file: 'jenkins-webhook-payload.json', text: payload

                    sh 'mkdir -p "$REPORT_BASE" && cp jenkins-webhook-payload.json "$REPORT_BASE/payload.json" || true'

                    def notified = false
                    for (int attempt = 1; attempt <= 3 && !notified; attempt++) {
                        echo "Notification n8n : tentative ${attempt}/3"
                        def code = sh(
                            returnStatus: true,
                            script: '''
                                curl -sS -o /tmp/n8n_resp.txt -w "%{http_code}" \
                                  -X POST "$N8N_WEBHOOK_URL" \
                                  -H "Content-Type: application/json" \
                                  -H "X-API-Key: $N8N_API_KEY" \
                                  --data-binary @jenkins-webhook-payload.json \
                                  --max-time 20 > /tmp/n8n_code.txt 2>/tmp/n8n_err.txt
                                CODE=$(cat /tmp/n8n_code.txt)
                                echo "HTTP $CODE"
                                case "$CODE" in 2*) exit 0 ;; *) exit 1 ;; esac
                            '''
                        )
                        if (code == 0) {
                            notified = true
                            echo 'n8n notifie avec succes'
                        } else {
                            echo "Echec tentative ${attempt}"
                            sleep(time: 5, unit: 'SECONDS')
                        }
                    }
                    if (!notified) {
                        echo 'ERREUR : n8n injoignable apres 3 tentatives.'
                        if (currentBuild.currentResult == 'SUCCESS') {
                            currentBuild.result = 'UNSTABLE'
                        }
                    }

                } catch (ex) {
                    echo "Reporting final en echec : ${ex.message}"
                }
            }

            deleteDir()
        }

        success  { echo 'Pipeline SUCCESS' }
        unstable { echo 'Pipeline UNSTABLE' }
        failure  { echo 'Pipeline FAILED' }
    }
}
