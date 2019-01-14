#!/bin/bash
set -e

script_dir=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )

${script_dir}/Run.sh examples/scenarios/berlin-v5.1-1pct-1agent/input/berlin-v5.1.config.xml
${script_dir}/Run.sh examples/scenarios/berlin-v5.1-1pct-1pt/input/berlin-v5.1.config.xml
