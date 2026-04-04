package sanctuary.app.feature.dump.platform

import android.content.Context
import android.os.Build
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import sanctuary.app.feature.dump.domain.transcription.OnDeviceTranscriber
import java.io.File
import java.util.Properties

/**
 * whisper.cpp-backed on-device STT implementation for Android.
 *
 * This path is fully local and free at runtime. It expects a ggml Whisper model
 * to be available either via an explicit file path or inside app assets.
 */
class WhisperCppOnDeviceTranscriber : OnDeviceTranscriber, KoinComponent {
    private val context: Context by inject()
    private val contextMutex = Mutex()

    private var whisperContext: WhisperCppContext? = null
    private var loadedModelPath: String? = null

    companion object {
        private const val TRANSCRIPTION_TIMEOUT_MS = 180_000L
        private const val MAX_FILE_SIZE_BYTES = 100 * 1024 * 1024L
        private const val TARGET_SAMPLE_RATE = 16_000
        private const val FLOAT_SAMPLE_BYTES = 4L
        private const val MAX_ESTIMATED_PCM_FOOTPRINT_BYTES = 96 * 1024 * 1024L
        private val SUPPORTED_FORMATS = setOf(".m4a", ".mp3", ".wav", ".flac", ".ogg", ".aac")
        private val LANGUAGE_CODES = mapOf(
            "en" to "en",
            "hi" to "hi"
        )

        fun hasConfiguredModel(context: Context): Boolean =
            WhisperModelLocator.hasConfiguredModel(context)
    }

    override suspend fun transcribe(filePath: String, locale: String): String = withContext(Dispatchers.IO) {
        require(filePath.isNotBlank()) { "filePath must not be empty" }
        require(locale.isNotBlank()) { "locale must not be empty" }

        val languageCode = LANGUAGE_CODES[locale] ?: throw IllegalArgumentException(
            "Unsupported locale: '$locale'. Supported: ${LANGUAGE_CODES.keys.joinToString(", ")}"
        )

        val audioFile = validateAudioFile(filePath)
        validateDecodedAudioFootprint(audioFile)
        val modelPath = WhisperModelLocator.resolveModelPath(context, locale)
        val audioSamples = WhisperAudioDecoder.decodeToMono16KhzFloatArray(audioFile)

        val transcript = withTimeoutOrNull(TRANSCRIPTION_TIMEOUT_MS) {
            contextMutex.withLock {
                val whisper = getOrCreateContextLocked(modelPath)
                whisper.transcribe(
                    audioSamples = audioSamples,
                    language = languageCode,
                    threadCount = WhisperCpuConfig.preferredThreadCount
                )
            }
        } ?: throw IllegalStateException(
            "whisper.cpp transcription timed out after ${TRANSCRIPTION_TIMEOUT_MS}ms"
        )

        if (transcript.isBlank()) {
            throw IllegalStateException("No speech detected in audio file")
        }

        transcript
    }

    private suspend fun getOrCreateContextLocked(modelPath: String): WhisperCppContext {
        val activeContext = whisperContext
        if (activeContext != null && loadedModelPath == modelPath) {
            return activeContext
        }

        activeContext?.release()
        return WhisperCppContext.fromModelPath(modelPath).also {
            whisperContext = it
            loadedModelPath = modelPath
        }
    }

    private fun validateAudioFile(filePath: String): File {
        val audioFile = File(filePath)

        if (!audioFile.exists()) {
            throw IllegalArgumentException("Audio file not found: $filePath")
        }

        if (!audioFile.canRead()) {
            throw IllegalArgumentException("Audio file not readable: $filePath")
        }

        if (audioFile.length() > MAX_FILE_SIZE_BYTES) {
            throw IllegalArgumentException(
                "Audio file too large: ${audioFile.length()} bytes (max: $MAX_FILE_SIZE_BYTES)"
            )
        }

        val extension = ".${audioFile.extension.lowercase()}"
        if (extension !in SUPPORTED_FORMATS) {
            throw IllegalArgumentException(
                "Unsupported audio format: $extension. Supported: ${SUPPORTED_FORMATS.joinToString(", ")}"
            )
        }

        return audioFile
    }

