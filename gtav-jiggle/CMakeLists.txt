cmake_minimum_required(VERSION 3.20)
project(GtaVJiggle LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

if(MSVC)
  # Static CRT: an .asi loaded into the game should not depend on a redistributable.
  set(CMAKE_MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
  add_compile_options(/W4 /permissive- /utf-8)
endif()

# Pure logic: math, INI/config, layout probe, skeleton access. No game, no Windows.
add_library(jiggle_core STATIC
  src/IniParser.cpp
  src/Settings.cpp
  src/LayoutProbe.cpp
  src/SkeletonAccess.cpp)
target_include_directories(jiggle_core PUBLIC src)

add_executable(jiggle_tests tests/TestMain.cpp)
target_link_libraries(jiggle_tests PRIVATE jiggle_core)
target_compile_definitions(jiggle_tests PRIVATE JIGGLE_SOURCE_DIR="${CMAKE_CURRENT_SOURCE_DIR}")
enable_testing()
add_test(NAME jiggle_tests COMMAND jiggle_tests)

# The plugin itself: Windows + Script Hook V SDK only.
if(WIN32)
  set(SHV_SDK_DIR "" CACHE PATH "Root of the unpacked Script Hook V SDK (contains inc/ and lib/)")
  set(SHV_INC_DIR "" CACHE PATH "Override: folder holding main.h, natives.h, types.h")
  set(SHV_LIB_FILE "" CACHE FILEPATH "Override: ScriptHookV.lib import library")

  set(_shv_inc "${SHV_INC_DIR}")
  set(_shv_lib "${SHV_LIB_FILE}")
  if(NOT _shv_inc AND SHV_SDK_DIR)
    set(_shv_inc "${SHV_SDK_DIR}/inc")
  endif()
  if(NOT _shv_lib AND SHV_SDK_DIR)
    set(_shv_lib "${SHV_SDK_DIR}/lib/ScriptHookV.lib")
  endif()

  if(_shv_inc AND _shv_lib AND EXISTS "${_shv_inc}/main.h" AND EXISTS "${_shv_lib}")
    add_library(Jiggle SHARED src/Plugin.cpp src/Win32Support.cpp)
    target_include_directories(Jiggle PRIVATE "${_shv_inc}")
    target_link_libraries(Jiggle PRIVATE jiggle_core "${_shv_lib}" user32)
    set_target_properties(Jiggle PROPERTIES PREFIX "" SUFFIX ".asi" OUTPUT_NAME "Jiggle")
    add_custom_command(TARGET Jiggle POST_BUILD
      COMMAND ${CMAKE_COMMAND} -E copy_if_different
              "${CMAKE_CURRENT_SOURCE_DIR}/Jiggle.ini" "$<TARGET_FILE_DIR:Jiggle>")
  else()
    message(STATUS "Jiggle.asi skipped: pass -DSHV_SDK_DIR=<SDK root> "
                   "(needs inc/main.h and lib/ScriptHookV.lib; override with SHV_INC_DIR / SHV_LIB_FILE)")
  endif()
endif()
