import util.Common

def j = job('release/tag-git-repos') {

  parameters {
    stringParam('BUILD_ID', null, 'BUILD_ID generated by lsst_build to generate EUPS distrib packages from. Eg. b1935')
    stringParam('GIT_TAG', null, 'git tag string. Eg. w.2016.08')
    booleanParam('DRY_RUN', true, 'Dry run')
  }

  scm {
    git {
      remote {
        github('lsst-sqre/sqre-codekit')
        //refspec('+refs/pull/*:refs/remotes/origin/pr/*')
      }
      branch('*/master')
      extensions {
        cloneOptions {
          shallow(true)
        }
      }
    }
  }

  properties {
    rebuild {
      autoRebuild()
    }
  }

  // python 2.7 is required
  label('centos-7')
  concurrentBuild(false)

  wrappers {
    credentialsBinding {
      string('GITHUB_TOKEN', 'github-api-token-sqreadmin')
    }
  }

  steps {
    shell(
      '''
      ARGS=()
      if [[ $DRY_RUN == "true" ]]; then
        ARGS+=('--dry-run')
      fi

      # do not echo GH token to console log
      set +x
      ARGS+=('--token' "$GITHUB_TOKEN")
      set -x

      ARGS+=('--org' 'lsst')
      ARGS+=('--team' 'Data Management')
      ARGS+=('--email' 'sqre-admin@lists.lsst.org')
      ARGS+=('--tagger' 'sqreadmin')
      ARGS+=('--debug')
      ARGS+=("$GIT_TAG")
      ARGS+=("$BUILD_ID")

      virtualenv venv
      . venv/bin/activate
      pip install -r requirements.txt
      python setup.py install

      # do not echo GH token to console log
      set +x
      github-tag-version "${ARGS[@]}"
      '''.replaceFirst("\n","").stripIndent()

    )
  }
}

Common.addNotification(j)