import util.Common
Common.makeFolders(this)

// note that this job *will not work* unless run-rebuild has been executed at
// least once in order to initialize the env.
pipelineJob('release/run-publish') {
  description('Create and publish EUPS distrib packages.')

  parameters {
    choiceParam('EUPSPKG_SOURCE', ['git', 'package'])
    stringParam('BUILD_ID', null, 'BUILD_ID generated by lsst_build to generate EUPS distrib packages from. Eg. b1935')
    stringParam('TAG', null, 'EUPS distrib tag name to publish. Eg. w_2016_08')
    stringParam('PRODUCT', null, 'Whitespace delimited list of EUPS products to tag.')
    // enable for debugging only
    // booleanParam('NO_PUSH', true, 'Skip s3 push.')
  }

  properties {
    rebuild {
      autoRebuild()
    }
  }

  // don't tie up a beefy build slave
  label('jenkins-master')
  concurrentBuild(false)
  keepDependencies(true)

  def repo = SEED_JOB.scm.userRemoteConfigs.get(0).getUrl()
  def ref  = SEED_JOB.scm.getBranches().get(0).getName()

  definition {
    cpsScm {
      scm {
        git {
          remote {
            url(repo)
          }
          branch(ref)
        }
      }
      scriptPath('pipelines/release/run_publish.groovy')
    }
  }
}
