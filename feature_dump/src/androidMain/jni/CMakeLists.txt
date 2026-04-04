cmake_minimum_required(VERSION 3.22.1)

project(sanctuary_whisper LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include(FetchContent)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(WHISPER_ALL_WARNINGS OFF CACHE BOOL "" FORCE)
set(GGML_ALL_WARNINGS OFF CACHE BOOL "" FORCE)
set(WHISPER_USE_SYSTEM_GGML OFF CACHE BOOL "" FORCE)

set(SANCTUARY_WHISPER_CPP_TAG "v1.8.4" CACHE STRING "Pinned whisper.cpp version")
set(SANCTUARY_WHISPER_CPP_DIR "" CACHE PATH "Optional local whisper.cpp checkout")

if (SANCTUARY_WHISPER_CPP_DIR)
    FetchContent_Declare(
        whispercpp
        SOURCE_DIR ${SANCTUARY_WHISPER_CPP_DIR}
    )
else()
    FetchContent_Declare(
        whispercpp
        GIT_REPOSITORY https://github.com/ggml-org/whisper.cpp.git
        GIT_TAG ${SANCTUARY_WHISPER_CPP_TAG}
        GIT_SHALLOW TRUE
    )
endif()

FetchContent_MakeAvailable(whispercpp)

find_library(LOG_LIB log)
find_library(ANDROID_LIB android)

add_library(sanctuary_whisper SHARED whisper_jni.cpp)

target_link_libraries(
    sanctuary_whisper
    PRIVATE
    whisper
    ${LOG_LIB}
    ${ANDROID_LIB}
)

target_include_directories(
    sanctuary_whisper
    PRIVATE
    ${whispercpp_SOURCE_DIR}/include
    ${whispercpp_SOURCE_DIR}/ggml/include
)
