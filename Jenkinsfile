// ============================================================
//  PFE DevSecOps 2026 — Jenkinsfile Professionnel
//  Projet : pfe-devsecops-2026
//  Auteur  : Amri Souhaiel
//  Version : 2.0
// ============================================================

def COLOR_MAP = [
  SUCCESS : 'good',
  UNSTABLE: 'warning',
  FAILURE : 'danger'
]

pipeline {
  agent any

  tools { maven 'M3' }

  // ── Options globales ──────────────────────────────────────
  options {
    timeout(time: 45, unit: 'MINUTES')
    buildDiscarder(logRotator(numToKeepStr: '15'))
    disableConcurrentBuilds()

  }

  // ── Variables d'environnement ─────────────────────────────
  environment {
    SONAR_PROJECT_KEY = 'pfe-devsecops-2026'
    DOCKER_IMAGE      = 'pfe-devsecops-2026'
    APP_NAME          = 'devsecops-app'
    APP_PORT          = '8080'
    N8N_WEBHOOK       = 'http://n8n:5678/webhook/jenkins-event'
    N8N_API_KEY       = 'devsecops-secret-2024'
    PROJECT_ID        = '54192eca-43da-4d8f-9b49-30c143983fdd'
    BACKEND_URL       = 'http://172.31.172.61:3001'
    DOCKER_NETWORK    = 'pfe-network'
    REGISTRY_URL      = 'nexus:8081'
  }

  stages {

    // ── 1. Informations du build ──────────────────────────────
    stage('📋 Build Info') {
      steps {
        script {
          echo "============================================"
          echo " Job       : ${env.JOB_NAME}"
          echo " Build     : #${env.BUILD_NUMBER}"
          echo " Branch    : ${env.GIT_BRANCH ?: 'unknown'}"
          echo " Commit    : ${env.GIT_COMMIT?.take(8) ?: 'unknown'}"
          echo " Workspace : ${env.WORKSPACE}"
          echo "============================================"
          env.BUILD_BRANCH = env.GIT_BRANCH?.replaceAll('origin/', '') ?: 'unknown'
          env.BUILD_VERSION = "${env.BUILD_NUMBER}"
          env.IMAGE_TAG     = "${env.BUILD_BRANCH}-${env.BUILD_NUMBER}"
        }
      }
    }

    // ── 2. Nettoyage ─────────────────────────────────────────
    stage('🧹 Clean') {
      steps {
        dir('app') {
          sh 'mvn clean -q'
        }
      }
    }

    // ── 3. Compilation ───────────────────────────────────────
    stage('⚙️ Compile') {
      steps {
        dir('app') {
          sh 'mvn compile -q'
        }
      }
      post {
        failure {
          script { env.COMPILE_STATUS = 'FAILED' }
        }
        success {
          script { env.COMPILE_STATUS = 'SUCCESS' }
        }
      }
    }

    // ── 4. Tests unitaires + Couverture JaCoCo ───────────────
stage('🧪 Tests + Coverage') {
  steps {
    dir('app') {
      sh 'mvn test jacoco:report'
    }
  }

  post {
    always {
      junit allowEmptyResults: true,
            testResults: 'app/target/surefire-reports/*.xml'

      script {
        def total = sh(
          script: "grep -h '<testsuite' app/target/surefire-reports/*.xml | sed -n 's/.*tests=\"\\([0-9]*\\)\".*/\\1/p' | awk '{s+=\$1} END {print s+0}'",
          returnStdout: true
        ).trim()

        def failures = sh(
          script: "grep -h '<testsuite' app/target/surefire-reports/*.xml | sed -n 's/.*failures=\"\\([0-9]*\\)\".*/\\1/p' | awk '{s+=\$1} END {print s+0}'",
          returnStdout: true
        ).trim()

        def skipped = sh(
          script: "grep -h '<testsuite' app/target/surefire-reports/*.xml | sed -n 's/.*skipped=\"\\([0-9]*\\)\".*/\\1/p' | awk '{s+=\$1} END {print s+0}'",
          returnStdout: true
        ).trim()

        env.TEST_TOTAL    = total
        env.TEST_FAILURES = failures
        env.TEST_SKIPPED  = skipped
        env.TEST_STATUS   = (failures == '0') ? 'PASSED' : 'FAILED'

        echo "Tests: ${env.TEST_TOTAL} total, ${env.TEST_FAILURES} failures, ${env.TEST_SKIPPED} skipped"
      }
    }

    failure {
      script {
        env.TEST_STATUS = 'FAILED'
      }
    }
  }
}

    // ── 5. Packaging ─────────────────────────────────────────
    stage('📦 Package') {
      steps {
        dir('app') {
          sh 'mvn package -DskipTests -q'
          script {
            def jar = sh(
              script: "ls target/*.jar | grep -v original | head -1",
              returnStdout: true
            ).trim()
            env.JAR_NAME = jar.replaceAll('target/', '')
            echo "JAR : ${env.JAR_NAME}"
          }
        }
      }
    }

    // ── 6. Analyse SAST — SonarQube ──────────────────────────
    stage('🔍 SAST - SonarQube') {
      options {
        catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE')
      }
      steps {
        dir('app') {
          withSonarQubeEnv('sq1') {
            sh """
              /var/jenkins_home/tools/hudson.tasks.Maven_MavenInstallation/M3/bin/mvn \
              org.sonarsource.scanner.maven:sonar-maven-plugin:4.0.0.4121:sonar \
              -Dsonar.projectKey=${env.SONAR_PROJECT_KEY} \
              -Dsonar.projectVersion=${env.BUILD_VERSION}
              
             
            """
          }
          script { env.SONAR_STATUS = 'SUCCESS' }
        }
      }
      post {
        failure {
          script { env.SONAR_STATUS = 'FAILED' }
        }
      }
    }

    // ── 7. Quality Gate SonarQube ────────────────────────────
    stage('⏳ Quality Gate') {
      options {
        catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE')
        timeout(time: 5, unit: 'MINUTES')
      }
      steps {
        script {
          def qg = waitForQualityGate()
          env.QUALITY_GATE_STATUS = qg.status
          if (qg.status != 'OK') {
            echo "Quality Gate failed: ${qg.status} — continuing pipeline"
          } else {
            echo "Quality Gate passed: ${qg.status}"
          }
        }
      }
    }

    // ── 8. SCA — OWASP Dependency Check ─────────────────────
    stage('🛡️ SCA - OWASP') {
      options {
        catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE')
      }
      steps {
        dir('app') {
          script {
            def result = sh(
              script: """
                mvn org.owasp:dependency-check-maven:check \
                  -DfailBuildOnCVSS=7 \
                  -DnvdApiKey=14EB33B7-AB3A-F111-836A-129478FCB64D \
                  -Dformats=HTML,JSON 2>&1 || true
              """,
              returnStdout: true
            ).trim()

            if (result.contains('NVD Returned Status Code: 403')) {
              env.OWASP_STATUS = 'FAILED'
              env.OWASP_ERROR  = 'NVD API 403 - API key expired or invalid'
            } else if (result.contains('BUILD SUCCESS')) {
              env.OWASP_STATUS = 'SUCCESS'
              env.OWASP_ERROR  = ''
            } else {
              env.OWASP_STATUS = 'FAILED'
              env.OWASP_ERROR  = 'Dependency check failed'
            }
            echo "OWASP: ${env.OWASP_STATUS}"
          }
        }
      }
      post {
        always {
          publishHTML(target: [
            reportDir  : 'app/target',
            reportFiles: 'dependency-check-report.html',
            reportName : 'OWASP Dependency Check'
          ])
        }
      }
    }

    // ── 9. Déploiement Nexus ─────────────────────────────────
    stage('📤 Deploy Nexus') {
      options {
        catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE')
      }
      steps {
        dir('app') {
          script {
            def result = sh(
              script: 'mvn deploy -DskipTests -Ddependency-check.skip=true --settings /var/jenkins_home/.m2/settings.xml 2>&1',
              returnStdout: true
            ).trim()
            env.NEXUS_STATUS = result.contains('BUILD SUCCESS') ? 'SUCCESS' : 'FAILED'
            echo "Nexus: ${env.NEXUS_STATUS}"
          }
        }
      }
    }

    // ── 10. Build Docker ──────────────────────────────────────
    stage('🐳 Build Docker') {
      steps {
        dir('app') {
          script {
            sh """
              docker build \
                --build-arg JAR_FILE=${env.JAR_NAME} \
                --build-arg BUILD_NUMBER=${env.BUILD_NUMBER} \
                --build-arg BUILD_BRANCH=${env.BUILD_BRANCH} \
                -t ${DOCKER_IMAGE}:${env.IMAGE_TAG} \
                -t ${DOCKER_IMAGE}:latest \
                .
            """
            env.DOCKER_BUILD_STATUS = 'SUCCESS'
            env.DOCKER_IMAGE_TAG    = "${DOCKER_IMAGE}:${env.IMAGE_TAG}"
            echo "Image built: ${env.DOCKER_IMAGE_TAG}"
          }
        }
      }
      post {
        failure {
          script { env.DOCKER_BUILD_STATUS = 'FAILED' }
        }
      }
    }

    // ── 11. Scan Trivy ────────────────────────────────────────
    stage('🔒 Container Scan - Trivy') {
      options {
        catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE')
      }
      steps {
        script {
          def trivyResult = sh(
            script: """
              trivy image \
                --exit-code 0 \
                --severity CRITICAL,HIGH \
                --format json \
                --output trivy-report.json \
                ${DOCKER_IMAGE}:latest 2>&1 || true
            """,
            returnStdout: true
          ).trim()

          if (trivyResult.contains('not found') || trivyResult.contains('command not found')) {
            env.TRIVY_STATUS   = 'FAILED'
            env.TRIVY_ERROR    = 'trivy: command not found - not installed on Jenkins agent'
            env.TRIVY_CRITICAL = '0'
            env.TRIVY_HIGH     = '0'
          } else {
            try {
              def report        = new groovy.json.JsonSlurper().parseText(readFile('trivy-report.json'))
              def criticalCount = 0
              def highCount     = 0
              report.Results?.each { r ->
                r.Vulnerabilities?.each { v ->
                  if (v.Severity == 'CRITICAL') criticalCount++
                  if (v.Severity == 'HIGH')     highCount++
                }
              }
              env.TRIVY_CRITICAL = "${criticalCount}"
              env.TRIVY_HIGH     = "${highCount}"
              env.TRIVY_STATUS   = criticalCount > 0
                ? "FAILED - ${criticalCount} CRITICAL CVEs"
                : 'SUCCESS'
              env.TRIVY_ERROR    = criticalCount > 0
                ? "${criticalCount} critical CVEs in Docker image"
                : ''
              echo "Trivy: CRITICAL=${criticalCount}, HIGH=${highCount}"
            } catch(e) {
              env.TRIVY_STATUS   = 'FAILED'
              env.TRIVY_ERROR    = "Parse error: ${e.message}"
              env.TRIVY_CRITICAL = '0'
              env.TRIVY_HIGH     = '0'
            }
          }
        }
      }
      post {
        always {
          archiveArtifacts artifacts: 'trivy-report.json', allowEmptyArchive: true
        }
      }
    }

    // ── 12. Push Docker Hub ───────────────────────────────────
    stage('📤 Push Docker Hub') {
      when {
        expression { env.TRIVY_CRITICAL == '0' || env.TRIVY_CRITICAL == null }
      }
      steps {
        withCredentials([usernamePassword(
          credentialsId   : 'dockerHub',
          usernameVariable: 'DOCKER_HUB_USER',
          passwordVariable: 'DOCKER_HUB_PASS'
        )]) {
          sh """
            echo \$DOCKER_HUB_PASS | docker login -u \$DOCKER_HUB_USER --password-stdin
            docker tag ${DOCKER_IMAGE}:latest \$DOCKER_HUB_USER/${DOCKER_IMAGE}:${env.IMAGE_TAG}
            docker tag ${DOCKER_IMAGE}:latest \$DOCKER_HUB_USER/${DOCKER_IMAGE}:latest
            docker push \$DOCKER_HUB_USER/${DOCKER_IMAGE}:${env.IMAGE_TAG}
            docker push \$DOCKER_HUB_USER/${DOCKER_IMAGE}:latest
          """
        }
        script { env.DOCKER_PUSH_STATUS = 'SUCCESS' }
      }
      post {
        failure {
          script { env.DOCKER_PUSH_STATUS = 'FAILED' }
        }
      }
    }

    // ── 13. Déploiement application ───────────────────────────
    stage('🚀 Deploy App') {
      steps {
        script {
          sh """
            docker stop ${APP_NAME} 2>/dev/null || true
            docker rm   ${APP_NAME} 2>/dev/null || true
            docker run -d \
              --name ${APP_NAME} \
              --network ${DOCKER_NETWORK} \
              --restart unless-stopped \
              -p ${APP_PORT}:8080 \
              -e DB_HOST=pfe-postgres \
              -e DB_PORT=5432 \
              -e DB_NAME=devsecops \
              -e DB_USER=devsecops \
              -e DB_PASSWORD=devsecops123 \
              -e APP_VERSION=${env.IMAGE_TAG} \
              -l build.number=${env.BUILD_NUMBER} \
              -l build.branch=${env.BUILD_BRANCH} \
              ${DOCKER_IMAGE}:latest
          """
          env.DEPLOY_STATUS = 'SUCCESS'
          echo "Application déployée : ${DOCKER_IMAGE}:${env.IMAGE_TAG}"
        }
      }
      post {
        failure {
          script { env.DEPLOY_STATUS = 'FAILED' }
        }
      }
    }

    // ── 14. DAST — OWASP ZAP ─────────────────────────────────
    stage('🌐 DAST - OWASP ZAP') {
      options {
        catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE')
      }
      steps {
        sh """
          sleep 15
          docker run --rm \
            --network ${DOCKER_NETWORK} \
            -v \$(pwd)/security/zap:/zap/wrk:rw --user root \
            ghcr.io/zaproxy/zaproxy:stable \
            zap-baseline.py \
            -t http://${APP_NAME}:8080 \
            -r zap-report.html \
            -I || true
        """
      }
      post {
        always {
          publishHTML(target: [
            reportDir  : 'security/zap',
            reportFiles: 'zap-report.html',
            reportName : 'OWASP ZAP DAST'
          ])
        }
      }
    }
  }

  // ── Post — Notifications & Webhook n8n ───────────────────
  post {
    always {
      script {
        def buildStatus = currentBuild.currentResult
        def event       = buildStatus == 'SUCCESS'  ? 'pipeline_success'
                        : buildStatus == 'UNSTABLE' ? 'pipeline_unstable'
                        : 'pipeline_failed'
        def severity    = buildStatus == 'FAILURE'  ? 'HIGH'
                        : buildStatus == 'UNSTABLE' ? 'MEDIUM' : 'LOW'

        def branch = env.GIT_BRANCH?.replaceAll('origin/', '') ?: 'unknown'

        // ── Webhook n8n — Intelligence déléguée ──────────────
        // n8n récupère automatiquement les logs Jenkins et les métriques SonarQube
        try {
          def payload = """{
            "event"             : "${event}",
            "job"               : "${env.JOB_NAME}",
            "build_number"      : "${env.BUILD_NUMBER}",
            "build_url"         : "${env.BUILD_URL}",
            "logs_url"          : "${env.BUILD_URL}consoleText",
            "branch"            : "${branch}",
            "commit"            : "${env.GIT_COMMIT?.take(8) ?: 'unknown'}",
            "status"            : "${buildStatus}",
            "severity"          : "${severity}",
            "project_id"        : "${env.PROJECT_ID}",
            "sonar_project_key" : "${env.SONAR_PROJECT_KEY}",
            "image_tag"         : "${env.IMAGE_TAG ?: 'latest'}",
            "quality_gate"      : "${env.QUALITY_GATE_STATUS ?: 'UNKNOWN'}",
            "duration_ms"       : ${currentBuild.duration}
          }"""

          httpRequest(
            url                : env.N8N_WEBHOOK,
            httpMode           : 'POST',
            contentType        : 'APPLICATION_JSON',
            requestBody        : payload,
            customHeaders      : [[name: 'X-API-Key', value: env.N8N_API_KEY]],
            ignoreSslErrors    : true,
            validResponseCodes : '100:599',
            timeout            : 30
          )
          echo "✅ n8n notifié : ${event}"
        } catch (Exception e) {
          echo "⚠️ n8n failed: ${e.message}"
        }

        // ── Email ─────────────────────────────────────────────
        try {
          emailext(
            subject : "[${buildStatus}] ${env.JOB_NAME} #${env.BUILD_NUMBER} — ${branch}",
            body    : """
              <h2>Build ${buildStatus}</h2>
              <table>
                <tr><td><b>Projet</b></td><td>${env.JOB_NAME}</td></tr>
                <tr><td><b>Build</b></td><td>#${env.BUILD_NUMBER}</td></tr>
                <tr><td><b>Branche</b></td><td>${branch}</td></tr>
                <tr><td><b>Commit</b></td><td>${env.GIT_COMMIT?.take(8) ?: 'N/A'}</td></tr>
                <tr><td><b>Tests</b></td><td>${env.TEST_TOTAL ?: '?'} total, ${env.TEST_FAILURES ?: '?'} failures</td></tr>
                <tr><td><b>Quality Gate</b></td><td>${env.QUALITY_GATE_STATUS ?: 'N/A'}</td></tr>
                <tr><td><b>Trivy CRITICAL</b></td><td>${env.TRIVY_CRITICAL ?: '0'}</td></tr>
                <tr><td><b>Trivy HIGH</b></td><td>${env.TRIVY_HIGH ?: '0'}</td></tr>
                <tr><td><b>Image Docker</b></td><td>${env.DOCKER_IMAGE_TAG ?: 'N/A'}</td></tr>
              </table>
              <p><a href="${env.BUILD_URL}">Voir le build complet</a></p>
            """,
            to      : 'souhaiel.amri@esprit.tn',
            mimeType: 'text/html'
          )
        } catch (Exception e) {
          echo "⚠️ Email failed: ${e.message}"
        }

        // ── Slack ─────────────────────────────────────────────
        try {
          def icon = buildStatus == 'SUCCESS'  ? '✅'
                   : buildStatus == 'UNSTABLE' ? '⚠️' : '❌'
          slackSend(
            channel : '#souhaiel',
            color   : COLOR_MAP[buildStatus] ?: 'warning',
            // APRÈS — correct
            message : "${icon} *${buildStatus}* — ${env.JOB_NAME} #${env.BUILD_NUMBER}\nBranch: ${branch} | Tests: ${env.TEST_TOTAL ?: '?'} total, ${env.TEST_FAILURES ?: '?'} failed\nQuality Gate: ${env.QUALITY_GATE_STATUS ?: 'N/A'} | Trivy CRITICAL: ${env.TRIVY_CRITICAL ?: '0'}\n${env.BUILD_URL}"
          )
        } catch (Exception e) {
          echo "⚠️ Slack failed: ${e.message}"
        }

        // ── Backend Dashboard ──────────────────────────────────
        try {
          httpRequest(
            url             : "${env.BACKEND_URL}/api/webhooks/jenkins/${env.PROJECT_ID}",
            httpMode        : 'POST',
            contentType     : 'APPLICATION_JSON',
            requestBody     : """{"name":"${env.JOB_NAME}","build":{"phase":"FINALIZED","status":"${buildStatus}","number":"${env.BUILD_NUMBER}","url":"${env.BUILD_URL}","branch":"${branch}"}}""",
            ignoreSslErrors : true,
            validResponseCodes: '100:599'
          )
          echo "✅ Backend notifié"
        } catch (Exception e) {
          echo "⚠️ Backend failed: ${e.message}"
        }
      }
    }

    success {
      echo "🎉 Pipeline terminé avec succès — Build #${env.BUILD_NUMBER}"
    }

    unstable {
      echo "⚠️ Pipeline instable — vérifier les rapports"
    }

    failure {
      echo "❌ Pipeline échoué — analyse IA en cours via n8n"
    }
  }
}