    private fun validateDecodedAudioFootprint(audioFile: File) {
        val estimate = estimateDecodedAudioFootprint(audioFile) ?: return
        if (estimate.estimatedPeakBytes <= MAX_ESTIMATED_PCM_FOOTPRINT_BYTES) {
            return
        }

        val estimatedMegabytes = estimate.estimatedPeakBytes / (1024 * 1024)
        throw IllegalArgumentException(
            "Audio file is too large for on-device transcription. " +
                "Estimated decoded PCM footprint: ${estimatedMegabytes}MB " +
                "(max: ${MAX_ESTIMATED_PCM_FOOTPRINT_BYTES / (1024 * 1024)}MB)."
        )
    }

    private fun estimateDecodedAudioFootprint(audioFile: File): AudioDecodeEstimate? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(audioFile.absolutePath)
            val trackIndex = extractor.findAudioTrackIndex() ?: return null
            val format = extractor.getTrackFormat(trackIndex)
            val durationUs = format.getLongOrNull(MediaFormat.KEY_DURATION) ?: return null
            val sampleRate = (format.getIntegerOrNull(MediaFormat.KEY_SAMPLE_RATE) ?: return null).coerceAtLeast(1)
            if (durationUs <= 0L) {
                return null
            }

            val decodedSourceBytes = durationUs * sampleRate * FLOAT_SAMPLE_BYTES / 1_000_000L
            val resampledBytes = durationUs * TARGET_SAMPLE_RATE * FLOAT_SAMPLE_BYTES / 1_000_000L
            return AudioDecodeEstimate(
                estimatedPeakBytes = decodedSourceBytes + resampledBytes
            )
        } finally {
            extractor.release()
        }
    }
}

private object WhisperModelLocator {
    private const val LOCAL_MODEL_DIRECTORY = "whisper-models"
    private const val CACHE_METADATA_SUFFIX = ".metadata"
    private const val CACHE_COPY_SUFFIX = ".tmp"
    private val explicitPropertyKeys = listOf(
        "sanctuary.whisper.model.path",
        "whisper.model.path"
    )
    private val explicitEnvironmentKeys = listOf(
        "WHISPER_MODEL_PATH",
        "WHISPER_CPP_MODEL_PATH"
    )
    private val assetDirectories = listOf("whisper", "models", "")
    private val knownModelNames = listOf(
        "ggml-base.bin",
        "ggml-base-q5_1.bin",
        "ggml-base.en.bin",
        "ggml-base.en-q5_1.bin",
        "ggml-small.bin",
        "ggml-small.en.bin",
        "ggml-tiny.bin",
        "ggml-tiny.en.bin"
    )

    fun hasConfiguredModel(context: Context): Boolean {
        if (findExplicitModelFile("en") != null) {
            return true
        }

        if (listLocalModelFiles(context).isNotEmpty()) {
            return true
        }

        return listBundledAssetModels(context).isNotEmpty()
    }

    fun resolveModelPath(context: Context, locale: String): String {
        val explicitModel = findExplicitModelFile(locale)
        if (explicitModel != null) {
            ensureLocaleCompatible(explicitModel, locale)
            return explicitModel.absolutePath
        }

        val localModel = selectBestModel(listLocalModelFiles(context), locale)
        if (localModel != null) {
            return localModel.absolutePath
        }

        val bundledAsset = selectBestAsset(listBundledAssetModels(context), locale)
            ?: throw IllegalStateException(
                "No whisper.cpp model found. Set sanctuary.whisper.model.path, " +
                    "set WHISPER_MODEL_PATH, or bundle a ggml model in feature_dump/src/androidMain/assets/whisper/."
            )

        ensureLocaleCompatible(bundledAsset.fileName, locale)
        return copyAssetToInternalStorage(
            context = context,
            assetPath = bundledAsset.assetPath,
            fileName = bundledAsset.fileName
        ).absolutePath
    }

