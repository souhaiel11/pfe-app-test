pipeline {
    agent any
    tools { maven 'M3' }
    environment {
        N8N_API_KEY    = credentials('N8N_API_KEY')
        NVD_API_KEY    = credentials('NVD_API_KEY')
        SONAR_TOKEN    = credentials('SONAR_TOKEN')
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
                    env.REPORT_DIR      = "/shared/reports/${env.JOB_NAME}/${env.BUILD_NUMBER}"
                    echo "============================================"
                    echo " Job     : ${env.JOB_NAME}"
                    echo " Build   : #${env.BUILD_NUMBER}"
                    echo " Reports : ${env.REPORT_DIR}"
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
        stage('Tests') {
            steps {
                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    echo '=== STAGE 3: Tests ==='
                    sh 'mvn test -B'
                }
            }
            post {
                always {
                    junit testResults: '**/target/surefire-reports/*.xml', allowEmptyResults: true
                }
            }
        }
        stage('SonarQube Analysis') {
            steps {
                echo '=== STAGE 4: SonarQube ==='
                withSonarQubeEnv('SonarQube') {
                    sh """
                        mvn sonar:sonar \
                          -Dsonar.projectKey=${env.APP_NAME} \
                          -Dsonar.host.url=${env.SONAR_HOST_URL} \
                          -Dsonar.login=${env.SONAR_TOKEN} \
                          -B
                    """
                }
            }
        }
        stage('Trivy Scan') {
            steps {
                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    echo '=== STAGE 5: Trivy Security Scan ==='
                    sh "docker build -t ${env.IMAGE_NAME}:${env.IMAGE_TAG} . || true"
                    sh """
                        docker run --rm \
                          -v /var/run/docker.sock:/var/run/docker.sock \
                          aquasec/trivy:latest image \
                            --exit-code 0 \
                            --format json \
                            --severity CRITICAL,HIGH,MEDIUM \
                            --no-progress \
                            ${env.IMAGE_NAME}:${env.IMAGE_TAG} > trivy-report.json 2>/dev/null || true
                        echo "Trivy termine"
                        ls -la trivy-report.json 2>/dev/null || echo "Rapport Trivy absent"
                        mkdir -p ${env.REPORT_DIR}
                        [ -f trivy-report.json ] && cp trivy-report.json ${env.REPORT_DIR}/ || true
                    """
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: 'trivy-report.json', allowEmptyArchive: true
                }
            }
        }
        stage('OWASP Dependency Check') {
            steps {
                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    echo '=== STAGE 6: OWASP Dependency Check ==='
                    sh """
                        mvn org.owasp:dependency-check-maven:check \
                          -DfailBuildOnCVSS=10 \
                          -DnvdApiKey=${env.NVD_API_KEY} \
                          -DnvdApiDelay=6000 \
                          -DfailOnError=false \
                          -Dformat=ALL \
                          -DdataDirectory=/tmp/dc-data \
                          -B || true
                        ls -la target/dependency-check-report.* 2>/dev/null || echo "OWASP: no report generated"
                        mkdir -p ${env.REPORT_DIR}
                        [ -f target/dependency-check-report.json ] && cp target/dependency-check-report.json ${env.REPORT_DIR}/owasp-report.json || true
                    """
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: 'target/dependency-check-report.*', allowEmptyArchive: true
                }
            }
        }
        stage('Docker Build') {
            steps {
                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    echo '=== STAGE 7: Docker Build ==='
                    sh "docker build -t ${env.IMAGE_NAME}:${env.IMAGE_TAG} ."
                }
            }
        }
        stage('Deploy to Minikube') {
            steps {
                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    echo '=== STAGE 8: Deploy to Minikube ==='
                    sh """
                        kubectl config use-context minikube || true
                        kubectl get ns ${env.K8S_NAMESPACE} || kubectl create ns ${env.K8S_NAMESPACE}
                        sed -i 's|image:.*|image: ${env.IMAGE_NAME}:${env.IMAGE_TAG}|' k8s/deployment.yaml || true
                        kubectl apply -f k8s/ -n ${env.K8S_NAMESPACE} || true
                        kubectl rollout status deployment/${env.APP_NAME} -n ${env.K8S_NAMESPACE} --timeout=120s || true
                    """
                }
            }
        }
        stage('ZAP DAST Scan') {
            steps {
                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    echo '=== STAGE 9: OWASP ZAP DAST ==='
                    sh """
                        sleep 15
                        ZAP_CID=\$(docker run -d \
                          --network=host \
                          --user root \
                          ghcr.io/zaproxy/zaproxy:stable \
                          zap-baseline.py \
                            -t ${env.ZAP_TARGET_URL} \
                            -J zap-report.json \
                            -r zap-report.html \
                            -I)
                        docker wait \$ZAP_CID || true
                        docker cp \$ZAP_CID:/zap/zap-report.json . 2>/dev/null || \
                        docker cp \$ZAP_CID:/zap/wrk/zap-report.json . 2>/dev/null || \
                        docker cp \$ZAP_CID:/home/zap/zap-report.json . 2>/dev/null || true
                        docker cp \$ZAP_CID:/zap/zap-report.html . 2>/dev/null || \
                        docker cp \$ZAP_CID:/zap/wrk/zap-report.html . 2>/dev/null || true
                        docker rm \$ZAP_CID 2>/dev/null || true
                        echo "ZAP termine"
                        ls -la zap-report.json 2>/dev/null || echo "Rapport ZAP absent"
                        mkdir -p ${env.REPORT_DIR}
                        [ -f zap-report.json ] && cp zap-report.json ${env.REPORT_DIR}/ || true
                        [ -f zap-report.html ] && cp zap-report.html ${env.REPORT_DIR}/ || true
                    """
                }
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
            script {
                try {
                    def branch = sh(script: 'git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "unknown"', returnStdout: true).trim()
                    def commit = sh(script: 'git rev-parse --short HEAD 2>/dev/null || echo "unknown"', returnStdout: true).trim()
                    def event = "build_${currentBuild.result ?: 'UNKNOWN'}"
                    def buildStatus = currentBuild.result ?: 'UNKNOWN'
                    def severity = buildStatus == 'FAILURE' ? 'HIGH' : buildStatus == 'UNSTABLE' ? 'MEDIUM' : 'LOW'

                    // ── Tests parsing ──
                    def testTotal = 0; def testFail = 0; def testSkip = 0; def testStatus = 'UNKNOWN'
                    try {
                        def tr = tm('**/target/surefire-reports/*.xml')
                        testTotal = tr.totalCount; testFail = tr.failCount; testSkip = tr.skipCount
                        testStatus = testFail > 0 ? 'FAILED' : testTotal > 0 ? 'PASSED' : 'NO_TESTS'
                    } catch(ex) {}

                    // ── Trivy parsing — extraire compteurs + top CVEs ──
                    def trivyCritical = 0; def trivyHigh = 0; def trivyCves = '[]'
                    try {
                        def td = new groovy.json.JsonSlurper().parseText(
                            readFile("${env.WORKSPACE}/trivy-report.json")
                        )
                        def cveList = []
                        td.Results?.each { r -> r.Vulnerabilities?.each { v ->
                            if (v.Severity == 'CRITICAL') trivyCritical++
                            if (v.Severity == 'HIGH')     trivyHigh++
                            if (cveList.size() < 10 && (v.Severity == 'CRITICAL' || v.Severity == 'HIGH')) {
                                cveList << [id: v.VulnerabilityID, severity: v.Severity, pkg: v.PkgName, title: (v.Title ?: '').take(80)]
                            }
                        }}
                        trivyCves = groovy.json.JsonOutput.toJson(cveList)
                    } catch(ex) {}

                    // ── ZAP parsing — extraire compteurs + top alertes ──
                    def zapHigh = 0; def zapMedium = 0; def zapLow = 0; def zapAlerts = '[]'
                    try {
                        def zd = new groovy.json.JsonSlurper().parseText(
                            readFile("${env.WORKSPACE}/zap-report.json")
                        )
                        def alertList = []
                        zd.site?.each { s -> s.alerts?.each { a ->
                            def risk = a.riskdesc?.split(' ')[0]
                            if (risk == 'High')   zapHigh++
                            if (risk == 'Medium') zapMedium++
                            if (risk == 'Low')    zapLow++
                            if (alertList.size() < 5 && (risk == 'High' || risk == 'Medium')) {
                                alertList << [name: (a.name ?: '').take(60), risk: risk, desc: (a.desc ?: '').take(80), solution: (a.solution ?: '').take(80)]
                            }
                        }}
                        zapAlerts = groovy.json.JsonOutput.toJson(alertList)
                    } catch(ex) {}

                    // ── OWASP parsing — extraire compteurs + top CVEs ──
                    def owaspCritical = 0; def owaspHigh = 0; def owaspCves = '[]'
                    try {
                        def od = new groovy.json.JsonSlurper().parseText(
                            readFile("${env.WORKSPACE}/target/dependency-check-report.json")
                        )
                        def owaspList = []
                        od.dependencies?.each { dep -> dep.vulnerabilities?.each { v ->
                            def sev = v.severity?.toUpperCase()
                            if (sev == 'CRITICAL') owaspCritical++
                            if (sev == 'HIGH')     owaspHigh++
                            if (owaspList.size() < 5 && (sev == 'CRITICAL' || sev == 'HIGH')) {
                                owaspList << [id: v.name, severity: sev, pkg: dep.fileName, desc: (v.description ?: '').take(80)]
                            }
                        }}
                        owaspCves = groovy.json.JsonOutput.toJson(owaspList)
                    } catch(ex) {}

                    // ── Webhook payload ──
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
                            "cves"       : ${trivyCves},
                            "report_url" : "${env.BUILD_URL}artifact/trivy-report.json"
                        },
                        "zap" : {
                            "alerts_high"   : ${zapHigh},
                            "alerts_medium" : ${zapMedium},
                            "alerts_low"    : ${zapLow},
                            "alerts"        : ${zapAlerts},
                            "target_url"    : "${env.ZAP_TARGET_URL}",
                            "report_url"    : "${env.BUILD_URL}artifact/zap-report.json"
                        },
                        "owasp" : {
                            "critical"   : ${owaspCritical},
                            "high"       : ${owaspHigh},
                            "cves"       : ${owaspCves},
                            "report_url" : "${env.BUILD_URL}artifact/target/dependency-check-report.json"
                        },
                        "docker" : { "image" : "${env.IMAGE_NAME}:${env.IMAGE_TAG}" },
                        "deploy" : { "namespace" : "${env.K8S_NAMESPACE}", "app_url" : "${env.ZAP_TARGET_URL}" },
                        "reports" : {
                            "trivyPath" : "${env.REPORT_DIR}/trivy-report.json",
                            "zapPath"   : "${env.REPORT_DIR}/zap-report.json",
                            "owaspPath" : "${env.REPORT_DIR}/owasp-report.json"
                        },
                        "tests" : {
                            "total"    : ${testTotal},
                            "failures" : ${testFail},
                            "skipped"  : ${testSkip},
                            "status"   : "${testStatus}"
                        }
                    }"""
                    sh """
                        curl -s -X POST '${env.N8N_WEBHOOK_URL}' \
                          -H 'Content-Type: application/json' \
                          -H 'X-API-Key: ${env.N8N_API_KEY}' \
                          -d '${payload.replaceAll("'", "\\\\'")}' \
                          --max-time 15 || true
                        echo "n8n notifie: ${event}"
                    """
                } catch(ex) {
                    echo "Webhook failed: ${ex.message}"
                }
            }
            deleteDir()
        }
        success  { echo 'Pipeline pfe-app-test SUCCESS' }
        unstable { echo 'Pipeline pfe-app-test UNSTABLE' }
        failure  { echo 'Pipeline pfe-app-test FAILED' }
    }
}
