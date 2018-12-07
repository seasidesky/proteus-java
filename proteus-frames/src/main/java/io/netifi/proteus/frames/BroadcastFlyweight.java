package io.netifi.proteus.frames;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class BroadcastFlyweight {
  public static ByteBuf encode(ByteBufAllocator allocator, ByteBuf metadata, Tags tags) {

    ByteBuf byteBuf = FrameHeaderFlyweight.encodeFrameHeader(allocator, FrameType.BROADCAST);

    int metadataSize = metadata.readableBytes();
    byteBuf.writeInt(metadataSize).writeBytes(metadata, metadata.readerIndex(), metadataSize);

    for (Tag tag : tags) {
      String key = tag.getKey();
      String value = tag.getValue();

      int keyLength = ByteBufUtil.utf8Bytes(key);
      byteBuf.writeInt(keyLength);
      ByteBufUtil.reserveAndWriteUtf8(byteBuf, key, keyLength);

      int valueLength = ByteBufUtil.utf8Bytes(value);
      byteBuf.writeInt(valueLength);
      ByteBufUtil.reserveAndWriteUtf8(byteBuf, value, valueLength);
    }

    return byteBuf;
  }

  public static ByteBuf metadata(ByteBuf byteBuf) {
    int offset = FrameHeaderFlyweight.BYTES;

    int metadataLength = byteBuf.getInt(offset);
    offset += Integer.BYTES;

    return byteBuf.slice(offset, metadataLength);
  }

  public static Tags tags(ByteBuf byteBuf) {
    int offset = FrameHeaderFlyweight.BYTES;

    int metadataLength = byteBuf.getInt(offset);
    offset += Integer.BYTES + metadataLength;

    List<Tag> tags = new ArrayList<>();
    while (offset < byteBuf.readableBytes()) {
      int keyLength = byteBuf.getInt(offset);
      offset += Integer.BYTES;

      String key = byteBuf.toString(offset, keyLength, StandardCharsets.UTF_8);
      offset += keyLength;

      int valueLength = byteBuf.getInt(offset);
      offset += Integer.BYTES;

      String value = byteBuf.toString(offset, valueLength, StandardCharsets.UTF_8);
      offset += valueLength;

      tags.add(Tag.of(key, value));
    }

    return Tags.of(tags);
  }
}
