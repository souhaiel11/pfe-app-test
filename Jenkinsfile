@Library('pfe-devsecops') _

devSecOpsPipeline {
    applicationName = 'pfe-app-test'
    zapTargetUrl    = 'http://app-test:8080'
    skipTests       = false
    // Surface anonyme reellement exposee par cette application : tout le reste
    // repond 403 (Spring Security, JWT). Une API REST ne publie aucun lien, donc
    // sans ces points de depart le spider de ZAP ne voit que la racine -- 3 URL
    // explorees, et un « 0 alerte » qui ne prouve rien. Mesure du build #160 :
    // 3 -> 9 URL explorees avec cet amorcage.
    zapSeedPaths    = ['/api/auth/health', '/api/auth/login', '/api/auth/register', '/api/tasks']
}
