package io.netifi.proteus.frames;

import io.micrometer.core.instrument.Tags;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import org.junit.Assert;
import org.junit.Test;

public class ShardFlyweightTest {
  @Test
  public void testEncoding() {
    ByteBuf metadata = Unpooled.wrappedBuffer("metadata".getBytes());
    ByteBuf shardKey = Unpooled.wrappedBuffer("shardKey".getBytes());
    Tags tags = Tags.of("group", "toGroup", "destination", "toDestination");
    ByteBuf byteBuf = ShardFlyweight.encode(ByteBufAllocator.DEFAULT, metadata, shardKey, tags);

    System.out.println(ByteBufUtil.prettyHexDump(ShardFlyweight.metadata(byteBuf)));
    Assert.assertTrue(ByteBufUtil.equals(metadata, ShardFlyweight.metadata(byteBuf)));
    Assert.assertTrue(ByteBufUtil.equals(shardKey, ShardFlyweight.shardKey(byteBuf)));
    Assert.assertEquals(tags, ShardFlyweight.tags(byteBuf));
  }
}
