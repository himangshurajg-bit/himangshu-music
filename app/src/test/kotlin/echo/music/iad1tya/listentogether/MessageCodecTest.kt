package echo.music.iad1tya.listentogether

import com.google.protobuf.ByteString
import echo.music.iad1tya.listentogether.proto.Listentogether
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageCodecTest {

  @Test
  fun protobufPingUsesTheServerEnvelopeFormat() {
    val codec = MessageCodec(MessageFormat.PROTOBUF, compressionEnabled = false)

    val encoded = codec.encode(MessageTypes.PING, null)
    val envelope = Listentogether.Envelope.parseFrom(encoded)

    assertEquals(MessageTypes.PING, envelope.type)
    assertTrue(envelope.payload.isEmpty)
    assertFalse(envelope.compressed)
    assertArrayEquals(byteArrayOf(0x0a, 0x04, 0x70, 0x69, 0x6e, 0x67), encoded)
  }

  @Test
  fun binaryPongEnvelopeIsDecodedAsServerMessage() {
    val codec = MessageCodec(MessageFormat.PROTOBUF, compressionEnabled = false)
    val payload = byteArrayOf(0x10, 0x01, 0x18, 0x02)
    val envelope =
      Listentogether.Envelope.newBuilder()
        .setType(MessageTypes.PONG)
        .setPayload(ByteString.copyFrom(payload))
        .build()

    val (type, decodedPayload) = codec.decode(envelope.toByteArray())

    assertEquals(MessageTypes.PONG, type)
    assertArrayEquals(payload, decodedPayload)
  }
}
