// Always omit teamDomain & token args from slackSend: these come from Jenkins
// Can also omit channel to use the default (#commits)
final def consoleURL = "<${env.BUILD_URL}console|${env.BUILD_TAG}>"
final def testResultURL = "<${env.BUILD_URL}testReport|${env.BUILD_TAG}>"

// JUnitResultArchiver sets result="UNSTABLE" if tests fail, otherwise does
// nothing (thus result == null).
boolean buildIsGood() {
  currentBuild.result == null || currentBuild.result == 'SUCCESS'
}

node {
  checkout scm

  stage "Running tests"
  sh 'mkdir -p target/reports'
  sh "${env.LEIN} with-profile +jenkins test-out junit target/reports/TEST-gybe.xml"

  stage "Gathering test results"
  step([$class: 'JUnitResultArchiver', testResults: 'target/reports/TEST-gybe.xml'])

  if (buildIsGood()) {
    slackSend color: 'good', message: "Gybe tests succeeded: ${testResultURL}"
  } else {
    slackSend color: 'danger', message: "Gybe tests failed (${currentBuild.result}): ${testResultURL}"
  }
}

