# Install script for directory: /Users/parakramsingh/Desktop/Projects/Sanctuary/feature_dump/.cxx/Debug/6j5c3k12/armeabi-v7a/_deps/whispercpp-src/ggml

# Set the install prefix
if(NOT DEFINED CMAKE_INSTALL_PREFIX)
  set(CMAKE_INSTALL_PREFIX "/usr/local")
endif()
string(REGEX REPLACE "/$" "" CMAKE_INSTALL_PREFIX "${CMAKE_INSTALL_PREFIX}")

# Set the install configuration name.
if(NOT DEFINED CMAKE_INSTALL_CONFIG_NAME)
  if(BUILD_TYPE)
    string(REGEX REPLACE "^[^A-Za-z0-9_]+" ""
           CMAKE_INSTALL_CONFIG_NAME "${BUILD_TYPE}")
  else()
    set(CMAKE_INSTALL_CONFIG_NAME "Debug")
  endif()
  message(STATUS "Install configuration: \"${CMAKE_INSTALL_CONFIG_NAME}\"")
endif()

# Set the component getting installed.
if(NOT CMAKE_INSTALL_COMPONENT)
  if(COMPONENT)
    message(STATUS "Install component: \"${COMPONENT}\"")
    set(CMAKE_INSTALL_COMPONENT "${COMPONENT}")
  else()
    set(CMAKE_INSTALL_COMPONENT)
  endif()
endif()

# Install shared libraries without execute permission?
if(NOT DEFINED CMAKE_INSTALL_SO_NO_EXE)
  set(CMAKE_INSTALL_SO_NO_EXE "0")
endif()

# Is this installation the result of a crosscompile?
if(NOT DEFINED CMAKE_CROSSCOMPILING)
  set(CMAKE_CROSSCOMPILING "TRUE")
endif()

# Set default install directory permissions.
if(NOT DEFINED CMAKE_OBJDUMP)
  set(CMAKE_OBJDUMP "/Users/parakramsingh/Library/Android/sdk/ndk/27.0.12077973/toolchains/llvm/prebuilt/darwin-x86_64/bin/llvm-objdump")
endif()

