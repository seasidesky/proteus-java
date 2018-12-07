package io.netifi.proteus.frames;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class DestinationSetupFlyweight {
  public static ByteBuf encode(
      ByteBufAllocator allocator, long accessKey, byte[] accessToken, Tags tags) {
    return encode(allocator, accessKey, Unpooled.wrappedBuffer(accessToken), tags);
  }

  public static ByteBuf encode(
      ByteBufAllocator allocator, long accessKey, ByteBuf accessToken, Tags tags) {
    Objects.requireNonNull(tags);

    ByteBuf byteBuf =
        FrameHeaderFlyweight.encodeFrameHeader(allocator, FrameType.DESTINATION_SETUP);

    int accessTokenLength = accessToken.readableBytes();
    byteBuf
        .writeLong(accessKey)
        .writeInt(accessTokenLength)
        .writeBytes(accessToken, accessToken.readerIndex(), accessTokenLength);

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

  public static long accessKey(ByteBuf byteBuf) {
    int offset = FrameHeaderFlyweight.BYTES;

    return byteBuf.getLong(offset);
  }

  public static ByteBuf accessToken(ByteBuf byteBuf) {
    int offset = FrameHeaderFlyweight.BYTES + Long.BYTES;

    int accessTokenLength = byteBuf.getInt(offset);
    offset += Integer.BYTES;

    return byteBuf.slice(offset, accessTokenLength);
  }

  public static Tags tags(ByteBuf byteBuf) {
    int offset = FrameHeaderFlyweight.BYTES + Long.BYTES;

    int accessTokenLength = byteBuf.getInt(offset);
    offset += Integer.BYTES + accessTokenLength;

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
