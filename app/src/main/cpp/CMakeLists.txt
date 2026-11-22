cmake_minimum_required(VERSION 3.22)
project(lastlight_bridge C)

if(NOT NX_PEANUTS_ROOT)
  get_filename_component(NX_PEANUTS_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../peanuts-native" ABSOLUTE)
endif()

# 16 KB page alignment for Android 15+ devices
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384")

# Build libnxhost.so compiled specifically for Metro: Last Light Redux (0100F0400E850000)
set(GAME "0100F0400E850000" CACHE STRING "" FORCE)
set(NX_GENERIC_HOST OFF CACHE BOOL "" FORCE)
add_subdirectory("${NX_PEANUTS_ROOT}/android/host" "${CMAKE_CURRENT_BINARY_DIR}/nxhost_build")

# Build libnximport.so (.nsp / .xci / .nsz -> .nup native importer, dev.peanuts.importer.Native)
add_subdirectory("${NX_PEANUTS_ROOT}/dm/peanuts/import" "${CMAKE_CURRENT_BINARY_DIR}/nximport_build")

add_library(lastlight_bridge SHARED lastlight_jni.c)
target_include_directories(lastlight_bridge PRIVATE
  "${NX_PEANUTS_ROOT}/dm/host/android"
  "${NX_PEANUTS_ROOT}/dm/gfx/vk_android"
)
target_compile_options(lastlight_bridge PRIVATE -O2 -g -Wall -Wextra -Wno-unused-parameter)
target_link_options(lastlight_bridge PRIVATE -Wl,--no-undefined)
target_link_libraries(lastlight_bridge PRIVATE nxhost android log)
