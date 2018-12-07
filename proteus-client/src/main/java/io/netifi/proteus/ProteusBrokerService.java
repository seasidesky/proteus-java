package io.netifi.proteus;

import io.micrometer.core.instrument.Tags;
import io.netifi.proteus.rsocket.ProteusSocket;
import io.netty.buffer.ByteBuf;

interface ProteusBrokerService {
  ProteusSocket unicast(Tags tags);

  ProteusSocket broadcast(Tags tags);

  ProteusSocket shard(ByteBuf shardKey, Tags tags);
}