    private fun findExplicitModelFile(locale: String): File? {
        val rawPath = explicitPropertyKeys
            .asSequence()
            .mapNotNull { System.getProperty(it) }
            .firstOrNull { it.isNotBlank() }
            ?: explicitEnvironmentKeys
                .asSequence()
                .mapNotNull { System.getenv(it) }
                .firstOrNull { it.isNotBlank() }

        if (rawPath.isNullOrBlank()) {
            return null
        }

        val candidate = File(rawPath)
        return when {
            candidate.isFile && candidate.canRead() -> candidate
            candidate.isDirectory -> selectBestModel(
                candidate.listFiles().orEmpty().filter { it.isFile && it.canRead() },
                locale
            )
            else -> throw IllegalStateException("Configured whisper.cpp model path is unreadable: $rawPath")
        }
    }

    private fun listLocalModelFiles(context: Context): List<File> {
        val directory = File(context.filesDir, LOCAL_MODEL_DIRECTORY)
        return directory.listFiles().orEmpty()
            .filter { it.isFile && it.canRead() && it.name.endsWith(".bin", ignoreCase = true) }
    }

    private fun listBundledAssetModels(context: Context): List<AssetModel> {
        val models = mutableListOf<AssetModel>()
        for (directory in assetDirectories) {
            val assetEntries = runCatching { context.assets.list(directory) ?: emptyArray() }.getOrDefault(emptyArray())
            val normalizedDirectory = directory.trim('/')

            for (entry in assetEntries) {
                if (!entry.endsWith(".bin", ignoreCase = true)) {
                    continue
                }

                val assetPath = if (normalizedDirectory.isEmpty()) entry else "$normalizedDirectory/$entry"
                models += AssetModel(assetPath = assetPath, fileName = entry)
            }

            if (normalizedDirectory.isNotEmpty()) {
                for (name in knownModelNames) {
                    val assetPath = "$normalizedDirectory/$name"
                    if (assetExists(context, assetPath) && models.none { it.assetPath == assetPath }) {
                        models += AssetModel(assetPath = assetPath, fileName = name)
                    }
                }
            }
        }

        return models.distinctBy { it.assetPath }
    }

    private fun selectBestModel(models: List<File>, locale: String): File? =
        models
            .filter { it.name.endsWith(".bin", ignoreCase = true) }
            .sortedBy { scoreModelName(it.name, locale) }
            .firstOrNull()

    private fun selectBestAsset(models: List<AssetModel>, locale: String): AssetModel? =
        models.sortedBy { scoreModelName(it.fileName, locale) }.firstOrNull()

    private fun scoreModelName(modelName: String, locale: String): Int {
        val lowerName = modelName.lowercase()
        val isEnglishOnly = englishOnlyRegex.containsMatchIn(lowerName)
        val isQuantized = "-q" in lowerName

        return when {
            locale == "hi" && !isEnglishOnly && !isQuantized -> 0
            locale == "hi" && !isEnglishOnly -> 1
            locale == "en" && isEnglishOnly && !isQuantized -> 0
            locale == "en" && isEnglishOnly -> 1
            locale == "en" && !isEnglishOnly && !isQuantized -> 2
            else -> 3
        }
    }

    private fun ensureLocaleCompatible(modelFile: File, locale: String) {
        ensureLocaleCompatible(modelFile.name, locale)
    }

    private fun ensureLocaleCompatible(modelName: String, locale: String) {
        if (locale == "hi" && englishOnlyRegex.containsMatchIn(modelName.lowercase())) {
            throw IllegalStateException(
                "Hindi transcription requires a multilingual Whisper model. " +
                    "The resolved model '$modelName' is English-only."
            )
        }
    }

