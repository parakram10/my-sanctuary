#include <android/log.h>
#include <jni.h>

#include "whisper.h"

namespace {
constexpr const char *TAG = "SanctuaryWhisper";

void log_warn(const char *message) {
    __android_log_write(ANDROID_LOG_WARN, TAG, message);
}
} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_sanctuary_app_feature_dump_platform_WhisperCppNativeLib_00024Companion_initContext(
    JNIEnv *env,
    jobject /* thiz */,
    jstring model_path_str
) {
    if (model_path_str == nullptr) {
        return 0L;
    }

    const char *model_path = env->GetStringUTFChars(model_path_str, nullptr);
    whisper_context_params params = whisper_context_default_params();
    whisper_context *context = whisper_init_from_file_with_params(model_path, params);
    env->ReleaseStringUTFChars(model_path_str, model_path);
    return reinterpret_cast<jlong>(context);
}

JNIEXPORT void JNICALL
Java_sanctuary_app_feature_dump_platform_WhisperCppNativeLib_00024Companion_freeContext(
    JNIEnv * /* env */,
    jobject /* thiz */,
    jlong context_ptr
) {
    auto *context = reinterpret_cast<whisper_context *>(context_ptr);
    if (context != nullptr) {
        whisper_free(context);
    }
}

JNIEXPORT jint JNICALL
Java_sanctuary_app_feature_dump_platform_WhisperCppNativeLib_00024Companion_fullTranscribe(
    JNIEnv *env,
    jobject /* thiz */,
    jlong context_ptr,
    jint num_threads,
    jstring language_str,
    jfloatArray audio_data
) {
    auto *context = reinterpret_cast<whisper_context *>(context_ptr);
    if (context == nullptr || audio_data == nullptr || language_str == nullptr) {
        return -1;
    }

    const char *language = env->GetStringUTFChars(language_str, nullptr);
    jfloat *samples = env->GetFloatArrayElements(audio_data, nullptr);
    const jsize sample_count = env->GetArrayLength(audio_data);

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = false;
    params.no_context = true;
    params.no_timestamps = true;
    params.single_segment = false;
    params.n_threads = num_threads > 0 ? num_threads : 1;
    params.language = language;

    whisper_reset_timings(context);
    const int result = whisper_full(context, params, samples, sample_count);

    env->ReleaseFloatArrayElements(audio_data, samples, JNI_ABORT);
    env->ReleaseStringUTFChars(language_str, language);

    if (result != 0) {
        log_warn("whisper_full returned a non-zero status");
    }

    return result;
}

JNIEXPORT jint JNICALL
Java_sanctuary_app_feature_dump_platform_WhisperCppNativeLib_00024Companion_getTextSegmentCount(
    JNIEnv * /* env */,
    jobject /* thiz */,
    jlong context_ptr
) {
    auto *context = reinterpret_cast<whisper_context *>(context_ptr);
    return context == nullptr ? 0 : whisper_full_n_segments(context);
}

JNIEXPORT jstring JNICALL
Java_sanctuary_app_feature_dump_platform_WhisperCppNativeLib_00024Companion_getTextSegment(
    JNIEnv *env,
    jobject /* thiz */,
    jlong context_ptr,
    jint index
) {
    auto *context = reinterpret_cast<whisper_context *>(context_ptr);
    if (context == nullptr) {
        return env->NewStringUTF("");
    }

    const char *text = whisper_full_get_segment_text(context, index);
    return env->NewStringUTF(text == nullptr ? "" : text);
}

} // extern "C"
