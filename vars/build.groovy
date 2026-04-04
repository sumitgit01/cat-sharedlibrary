    def call(Map config = [:]) {

        def appType        = config.get('appType', 'node')   // node | maven
        def sonarProjectKey= config.get('sonarProjectKey', 'default-key')
        def sonarSources   = config.get('sonarSources', 'src')
        // Get the port from Jenkinsfile, default to 8082 if not provided
        def nexusPort       = config.get('nexusPort', '8082')
        def repoName  = config.get('repoName', 'default-repo')
        def helmRepoName = config.get('helmRepoName')
        pipeline {
            agent any

            tools {
                nodejs 'node20'
                maven 'maven3'   // configure in Jenkins
                jdk 'jdk21'
            }

            environment {
                SONARQUBE_SERVER = 'SonarQube'
                NEXUS_IP   = "192.168.68.124"
                NEXUS_URL  = "${NEXUS_IP}:${nexusPort}"
                REPO_NAME  = "${repoName}"
                HELM_REPO_NAME = "${helmRepoName}"
                // DEPLOY_TARGET = "192.168.68.126"
            }

            stages {
    /* 
                stage('Initialise') {
                    steps {
                        cleanWs()
                    }
                } */
    
                stage('Build Provisioning') {
                    steps {
                    script {
                            if (appType == 'node') {
                                sh '''
                                npm install
                                npm run build
                                
                                '''
                            }
                            else if (appType == 'maven') {
                                def version = sh(script: "mvn help:evaluate -Dexpression=project.version -q -DforceStdout", returnStdout: true).trim()
                                sh '''
                                    mvn clean install -DskipTests
                                '''
                                env.APP_VERSION = version
                                print("APP VERSION is "+APP_VERSION)
                            }
                            else {
                                error "Unsupported appType: ${appType}"
                            }
                        }
                    }
                }
                stage('Test') {
                    steps {
                        script {
                        if (appType == 'node') {
                            sh '''
                            npm test -- --coverage
                            '''
                        } else if (appType == 'maven') {
                            sh '''
                            mvn test
                            '''
                        }
                    }
                }
            }
                stage('SonarQube Analysis') {
                    steps {
                        withSonarQubeEnv("${SONARQUBE_SERVER}") {
                            withCredentials([string(credentialsId: 'sonar-token', variable: 'SONAR_TOKEN')]) {

                                script {
                                    if (appType == 'node') {
                                    def scannerHome    = tool 'sonar-scanner'
                                        sh """
                                        ${scannerHome}/bin/sonar-scanner \
                                        -Dsonar.projectKey=${sonarProjectKey} \
                                        -Dsonar.sources=${sonarSources} \
                                        -Dsonar.host.url=$SONAR_HOST_URL \
                                        -Dsonar.login=$SONAR_TOKEN
                                        """
                                    }

                                    else if (appType == 'maven') {

                                        sh """
                                        mvn sonar:sonar \
                                        -Dsonar.projectKey=${sonarProjectKey} \
                                        -Dsonar.host.url=$SONAR_HOST_URL \
                                        -Dsonar.login=$SONAR_TOKEN
                                        """
                                    }
                                }
                            }
                        }
                        
                    }
                }
                stage('Quality Gate') {
                    steps {
                        timeout(time: 10, unit: 'MINUTES') {
                            waitForQualityGate abortPipeline: true
                        }
                    }
                }
                stage('Upload images to Nexus Artifactory') {
                    steps {
                        script {
                            withCredentials([usernamePassword(
                                credentialsId: 'nexus_cred',
                                usernameVariable: 'NEXUS_USER',
                                passwordVariable: 'NEXUS_PASS'
                            )]) {
                                sh '''
                                echo "--- DEBUG: Checking Docker Config as Jenkins User ---"
                                docker info | grep -A 1 "Insecure Registries"
                                
                                # If the output above is BLANK, Jenkins is not using the daemon you configured.
                                
                                echo "$NEXUS_PASS" | docker login ${NEXUS_URL} -u "$NEXUS_USER" --password-stdin
                                
                                chmod +x build.sh
                                ./build.sh ${NEXUS_URL} ${REPO_NAME}

                                docker logout ${NEXUS_URL}
                                '''
                            }
                        }
                    }
                }   

                stage('Package & Upload Helm Chart') {
                steps {
                    script {
                        withCredentials([usernamePassword(
                            credentialsId: 'nexus_cred',
                            usernameVariable: 'NEXUS_USER',
                            passwordVariable: 'NEXUS_PASS'
                        )]) {

                            sh """
                            cd manifestbuild
                            helm version
                            CHART_NAME=$(grep '^name:' Chart.yaml | awk '{print \$2}')
                            CHART_VERSION=$(grep '^version:' Chart.yaml | awk '{print \$2}')

                            echo "Chart Name: ${CHART_NAME}" 
                            echo "Chart Version: ${CHART_VERSION}"

                            helm package .

                            curl -u "$\NEXUS_USER:$\NEXUS_PASS" \
                            --upload-file ${CHART_NAME}-${CHART_VERSION}.tgz \
                            http://${NEXUS_URL}/repository/${HELM_REPO_NAME}/
                            """
                        }
                    }
                }
            }
            }
        }
    }

