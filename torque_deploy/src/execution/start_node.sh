#!/bin/bash

cd ~/lambda/execution
. ./global.sh

$lambda_home_pyenv/bin/python starter.py configure_node $lambda_home_exec/resources.json $PBS_NODENUM $PBS_VNODENUM $lambda_home_exec/tmp/hosts $lambda_home_exec



lambda_home_pyenv=$lambda_home_pyenv lambda_home_exec=$lambda_home_exec nohup $lambda_home_pyenv/bin/python starter.py wait $lambda_home_exec/resources.json $PBS_NODENUM $PBS_VNODENUM $lambda_home_exec/tmp/hosts $lambda_home_exec $job_home > starter-$PBS_NODENUM.log &

