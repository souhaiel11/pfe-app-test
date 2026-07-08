pipeline {
    agent any

    tools {
        maven 'M3'
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
                    env.APP_NAME        = 'pfe-app-test'
                    env.BACKEND_URL     = 'http://172.31.172.61:3001'
                    env.N8N_WEBHOOK_URL = 'http://n8n:5678/webhook/jenkins-event'
                    env.SONAR_HOST_URL  = 'http://sonarqube:9000'
                    env.ZAP_TARGET_URL  = 'http://192.168.49.2:30003'
                    env.K8S_NAMESPACE   = 'pfe-devsecops'
                    env.IMAGE_NAME      = 'pfe-app-test'
                    env.IMAGE_TAG       = "${env.BUILD_NUMBER}"
                    env.REPORT_BASE     = "/shared/reports/${env.APP_NAME}/${env.BUILD_NUMBER}"

                    sh """
                        mkdir -p '${env.REPORT_BASE}'
                        echo "Report base: ${env.REPORT_BASE}"
                    """

                    echo "============================================"
                    echo " Job       : ${env.JOB_NAME}"
                    echo " Build     : #${env.BUILD_NUMBER}"
                    echo " Commit    : ${env.GIT_COMMIT ?: 'unknown'}"
                    echo " Reports   : ${env.REPORT_BASE}"
                    echo "============================================"
                }
            }
        }

        stage('Checkout') {
            steps {
                echo '=== STAGE 1: Checkout ==='
                checkout scm
                sh """
                    echo "Commit: \$(git rev-parse --short HEAD)"
                    echo "Branch: \$(git rev-parse --abbrev-ref HEAD || true)"
                """
            }
        }

        stage('Build Without Tests') {
            steps {
                echo '=== STAGE 2: Maven Build Without Tests ==='
                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    sh """
                        mvn clean package -B \
                          -DskipTests=true \
                          -Djacoco.skip=true || true
                    """
                }
            }
        }

        stage('SonarQube Analysis') {
            steps {
                echo '=== STAGE 3: SonarQube SAST ==='
                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    withSonarQubeEnv('sq1') {
                        sh """
                            mvn sonar:sonar -B \
                              -DskipTests=true \
                              -Djacoco.skip=true \
                              -Dsonar.projectKey=${env.APP_NAME} \
                              -Dsonar.projectName='PFE App Test' \
                              -Dsonar.host.url=${env.SONAR_HOST_URL} \
                              -Dsonar.token=${env.SONAR_TOKEN} || true
                        """
                    }
                }
            }
        }

        stage('Docker Build') {
            steps {
                echo '=== STAGE 4: Docker Build ==='
                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    sh """
                        docker build -t ${env.IMAGE_NAME}:${env.IMAGE_TAG} . || true
                        docker tag ${env.IMAGE_NAME}:${env.IMAGE_TAG} ${env.IMAGE_NAME}:latest || true
                    """
                }
            }
        }

        stage('Trivy Scan') {
            steps {
                echo '=== STAGE 5: Trivy Security Scan ==='
                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    sh """
                        docker run --rm \
                          -v /var/run/docker.sock:/var/run/docker.sock \
                          aquasec/trivy:latest image \
                            --exit-code 0 \
                            --format json \
                            --severity CRITICAL,HIGH,MEDIUM \
                            --no-progress \
                            ${env.IMAGE_NAME}:${env.IMAGE_TAG} > trivy-report.json 2>/dev/null || true

                        if [ ! -s trivy-report.json ]; then
                          echo '{"SchemaVersion":2,"Results":[],"status":"trivy_report_missing"}' > trivy-report.json
                        fi

                        cp trivy-report.json '${env.REPORT_BASE}/trivy-report.json' || true

                        echo "=== Trivy copied ==="
                        ls -la '${env.REPORT_BASE}/trivy-report.json' || true
                    """
                }
            }
        }

        stage('OWASP Dependency Check') {
            steps {
                echo '=== STAGE 6: OWASP Dependency Check ==='
                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    sh """
                        timeout 20m mvn org.owasp:dependency-check-maven:check \
                          -DfailBuildOnCVSS=10 \
                          -DnvdApiKey=${env.NVD_API_KEY} \
                          -DnvdApiDelay=6000 \
                          -DfailOnError=false \
                          -Dformat=ALL \
                          -DdataDirectory=/tmp/dc-data \
                          -DskipTests=true \
                          -B || true

                        if [ -f target/dependency-check-report.json ]; then
                          cp target/dependency-check-report.json '${env.REPORT_BASE}/dependency-check-report.json' || true
                        else
                          echo '{"dependencies":[],"status":"owasp_report_missing"}' > '${env.REPORT_BASE}/dependency-check-report.json'
                        fi

                        if [ -f target/dependency-check-report.html ]; then
                          cp target/dependency-check-report.html '${env.REPORT_BASE}/dependency-check-report.html' || true
                        fi

                        echo "=== OWASP copied ==="
                        ls -la '${env.REPORT_BASE}/dependency-check-report.json' || true
                    """
                }
            }
        }

        stage('Deploy to Minikube') {
            steps {
                echo '=== STAGE 7: Deploy Kubernetes ==='
                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
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
        }

        stage('ZAP DAST Scan') {
            options {
                timeout(time: 15, unit: 'MINUTES')
            }

            steps {
                echo '=== STAGE 8: OWASP ZAP DAST ==='
                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    sh """
                        sleep 10

                        echo "Testing ZAP target..."
                        curl -I --max-time 10 ${env.ZAP_TARGET_URL} || true

                        ZAP_CID=\$(docker run -d \
                          --network=host \
                          --user root \
                          ghcr.io/zaproxy/zaproxy:stable \
                          zap-baseline.py \
                            -t ${env.ZAP_TARGET_URL} \
                            -J zap-report.json \
                            -r zap-report.html \
                            -I || true)

                        echo "ZAP container: \$ZAP_CID"

                        if [ -n "\$ZAP_CID" ]; then
                          timeout 12m docker wait \$ZAP_CID || true

                          docker cp \$ZAP_CID:/zap/zap-report.json . 2>/dev/null || \
                          docker cp \$ZAP_CID:/zap/wrk/zap-report.json . 2>/dev/null || \
                          docker cp \$ZAP_CID:/home/zap/zap-report.json . 2>/dev/null || true

                          docker cp \$ZAP_CID:/zap/zap-report.html . 2>/dev/null || \
                          docker cp \$ZAP_CID:/zap/wrk/zap-report.html . 2>/dev/null || \
                          docker cp \$ZAP_CID:/home/zap/zap-report.html . 2>/dev/null || true

                          docker rm -f \$ZAP_CID 2>/dev/null || true
                        fi

                        if [ ! -s zap-report.json ]; then
                          echo '{"site":[],"status":"zap_report_missing"}' > zap-report.json
                        fi

                        cp zap-report.json '${env.REPORT_BASE}/zap-report.json' || true

                        if [ -f zap-report.html ]; then
                          cp zap-report.html '${env.REPORT_BASE}/zap-report.html' || true
                        fi

                        echo "=== ZAP copied ==="
                        ls -la '${env.REPORT_BASE}/zap-report.json' || true
                    """
                }
            }
        }
    }

    post {
        always {
            echo "=== FINAL REPORTING ==="

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

                    def trivyPath = "${env.REPORT_BASE}/trivy-report.json"
                    def zapPath   = "${env.REPORT_BASE}/zap-report.json"
                    def owaspPath = "${env.REPORT_BASE}/dependency-check-report.json"

                    def trivyCritical = 0
                    def trivyHigh = 0
                    def trivyCves = []

                    try {
                        if (fileExists('trivy-report.json')) {
                            def td = new groovy.json.JsonSlurper().parseText(readFile('trivy-report.json'))

                            td.Results?.each { r ->
                                r.Vulnerabilities?.each { v ->
                                    if (v.Severity == 'CRITICAL') trivyCritical++
                                    if (v.Severity == 'HIGH') trivyHigh++

                                    if (trivyCves.size() < 10 && (v.Severity == 'CRITICAL' || v.Severity == 'HIGH')) {
                                        trivyCves << [
                                            id      : v.VulnerabilityID,
                                            severity: v.Severity,
                                            pkg     : v.PkgName,
                                            title   : (v.Title ?: '').take(80)
                                        ]
                                    }
                                }
                            }
                        }
                    } catch (ex) {
                        echo "Trivy parse failed: ${ex.message}"
                    }

                    def zapHigh = 0
                    def zapMedium = 0
                    def zapLow = 0
                    def zapAlerts = []

                    try {
                        if (fileExists('zap-report.json')) {
                            def zd = new groovy.json.JsonSlurper().parseText(readFile('zap-report.json'))

                            zd.site?.each { s ->
                                s.alerts?.each { a ->
                                    def risk = a.riskdesc?.split(' ')[0]

                                    if (risk == 'High') zapHigh++
                                    if (risk == 'Medium') zapMedium++
                                    if (risk == 'Low') zapLow++

                                    if (zapAlerts.size() < 5 && (risk == 'High' || risk == 'Medium')) {
                                        zapAlerts << [
                                            name    : (a.name ?: '').take(60),
                                            risk    : risk,
                                            desc    : (a.desc ?: '').take(80),
                                            solution: (a.solution ?: '').take(80)
                                        ]
                                    }
                                }
                            }
                        }
                    } catch (ex) {
                        echo "ZAP parse failed: ${ex.message}"
                    }

                    def owaspCritical = 0
                    def owaspHigh = 0
                    def owaspCves = []

                    try {
                        if (fileExists('target/dependency-check-report.json')) {
                            def od = new groovy.json.JsonSlurper().parseText(readFile('target/dependency-check-report.json'))

                            od.dependencies?.each { dep ->
                                dep.vulnerabilities?.each { v ->
                                    def sev = v.severity?.toUpperCase()

                                    if (sev == 'CRITICAL') owaspCritical++
                                    if (sev == 'HIGH') owaspHigh++

                                    if (owaspCves.size() < 5 && (sev == 'CRITICAL' || sev == 'HIGH')) {
                                        owaspCves << [
                                            id      : v.name,
                                            severity: sev,
                                            pkg     : dep.fileName,
                                            desc    : (v.description ?: '').take(80)
                                        ]
                                    }
                                }
                            }
                        }
                    } catch (ex) {
                        echo "OWASP parse failed: ${ex.message}"
                    }

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
                            basePath : env.REPORT_BASE,
                            trivyPath: trivyPath,
                            zapPath  : zapPath,
                            owaspPath: owaspPath
                        ],

                        sonar: [
                            project_key  : env.APP_NAME,
                            dashboard_url: "${env.SONAR_HOST_URL}/dashboard?id=${env.APP_NAME}"
                        ],

                        trivy: [
                            critical   : trivyCritical,
                            high       : trivyHigh,
                            cves       : trivyCves,
                            report_path: trivyPath
                        ],

                        zap: [
                            alerts_high  : zapHigh,
                            alerts_medium: zapMedium,
                            alerts_low   : zapLow,
                            alerts       : zapAlerts,
                            target_url   : env.ZAP_TARGET_URL,
                            report_path  : zapPath
                        ],

                        owasp: [
                            critical   : owaspCritical,
                            high       : owaspHigh,
                            cves       : owaspCves,
                            report_path: owaspPath
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

                    sh """
                        cp jenkins-webhook-payload.json '${env.REPORT_BASE}/payload.json' || true

                        echo "=== Shared reports final ==="
                        ls -la '${env.REPORT_BASE}' || true

                        curl -s -X POST '${env.N8N_WEBHOOK_URL}' \
                          -H 'Content-Type: application/json' \
                          -H 'X-API-Key: ${env.N8N_API_KEY}' \
                          --data-binary @jenkins-webhook-payload.json \
                          --max-time 15 || true

                        echo "✅ n8n notified: ${event}"
                    """

                } catch (ex) {
                    echo "Final reporting failed: ${ex.message}"
                }
            }

            deleteDir()
        }
    }
}
