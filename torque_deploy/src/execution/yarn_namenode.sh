#!/bin/bash

. ./install.sh "yarn"


yarn_config_hdfs=$LAMBDA_APP_HOME/etc/hadoop/hdfs-site.xml
yarn_config_core=$LAMBDA_APP_HOME/etc/hadoop/core-site.xml
yarn_config_mapred=$LAMBDA_APP_HOME/etc/hadoop/mapred-site.xml

$yarn_host=$(host $(cat $PBS_NODEFILE) | grep -Eo '[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}')


cp $lambda_home_conf/hdfs-site.namenode.xml $yarn_config_hdfs
sed -ie "s,\$data_dir,$LAMBDA_APP_DATA," $zookeeper_config



LYARN_DATA_NM=$LYARN_DATA/nm
mkdir -p $LYARN_DATA_NM

LYARN_NAMENODE_HOST=$(host $(cat $PBS_NODEFILE) | grep -Eo '[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}')

./replace_var_xml.sh $LAMBDA_CONF/hdfs-site.namenode.xml $LYARN_HOME/etc/hadoop/hdfs-site.xml LYARN_DATA_NM $LYARN_DATA_NM
./replace_var_xml.sh $LAMBDA_CONF/core-site.xml $LYARN_HOME/etc/hadoop/core-site.xml LYARN_NAMENODE_HOST $LYARN_NAMENODE_HOST
./replace_var_xml_same.sh $LYARN_HOME/etc/hadoop/core-site.xml LYARN_TMP $LYARN_TMP
cp $LAMBDA_CONF/mapred-site.xml $LYARN_HOME/etc/hadoop/mapred-site.xml


cd $LYARN_HOME

echo "formatting hdfs"
JAVA_HOME=$JAVA_HOME ./bin/hdfs namenode -format lambda_cluster > $LYARN_LOGS/namenode_format.log

echo "starting namenode"
JAVA_HOME=$JAVA_HOME nohup sbin/hadoop-daemon.sh --config $LYARN_HOME/etc/hadoop --script hdfs start namenode > $LYARN_LOGS/namenode.log 2>&1 &
NN_PID=$!
echo $NN_PID > $LYARN_PID/namenode.pid
