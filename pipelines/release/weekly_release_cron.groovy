def notify = null
node {
  dir('jenkins-dm-jobs') {
    // XXX the git step seemed to blowup on a branch of '*/<foo>'
    checkout([
      $class: 'GitSCM',
      branches: scm.getBranches(),
      userRemoteConfigs: scm.getUserRemoteConfigs()
    ])
    notify = load 'pipelines/lib/notify.groovy'
  }
}

try {
  notify.started()

  def weeklyJob = 'release/weekly-release'
  def year = null
  def week = null

  stage('generate temporal coordinates') {
    def tz = TimeZone.getTimeZone('America/Los_Angeles')
    def date = new java.util.Date()

    def yearFormat = new java.text.SimpleDateFormat('Y')
    yearFormat.setTimeZone(tz)
    def weekFormat = new java.text.SimpleDateFormat('w')
    weekFormat.setTimeZone(tz)

    year = yearFormat.format(date)
    week = weekFormat.format(date)

    echo "generated year: ${year}"
    echo "generated week: ${week}"
  }

  stage('run weekly-release') {
    build job: weeklyJob,
      parameters: [
        stringParam(name: 'YEAR', value: year),
        stringParam(name: 'WEEK', value: week)
      ]
  }
} catch (e) {
  // If there was an exception thrown, the build failed
  currentBuild.result = "FAILED"
  throw e
} finally {
  echo "result: ${currentBuild.result}"
  switch(currentBuild.result) {
    case null:
    case 'SUCCESS':
      notify.success()
      break
    case 'ABORTED':
      notify.aborted()
      break
    case 'FAILURE':
      notify.failure()
      break
    default:
      notify.failure()
  }
}