if(NOT CMAKE_INSTALL_LOCAL_ONLY)
  # Include the install script for the subdirectory.
  include("/Users/parakramsingh/Desktop/Projects/Sanctuary/feature_dump/.cxx/Debug/6j5c3k12/armeabi-v7a/_deps/whispercpp-build/ggml/src/cmake_install.cmake")
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xUnspecifiedx" OR NOT CMAKE_INSTALL_COMPONENT)
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/lib" TYPE STATIC_LIBRARY FILES "/Users/parakramsingh/Desktop/Projects/Sanctuary/feature_dump/.cxx/Debug/6j5c3k12/armeabi-v7a/_deps/whispercpp-build/ggml/src/libggml.a")
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xUnspecifiedx" OR NOT CMAKE_INSTALL_COMPONENT)
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/include" TYPE FILE FILES
    "/Users/parakramsingh/Desktop/Projects/Sanctuary/feature_dump/.cxx/Debug/6j5c3k12/armeabi-v7a/_deps/whispercpp-src/ggml/include/ggml.h"
    "/Users/parakramsingh/Desktop/Projects/Sanctuary/feature_dump/.cxx/Debug/6j5c3k12/armeabi-v7a/_deps/whispercpp-src/ggml/include/ggml-cpu.h"
    "/Users/parakramsingh/Desktop/Projects/Sanctuary/feature_dump/.cxx/Debug/6j5c3k12/armeabi-v7a/_deps/whispercpp-src/ggml/include/ggml-alloc.h"
    "/Users/parakramsingh/Desktop/Projects/Sanctuary/feature_dump/.cxx/Debug/6j5c3k12/armeabi-v7a/_deps/whispercpp-src/ggml/include/ggml-backend.h"
    "/Users/parakramsingh/Desktop/Projects/Sanctuary/feature_dump/.cxx/Debug/6j5c3k12/armeabi-v7a/_deps/whispercpp-src/ggml/include/ggml-blas.h"
    "/Users/parakramsingh/Desktop/Projects/Sanctuary/feature_dump/.cxx/Debug/6j5c3k12/armeabi-v7a/_deps/whispercpp-src/ggml/include/ggml-cann.h"
    "/Users/parakramsingh/Desktop/Projects/Sanctuary/feature_dump/.cxx/Debug/6j5c3k12/armeabi-v7a/_deps/whispercpp-src/ggml/include/ggml-cpp.h"
    "/Users/parakramsingh/Desktop/Projects/Sanctuary/feature_dump/.cxx/Debug/6j5c3k12/armeabi-v7a/_deps/whispercpp-src/ggml/include/ggml-cuda.h"
    "/Users/parakramsingh/Desktop/Projects/Sanctuary/feature_dump/.cxx/Debug/6j5c3k12/armeabi-v7a/_deps/whispercpp-src/ggml/include/ggml-opt.h"
    "/Users/parakramsingh/Desktop/Projects/Sanctuary/feature_dump/.cxx/Debug/6j5c3k12/armeabi-v7a/_deps/whispercpp-src/ggml/include/ggml-metal.h"
    "/Users/parakramsingh/Desktop/Projects/Sanctuary/feature_dump/.cxx/Debug/6j5c3k12/armeabi-v7a/_deps/whispercpp-src/ggml/include/ggml-rpc.h"
    "/Users/parakramsingh/Desktop/Projects/Sanctuary/feature_dump/.cxx/Debug/6j5c3k12/armeabi-v7a/_deps/whispercpp-src/ggml/include/ggml-virtgpu.h"
    "/Users/parakramsingh/Desktop/Projects/Sanctuary/feature_dump/.cxx/Debug/6j5c3k12/armeabi-v7a/_deps/whispercpp-src/ggml/include/ggml-sycl.h"
    "/Users/parakramsingh/Desktop/Projects/Sanctuary/feature_dump/.cxx/Debug/6j5c3k12/armeabi-v7a/_deps/whispercpp-src/ggml/include/ggml-vulkan.h"
    "/Users/parakramsingh/Desktop/Projects/Sanctuary/feature_dump/.cxx/Debug/6j5c3k12/armeabi-v7a/_deps/whispercpp-src/ggml/include/ggml-webgpu.h"
    "/Users/parakramsingh/Desktop/Projects/Sanctuary/feature_dump/.cxx/Debug/6j5c3k12/armeabi-v7a/_deps/whispercpp-src/ggml/include/ggml-zendnn.h"
    "/Users/parakramsingh/Desktop/Projects/Sanctuary/feature_dump/.cxx/Debug/6j5c3k12/armeabi-v7a/_deps/whispercpp-src/ggml/include/ggml-openvino.h"
    "/Users/parakramsingh/Desktop/Projects/Sanctuary/feature_dump/.cxx/Debug/6j5c3k12/armeabi-v7a/_deps/whispercpp-src/ggml/include/gguf.h"
    )
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xUnspecifiedx" OR NOT CMAKE_INSTALL_COMPONENT)
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/lib" TYPE STATIC_LIBRARY FILES "/Users/parakramsingh/Desktop/Projects/Sanctuary/feature_dump/.cxx/Debug/6j5c3k12/armeabi-v7a/_deps/whispercpp-build/ggml/src/libggml-base.a")
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xUnspecifiedx" OR NOT CMAKE_INSTALL_COMPONENT)
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/lib/cmake/ggml" TYPE FILE FILES
    "/Users/parakramsingh/Desktop/Projects/Sanctuary/feature_dump/.cxx/Debug/6j5c3k12/armeabi-v7a/_deps/whispercpp-build/ggml/ggml-config.cmake"
    "/Users/parakramsingh/Desktop/Projects/Sanctuary/feature_dump/.cxx/Debug/6j5c3k12/armeabi-v7a/_deps/whispercpp-build/ggml/ggml-version.cmake"
    )
endif()

