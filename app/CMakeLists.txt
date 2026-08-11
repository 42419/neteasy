cmake_minimum_required(VERSION 3.22.1)

# nodejs-mobile JNI 桥（Java 侧 NodeJS.start 的 native 实现）
add_library(native-lib SHARED src/main/cpp/native-lib.cpp)

# Node.js 头文件（node.h / uv.h 等）
include_directories(libnode/include/node/)

# 导入预编译的 libnode.so（libnode/bin/<ABI>/libnode.so）
add_library(libnode SHARED IMPORTED)
set_target_properties(libnode PROPERTIES
        IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/libnode/bin/${ANDROID_ABI}/libnode.so)

find_library(log-lib log)

target_link_libraries(native-lib libnode ${log-lib})
