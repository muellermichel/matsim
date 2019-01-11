#!/bin/bash

script_dir=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )

# To get the classpath by maven:
mvn -pl matsim dependency:build-classpath -Dmdep.outputFile=matsim.cp
classpath="\
$script_dir/matsim/target/matsim-0.11.0-SNAPSHOT-tests.jar:\
$script_dir/matsim/target/matsim-0.11.0-SNAPSHOT.jar:\
$(cat $script_dir/matsim/matsim.cp)"

echo "============CLASSPATH============"
echo $classpath
echo "======RUN ALL THE THINGS========="

#config=$script_dir/scenarios/berlin-v5.1-1pct/input/berlin-v5.1-1pct-1it.config.xml

config=$script_dir/examples/scenarios/berlin-v5.1-1pct-1agent/input/berlin-v5.1.config.xml

#config=$script_dir/examples/scenarios/berlin-v5.1-1pct-1pt/input/berlin-v5.1.config.xml
#config=$script_dir/examples/scenarios/berlin-v5.1-1pct-1pt-1agent/input/berlin-v5.1.config.xml
#config=$script_dir/examples/scenarios/berlin-v5.1-1pct-fullpt/input/berlin-v5.1.config.xml
#config=$script_dir/examples/scenarios/berlin-v5.1-1pct/input/berlin-v5.1.config.xml

timestamp=$(date +"%Y-%m-%d-%H_%M")
config_dir="$(dirname $config)/.."
config_basename="$(basename $(dirname $(dirname $config)))"
validation_path="${config_dir}/output/ITERS/it.0/${config_basename}.0.events.xml.gz"

echo "Running matsim with config ${config} in ${config_dir}, base ${config_basename}, validating against ${validation_path}"

java \
	-Xmx15G \
	-Dfile.encoding=UTF-8 \
	-classpath "${classpath}" \
	org.matsim.berlin.RunBerlinScenario "${config}" "" "${validation_path}"  | tee output_${timestamp}.txt | tee run.log

echo "Running matsim...Done!"
