add_executable(tika-wrap-demo demo1.cpp)
target_include_directories(tika-wrap-demo PUBLIC ${QT5_INCLUDE_DIRS} ../src)
target_link_libraries(tika-wrap-demo PUBLIC ${QT5_LIBRARIES} -L${JAVA_HOME}/lib/server -ljvm tika-wrap)
set_target_properties(tika-wrap-demo PROPERTIES LINK_FLAGS -Wl,-rpath=${JAVA_HOME}/lib/server)
