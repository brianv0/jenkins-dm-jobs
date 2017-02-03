import util.Common

def j = matrixJob('validate_drp') {
  description('Execute validate_drp and ship the results to the squash qa-dashboard.')

  properties {
    rebuild {
      autoRebuild()
    }
  }

  label('master')
  concurrentBuild()

  multiscm {
    git {
      remote {
        github('lsst/lsstsw')
      }
      branch('*/master')
      extensions {
        relativeTargetDirectory('lsstsw')
        cloneOptions { shallow() }
      }
    }
    git {
      remote {
        github('lsst-sqre/buildbot-scripts')
      }
      branch('*/master')
      extensions {
        relativeTargetDirectory('buildbot-scripts')
        cloneOptions { shallow() }
      }
    }
    // jenkins can't properly clone a git-lfs repo (yet) due to the way it
    // invokes git.  Jenkins is managing the basic checkout but we need to do a
    // manual `git lfs pull`.  see:
    // https://issues.jenkins-ci.org/browse/JENKINS-30318
    git {
      remote {
        github('lsst/validation_data_hsc')
      }
      branch('*/master')
      extensions {
        relativeTargetDirectory('validation_data_hsc')
        cloneOptions { shallow() }
      }
    }
  }

  triggers {
    cron('H H/8 * * *')
  }

  axes {
    label('label', 'centos-7')
    text('dataset', 'cfht', 'hsc')
    text('python', 'py2')
  }

  combinationFilter('!(label=="centos-6" && python=="py3")')

  wrappers {
    colorizeOutput('gnome-terminal')
    credentialsBinding {
      usernamePassword(
        'SQUASH_USER',
        'SQUASH_PASS',
        'squash-api-user'
      )
      string('SQUASH_URL', 'squash-api-url')
    }
  }

  environmentVariables(
    BRANCH:    'master',
    PRODUCT:   'validate_drp',
    SKIP_DEMO: true,
    SKIP_DOCS: true,
    NO_FETCH:  false,
    // anything in thid dir will be saved as a build artifact
    ARCHIVE:   '$WORKSPACE/archive',
    // cwd for running the drp script
    DRP:       '$WORKSPACE/validate_drp',
    LSSTSW:    '$WORKSPACE/lsstsw',
    POSTQA:    '$WORKSPACE/post-qa',
    POSTQA_VERSION: '1.2.2',
    // validation data sets -- avoid variable name collision with EUPS
    HSC_DATA:  '$WORKSPACE/validation_data_hsc',
  )

  steps {
    // cleanup
    shell(
      '''
      #!/bin/bash -e

      # leave validate_drp results in workspace for debugging purproses but
      # always start with clean dirs

      rm -rf "$ARCHIVE" "$DRP"
      mkdir -p "$ARCHIVE" "$DRP"
      '''.replaceFirst("\n","").stripIndent()
    )

    // build/install validate_drp
    shell('./buildbot-scripts/jenkins_wrapper.sh')

    // run drp driver script
    shell(
      '''
      #!/bin/bash -e

      lfsconfig() {
        git config --local lfs.batch false
        # lfs.required must be false in order for jenkins to manage the clone
        git config --local filter.lfs.required false
        git config --local filter.lfs.smudge 'git-lfs smudge %f'
        git config --local filter.lfs.clean 'git-lfs clean %f'
        git config --local credential.helper '!f() { cat > /dev/null; echo username=; echo password=; }; f'
      }

      cd "$DRP"

      . "${LSSTSW}/bin/setup.sh"

      eval "$(grep -E '^BUILD=' "${LSSTSW}/build/manifest.txt")"

      DEPS=(validate_drp)

      for p in "${DEPS[@]}"; do
          setup "$p" -t "$BUILD"
      done

      #mkdir -p ~/.config/matplotlib
      #echo "backend: agg" > ~/.config/matplotlib/matplotlibrc

      case "$dataset" in
        cfht)
          RUN="$VALIDATE_DRP_DIR/examples/runCfhtTest.sh"
          OUTPUT="${DRP}/Cfht_output_r.json"
          ;;
        decam)
          RUN="$VALIDATE_DRP_DIR/examples/runDecamTest.sh"
          OUTPUT="${DRP}/Decam_output_z.json"
          ;;
        hsc)
          RUN="$VALIDATE_DRP_DIR/examples/runHscTest.sh"
          OUTPUT="${DRP}/data_hsc_rerun_20170105_HSC-R.json"

          ( set -e
            cd $HSC_DATA
            lfsconfig
            git lfs pull
          )
          setup -k -r $HSC_DATA
          ;;
        *)
          >&2 echo "Unknown DATASET: $dataset"
          exit 1
          ;;
      esac

      #rm -f ~/.config/matplotlib/matplotlibrc

      "$RUN" --noplot
      ln -sf "$OUTPUT" "${DRP}/output.json"
      cp "$OUTPUT" "$ARCHIVE"
      '''.replaceFirst("\n","").stripIndent()
    )

    // push results to squash
    shell(
      '''
      #!/bin/bash -e

      mkdir -p "$POSTQA"
      cd "$POSTQA"

      virtualenv venv
      . venv/bin/activate
      pip install functools32
      pip install post-qa==$POSTQA_VERSION

      post-qa --lsstsw "$LSSTSW" --qa-json "${DRP}/output.json" --api-url "$SQUASH_URL/jobs/"  --api-user "$SQUASH_USER" --api-password "$SQUASH_PASS"
      '''.replaceFirst("\n","").stripIndent()
    )
  }

  publishers {
    // we have to use postBuildScript here instead of the friendlier
    // postBuildScrips (plural) in order to use executeOn(), otherwise the
    // cleanup script is also run on the jenkins master
    postBuildScript {
      scriptOnlyIfSuccess(false)
      scriptOnlyIfFailure(true)
      markBuildUnstable(false)
      executeOn('AXES')
      buildStep {
        shell {
          command(
            '''
            Z=$(lsof -d 200 -t)
            if [[ ! -z $Z ]]; then
              kill -9 $Z
            fi

            rm -rf "${WORKSPACE}/lsstsw/stack/.lockDir"
            '''.replaceFirst("\n","").stripIndent()
          )
        }
      }
    }
    archiveArtifacts {
      fingerprint()
      pattern('archive/**/*')
    }
  }
}

Common.addNotification(j)
