package sanctuary.app.feature.dump.platform

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

internal object WhisperAudioDecoder {
    private const val TARGET_SAMPLE_RATE = 16_000
    private const val DEQUEUE_TIMEOUT_US = 10_000L

    fun decodeToMono16KhzFloatArray(audioFile: File): FloatArray {
        val extractor = MediaExtractor()

        try {
            extractor.setDataSource(audioFile.absolutePath)
            val trackIndex = findAudioTrackIndex(extractor)
            extractor.selectTrack(trackIndex)

            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mimeType = inputFormat.getString(MediaFormat.KEY_MIME)
                ?: throw IllegalStateException("Audio track is missing MIME type")

            val codec = MediaCodec.createDecoderByType(mimeType)
            try {
                codec.configure(inputFormat, null, null, 0)
                codec.start()
                return decodeSamples(extractor, codec, inputFormat)
            } finally {
                runCatching { codec.stop() }
                codec.release()
            }
        } finally {
            extractor.release()
        }
    }

    private fun decodeSamples(
        extractor: MediaExtractor,
        codec: MediaCodec,
        inputFormat: MediaFormat
    ): FloatArray {
        val bufferInfo = MediaCodec.BufferInfo()
        val estimatedSamples = estimateOutputSamples(inputFormat)
        val outputSamples = FloatSampleBuffer(estimatedSamples)

        var outputSampleRate = inputFormat.getIntegerOrNull(MediaFormat.KEY_SAMPLE_RATE) ?: TARGET_SAMPLE_RATE
        var outputChannels = max(inputFormat.getIntegerOrNull(MediaFormat.KEY_CHANNEL_COUNT) ?: 1, 1)
        var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT

        var inputEnded = false
        var outputEnded = false

        while (!outputEnded) {
            if (!inputEnded) {
                val inputBufferIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                        ?: throw IllegalStateException("Decoder input buffer was null")

                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(
                            inputBufferIndex,
                            0,
                            0,
                            0L,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        inputEnded = true
                    } else {
                        val presentationTimeUs = max(extractor.sampleTime, 0L)
                        codec.queueInputBuffer(
                            inputBufferIndex,
                            0,
                            sampleSize,
                            presentationTimeUs,
                            0
                        )
                        extractor.advance()
                    }
                }
            }

            when (val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val outputFormat = codec.outputFormat
                    outputSampleRate = outputFormat.getIntegerOrNull(MediaFormat.KEY_SAMPLE_RATE) ?: outputSampleRate
                    outputChannels = max(outputFormat.getIntegerOrNull(MediaFormat.KEY_CHANNEL_COUNT) ?: outputChannels, 1)
                    pcmEncoding = outputFormat.getIntegerOrNull(MediaFormat.KEY_PCM_ENCODING)
                        ?: AudioFormat.ENCODING_PCM_16BIT
                }
                else -> {
                    if (outputBufferIndex >= 0) {
                        if (bufferInfo.size > 0) {
                            val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                                ?: throw IllegalStateException("Decoder output buffer was null")
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            appendBufferSamples(
                                buffer = outputBuffer.slice().order(ByteOrder.LITTLE_ENDIAN),
                                channelCount = outputChannels,
                                pcmEncoding = pcmEncoding,
                                sink = outputSamples
                            )
                        }

                        outputEnded = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(outputBufferIndex, false)
                    }
                }
            }
        }

        val decoded = outputSamples.toArray()
        if (decoded.isEmpty()) {
            throw IllegalStateException("Audio decode completed but produced no PCM samples")
        }

        return if (outputSampleRate == TARGET_SAMPLE_RATE) {
            decoded
        } else {
            resampleLinear(decoded, outputSampleRate, TARGET_SAMPLE_RATE)
        }
    }

    private fun appendBufferSamples(
        buffer: ByteBuffer,
        channelCount: Int,
        pcmEncoding: Int,
        sink: FloatSampleBuffer
    ) {
        when (pcmEncoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> {
                val frameCount = buffer.remaining() / (4 * channelCount)
                val floatBuffer = buffer.asFloatBuffer()
                repeat(frameCount) {
                    var mixed = 0f
                    repeat(channelCount) {
                        mixed += floatBuffer.get().coerceIn(-1f, 1f)
                    }
                    sink.append((mixed / channelCount).coerceIn(-1f, 1f))
                }
            }

            AudioFormat.ENCODING_PCM_16BIT,
            AudioFormat.ENCODING_DEFAULT -> {
                val frameCount = buffer.remaining() / (2 * channelCount)
                val shortBuffer = buffer.asShortBuffer()
                repeat(frameCount) {
                    var mixed = 0f
                    repeat(channelCount) {
                        mixed += shortBuffer.get() / 32768f
                    }
                    sink.append((mixed / channelCount).coerceIn(-1f, 1f))
                }
            }

            else -> throw IllegalStateException("Unsupported PCM encoding: $pcmEncoding")
        }
    }

    private fun estimateOutputSamples(format: MediaFormat): Int {
        val durationUs = format.getLongOrNull(MediaFormat.KEY_DURATION) ?: 0L
        val sampleRate = format.getIntegerOrNull(MediaFormat.KEY_SAMPLE_RATE) ?: TARGET_SAMPLE_RATE
        if (durationUs <= 0L || sampleRate <= 0) {
            return TARGET_SAMPLE_RATE
        }

        val estimated = (durationUs * sampleRate / 1_000_000L).toInt()
        return max(estimated, TARGET_SAMPLE_RATE)
    }

    private fun findAudioTrackIndex(extractor: MediaExtractor): Int {
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            val mimeType = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mimeType.startsWith("audio/")) {
                return index
            }
        }

        throw IllegalArgumentException("No decodable audio track found in file")
    }

    private fun resampleLinear(input: FloatArray, inputSampleRate: Int, outputSampleRate: Int): FloatArray {
        if (inputSampleRate == outputSampleRate || input.isEmpty()) {
            return input
        }

        val outputSize = max((input.size.toLong() * outputSampleRate / inputSampleRate).toInt(), 1)
        val output = FloatArray(outputSize)
        val scale = inputSampleRate.toDouble() / outputSampleRate.toDouble()

        for (index in output.indices) {
            val sourcePosition = index * scale
            val baseIndex = floor(sourcePosition).toInt().coerceIn(0, input.lastIndex)
            val nextIndex = min(baseIndex + 1, input.lastIndex)
            val fraction = (sourcePosition - baseIndex).toFloat()
            output[index] = input[baseIndex] + ((input[nextIndex] - input[baseIndex]) * fraction)
        }

        return output
    }
}

private class FloatSampleBuffer(initialCapacity: Int) {
    private var values = FloatArray(max(initialCapacity, 16))
    private var size = 0

    fun append(value: Float) {
        ensureCapacity(size + 1)
        values[size] = value
        size += 1
    }

    fun toArray(): FloatArray = values.copyOf(size)

    private fun ensureCapacity(requiredSize: Int) {
        if (requiredSize <= values.size) {
            return
        }

        var newCapacity = values.size
        while (newCapacity < requiredSize) {
            newCapacity *= 2
        }
        values = values.copyOf(newCapacity)
    }
}

private fun MediaFormat.getIntegerOrNull(key: String): Int? =
    if (containsKey(key)) getInteger(key) else null

private fun MediaFormat.getLongOrNull(key: String): Long? =
    if (containsKey(key)) getLong(key) else null
