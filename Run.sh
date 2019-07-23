#!/bin/bash

script_dir=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )

export JAVA_HOME=/usr/lib/jvm/jdk-11.0.1
#export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
java=$JAVA_HOME/bin/java

#use_jfr="-XX:+UnlockCommercialFeatures -XX:+FlightRecorder -XX:StartFlightRecording=filename=run.jfr"
#c1visualizer="-Dgraal.PrintCFG=true"
#igv="-Dgraal.Dump -Dgraal.PrintGraph=true -Dgraal.MethodFilter=org.matsim.core.mobsim.hermes.Realm.*"
use_graal="-XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI -XX:+UseJVMCICompiler $igv $c1visualizer"
#debug="-agentlib:jdwp=transport=dt_socket,address=8000,server=y,suspend=y"

gc="-XX:+UseParallelGC -Xmx16G"

# To get the classpath by maven:
mvn -pl matsim dependency:build-classpath -Dmdep.outputFile=matsim.cp
classpath="\
$script_dir/matsim/target/matsim-0.11.0-SNAPSHOT-tests.jar:\
$script_dir/matsim/target/matsim-0.11.0-SNAPSHOT.jar:\
$(cat $script_dir/matsim/matsim.cp)"

#config=$script_dir/scenarios/berlin-v5.1-1pct/input/berlin-v5.1-1pct-1it.config.xml

#config=$script_dir/examples/scenarios/berlin-v5.1-1pct-1agent/input/berlin-v5.1.config.xml
#config=$script_dir/examples/scenarios/berlin-v5.1-1pct-1pt/input/berlin-v5.1.config.xml
#config=$script_dir/examples/scenarios/berlin-v5.1-1pct-1pt-1agent/input/berlin-v5.1.config.xml
#config=$script_dir/examples/scenarios/berlin-v5.1-1pct-fullpt/input/berlin-v5.1.config.xml
config=$script_dir/examples/scenarios/berlin-v5.1-1pct/input/berlin-v5.1.config.xml

# Overriding configs. Used to specify the number of sim threads
over_prefix=$script_dir/examples/scenarios/berlin-v5.1-1pct/input

function run {
    echo "Running matsim..."
    $java \
        $use_jfr \
        $use_graal \
        $debug \
        $gc \
        -Xlog:gc*:run.jvm:time \
        -Dfile.encoding=UTF-8 \
        -Dscenario=matsim \
        -classpath "${classpath}" \
        org.matsim.berlin.RunBerlinScenario $config $over &> run.log
    echo "Running matsim...Done!"
    grep "ETHZ" run.log
}

echo "Config = $config"
#for over_suffix in 1-sim-threads 2-sim-threads 4-sim-threads 8-sim-threads
#for over_suffix in 1-sim-threads
#do
#    over=$over_prefix/$over_suffix.xml
#    echo "Over = $over"
#    run
#done
run
paplay /usr/share/sounds/freedesktop/stereo/complete.oga
#beep
