#include <jni.h>
#include <string>
#include <cstdlib>
#include <cstring>
#include "node.h"
#include <pthread.h>
#include <unistd.h>
#include <android/log.h>

// 将 Node 的 stdout / stderr 重定向到 logcat
int pipe_stdout[2];
int pipe_stderr[2];
pthread_t thread_stdout;
pthread_t thread_stderr;
const char *ADBTAG = "nodejs-mobile";

void *thread_stderr_func(void *) {
    ssize_t redirect_size;
    char buf[2048];
    while ((redirect_size = read(pipe_stderr[0], buf, sizeof buf - 1)) > 0) {
        //__android_log will add a new line anyway.
        if (buf[redirect_size - 1] == '\n')
            --redirect_size;
        buf[redirect_size] = 0;
        __android_log_write(ANDROID_LOG_ERROR, ADBTAG, buf);
    }
    return 0;
}

void *thread_stdout_func(void *) {
    ssize_t redirect_size;
    char buf[2048];
    while ((redirect_size = read(pipe_stdout[0], buf, sizeof buf - 1)) > 0) {
        //__android_log will add a new line anyway.
        if (buf[redirect_size - 1] == '\n')
            --redirect_size;
        buf[redirect_size] = 0;
        __android_log_write(ANDROID_LOG_INFO, ADBTAG, buf);
    }
    return 0;
}

int start_redirecting_stdout_stderr() {
    // set stdout as unbuffered.
    setvbuf(stdout, 0, _IONBF, 0);
    pipe(pipe_stdout);
    dup2(pipe_stdout[1], STDOUT_FILENO);

    // set stderr as unbuffered.
    setvbuf(stderr, 0, _IONBF, 0);
    pipe(pipe_stderr);
    dup2(pipe_stderr[1], STDERR_FILENO);

    if (pthread_create(&thread_stdout, 0, thread_stdout_func, 0) == -1)
        return -1;
    pthread_detach(thread_stdout);

    if (pthread_create(&thread_stderr, 0, thread_stderr_func, 0) == -1)
        return -1;
    pthread_detach(thread_stderr);

    return 0;
}

// node 的 libUV 要求所有参数在连续内存中。
extern "C" jint JNICALL
Java_top_yunov_neteasy_NodeJS_startNodeWithArguments(
        JNIEnv *env,
        jobject /* this */,
        jobjectArray arguments) {

    // argc
    jsize argument_count = env->GetArrayLength(arguments);

    // 计算所有参数所需的连续内存大小
    int c_arguments_size = 0;
    for (int i = 0; i < argument_count; i++) {
        c_arguments_size += strlen(env->GetStringUTFChars((jstring) env->GetObjectArrayElement(arguments, i), 0));
        c_arguments_size++; // for '\0'
    }

    // 在连续内存中保存参数
    char *args_buffer = (char *) calloc(c_arguments_size, sizeof(char));

    // 传给 node 的 argv
    char *argv[argument_count];

    // 用于遍历 args_buffer 中每个参数的预期起始位置
    char *current_args_position = args_buffer;

    // 填充 args_buffer 与 argv
    for (int i = 0; i < argument_count; i++) {
        const char *current_argument = env->GetStringUTFChars((jstring) env->GetObjectArrayElement(arguments, i), 0);

        // 把当前参数复制到 args_buffer 中的预期位置
        strncpy(current_args_position, current_argument, strlen(current_argument));

        // 保存当前参数的起始位置到 argv
        argv[i] = current_args_position;

        // 移动到下一个参数的预期位置
        current_args_position += strlen(current_args_position) + 1;
    }

    // 启动线程把 stdout / stderr 重定向到 logcat
    if (start_redirecting_stdout_stderr() == -1) {
        __android_log_write(ANDROID_LOG_ERROR, ADBTAG, "Couldn't start redirecting stdout and stderr to logcat.");
    }

    // 启动 node（阻塞，直到 Node 退出；需在 Java 侧工作线程调用）
    return jint(node::Start(argument_count, argv));
}
