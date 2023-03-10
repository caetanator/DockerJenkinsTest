pipeline {
    agent {
        label 'docker-host'
    }
    options {
        disableConcurrentBuilds()
        disableResume()
    }

    parameters {
        string(name: 'ENVIRONMENT_NAME', trim: true)
        password(name: 'MYSQL_PASSWORD', defaultValue: '', description: 'Password to use for MySQL container - root user')
        string(name: 'MYSQL_PORT', trim: true)

        booleanParam(name: 'SKIP_STEP_1', defaultValue: false, description: 'STEP 1 - RE-CREATE DOCKER IMAGE')
    }

    stages {
        stage('Checkout GIT repository') {
            steps {
              /* 'credentialsId' should be obtained by 'credentials()', instead of being on plain text */
              script {
                git branch: 'master',
                credentialsId: '21f01d09-06da9cc35103',
                url: 'git@mysecret-nonexistent-repo/jenkins.git'
              }
            }
        }
        stage('Create latest Docker image') {
            steps {
              script {
                if (!params.SKIP_STEP_1) {
					          def validMSPort = (sh(script: "if $MYSQL_PORT < 1024 || $MYSQL_PORT > 65535; then echo "0"; else echo "1"", returnStdout: true).trim())
                    if (validMSPort == "1") {
                      echo "Creating docker image with name $params.ENVIRONMENT_NAME using port: $params.MYSQL_PORT"
                      sh '''
                      sed 's/<PASSWORD>/${MYSQL_PASSWORD}/g' pipelines/include/create_developer.template > pipelines/include/create_developer.sql
                      '''

                      sh '''
                      docker build pipelines/ -t ${ENVIRONMENT_NAME}:latest
                      '''
                    } else {
                        echo "Invalid MySQL port: $params.MYSQL_PORT"
                    }
                } else {
                    echo "Skipping STEP1"
                }
              }
            }
			post {
				failure {
					echo "The Pipeline failed! Invalid MySQL port: $params.MYSQL_PORT"
				}
			}
        }
        stage('Start new container using latest image and create user') {
            steps {
              script {

                  def dateTime = (sh(script: "date +%Y%m%d%H%M%S", returnStdout: true).trim())
                  def containerName = "${params.ENVIRONMENT_NAME}_${dateTime}"
                  sh '''
                  docker run -itd --name ${containerName} --rm -e MYSQL_ROOT_PASSWORD="${MYSQL_PASSWORD}" -p ${MYSQL_PORT}:3306 ${ENVIRONMENT_NAME}:latest
                  '''

                  sh '''
                  docker exec ${containerName} /bin/bash -c 'mysql --user="root" --password="${MYSQL_PASSWORD}" < /scripts/create_developer.sql'
                  '''

                  echo "Docker container created: $containerName"
                }
            }
        }
    }
}
