#!/bin/bash

# parameters
experiment=$1
dataset=$2
dataset_name=$3
ticks_per_ms=$4
start_time=$5
dataset_start_time=$6
kill_probability=$7
kill_interval=$8
paralellism=${9}
shutdown_path=${10}
log_path=${11}
realive_interval=${12}
nodefile=${13}
kill_concurrent_nodes=${14}

# install application and dependencies
. ./install.sh "jre"

cp -r $lambda_home_run/coordinator/* $coordinator_home/


# coordination paths
jar_path=$coordinator_home/lib/coordination-1.0-SNAPSHOT.jar
kafka_properties_path=$coordinator_home/config/kafka.properties
dataset_path=$job_home/dataset/

if [ $experiment = "speed" ]
then
  PATH=$PATH JAVA_HOME=$JAVA_HOME nohup $JAVA_HOME/bin/java -jar -Xmx7000m $jar_path -dataset $dataset_name -producer netty -ticksPerMs $ticks_per_ms -startDataTime $dataset_start_time -path $dataset_path -startSysTime $start_time &
fi

if [ $experiment = "batch-samza" ]
then
  PATH=$PATH JAVA_HOME=$JAVA_HOME nohup $JAVA_HOME/bin/java -jar -Xmx7000m $jar_path -dataset $dataset_name -producer kafka -ticksPerMs $ticks_per_ms -startDataTime $dataset_start_time -kafka-properties $kafka_properties_path -path $dataset_path -topic $dataset_name -startSysTime $start_time &
fi


sleep 10


# start node failure simulation
echo "starting node failure simulation"
$lambda_home_pyenv/bin/python $lambda_home_exec/fail_node.py "$lambda_node_num" "$kill_probability" "$kill_interval" "$paralellism" "$lambda_home_exec" "$shutdown_path" "$log_path" "$realive_interval" "$nodefile" "$kill_concurrent_nodes"
echo "finished node failure simulation"