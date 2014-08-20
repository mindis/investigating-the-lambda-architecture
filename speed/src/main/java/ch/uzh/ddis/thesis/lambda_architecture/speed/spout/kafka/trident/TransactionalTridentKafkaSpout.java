/**
 * Please not that the Kafka package from Storm was copied here in order to modify it for the use case. It
 * was not possible to deeply modify its behavior regarding reliable offset commits with its restrictive access
 * policy. The code was copied from: https://github.com/apache/incubator-storm/tree/master/external/storm-kafka
 *
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ch.uzh.ddis.thesis.lambda_architecture.speed.spout.kafka.trident;

import backtype.storm.task.TopologyContext;
import backtype.storm.tuple.Fields;
import ch.uzh.ddis.thesis.lambda_architecture.speed.spout.kafka.Partition;
import storm.trident.spout.IPartitionedTridentSpout;

import java.util.Map;
import java.util.UUID;


public class TransactionalTridentKafkaSpout implements IPartitionedTridentSpout<GlobalPartitionInformation, Partition, Map> {

    TridentKafkaConfig _config;
    String _topologyInstanceId = UUID.randomUUID().toString();

    public TransactionalTridentKafkaSpout(TridentKafkaConfig config) {
        _config = config;
    }


    @Override
    public IPartitionedTridentSpout.Coordinator getCoordinator(Map conf, TopologyContext context) {
        return new ch.uzh.ddis.thesis.lambda_architecture.speed.spout.kafka.trident.Coordinator(conf, _config);
    }

    @Override
    public IPartitionedTridentSpout.Emitter getEmitter(Map conf, TopologyContext context) {
        return new TridentKafkaEmitter(conf, context, _config, _topologyInstanceId).asTransactionalEmitter();
    }

    @Override
    public Fields getOutputFields() {
        return _config.scheme.getOutputFields();
    }

    @Override
    public Map<String, Object> getComponentConfiguration() {
        return null;
    }
}