    private fun copyAssetToInternalStorage(context: Context, assetPath: String, fileName: String): File {
        val targetDirectory = File(context.filesDir, LOCAL_MODEL_DIRECTORY).apply { mkdirs() }
        val targetFile = File(targetDirectory, fileName)
        val metadataFile = File(targetDirectory, "$fileName$CACHE_METADATA_SUFFIX")
        val cacheMetadata = BundledModelCacheMetadata(
            assetPath = assetPath,
            appVersionCode = context.appVersionCode()
        )

        if (isCachedAssetCurrent(targetFile, metadataFile, cacheMetadata)) {
            return targetFile
        }

        val tempFile = File(targetDirectory, "$fileName$CACHE_COPY_SUFFIX")
        runCatching { tempFile.delete() }

        try {
            val copiedBytes = context.assets.open(assetPath).use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            if (copiedBytes <= 0L) {
                throw IllegalStateException("Bundled Whisper model asset '$assetPath' was empty")
            }

            replaceFile(tempFile, targetFile)
            cacheMetadata.writeTo(metadataFile, copiedBytes)
            return targetFile
        } catch (error: Exception) {
            runCatching { tempFile.delete() }
            throw error
        }
    }

    private fun assetExists(context: Context, assetPath: String): Boolean =
        runCatching {
            context.assets.open(assetPath).use { input -> input.read() }
            true
        }.getOrDefault(false)

    private fun isCachedAssetCurrent(
        targetFile: File,
        metadataFile: File,
        expectedMetadata: BundledModelCacheMetadata
    ): Boolean {
        if (!targetFile.exists() || targetFile.length() <= 0L || !metadataFile.exists()) {
            return false
        }

        val cachedMetadata = BundledModelCacheMetadata.readFrom(metadataFile) ?: return false
        if (cachedMetadata.assetPath != expectedMetadata.assetPath ||
            cachedMetadata.appVersionCode != expectedMetadata.appVersionCode
        ) {
            return false
        }

        return targetFile.length() == cachedMetadata.copiedBytes
    }

    private fun replaceFile(source: File, target: File) {
        if (target.exists() && !target.delete()) {
            throw IllegalStateException("Unable to replace cached Whisper model at ${target.absolutePath}")
        }

        if (!source.renameTo(target)) {
            source.copyTo(target, overwrite = true)
            if (!source.delete()) {
                runCatching { source.deleteOnExit() }
            }
        }
    }

    private data class AssetModel(
        val assetPath: String,
        val fileName: String
    )

    private data class BundledModelCacheMetadata(
        val assetPath: String,
        val appVersionCode: Long,
        val copiedBytes: Long = 0L
    ) {
        fun writeTo(metadataFile: File, copiedBytes: Long) {
            val properties = Properties().apply {
                setProperty("assetPath", assetPath)
                setProperty("appVersionCode", appVersionCode.toString())
                setProperty("copiedBytes", copiedBytes.toString())
            }

            metadataFile.outputStream().use { output ->
                properties.store(output, null)
            }
        }

        companion object {
            fun readFrom(metadataFile: File): BundledModelCacheMetadata? = runCatching {
                val properties = Properties()
                metadataFile.inputStream().use { input ->
                    properties.load(input)
                }

                BundledModelCacheMetadata(
                    assetPath = properties.getProperty("assetPath") ?: return null,
                    appVersionCode = properties.getProperty("appVersionCode")?.toLongOrNull() ?: return null,
                    copiedBytes = properties.getProperty("copiedBytes")?.toLongOrNull() ?: return null
                )
            }.getOrNull()
        }
    }

    private val englishOnlyRegex = Regex("""(?:^|[._-])en(?:[._-]|$)""")
}

private data class AudioDecodeEstimate(
    val estimatedPeakBytes: Long
)

private fun MediaExtractor.findAudioTrackIndex(): Int? {
    for (index in 0 until trackCount) {
        val format = getTrackFormat(index)
        val mimeType = format.getString(MediaFormat.KEY_MIME) ?: continue
        if (mimeType.startsWith("audio/")) {
            return index
        }
    }

    return null
}

private fun MediaFormat.getIntegerOrNull(key: String): Int? =
    if (containsKey(key)) getInteger(key) else null

private fun MediaFormat.getLongOrNull(key: String): Long? =
    if (containsKey(key)) getLong(key) else null

@Suppress("DEPRECATION")
private fun Context.appVersionCode(): Long =
    packageManager.getPackageInfo(packageName, 0).let { packageInfo ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            packageInfo.versionCode.toLong()
        }
    }
