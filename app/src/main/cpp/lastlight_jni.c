// LastLight Reborn JNI Bridge -> libnxhost.so (Metro: Last Light Redux 0100F0400E850000)
#define _GNU_SOURCE
#include <jni.h>
#include <android/log.h>
#include <android/native_window_jni.h>
#include <fcntl.h>
#include <pthread.h>
#include <stdatomic.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/prctl.h>
#include <time.h>
#include <unistd.h>

#include "input_android.h"
#include "vk_android.h"

#define LOG_TAG "LastLight"
#define HOST_STACK (8u << 20)

int nxhost_main(int argc, char **argv);

static struct {
    int argc;
    char **argv;
} g_run;
static _Atomic int g_started;
static int g_log_fd = -1;
static int g_pipe_r = -1;

static void *logcat_thread(void *p) {
    (void)p;
    prctl(PR_SET_NAME, "ll_logcat");
    char buf[2048];
    char line[2048];
    size_t lpos = 0;
    ssize_t n;
    while ((n = read(g_pipe_r, buf, sizeof buf)) > 0) {
        if (g_log_fd >= 0) {
            ssize_t off = 0;
            while (off < n) {
                ssize_t w = write(g_log_fd, buf + off, (size_t)(n - off));
                if (w <= 0) break;
                off += w;
            }
        }
        for (ssize_t i = 0; i < n; i++) {
            char c = buf[i];
            if (c == '\n' || lpos >= sizeof(line) - 1) {
                line[lpos] = 0;
                if (lpos > 0) {
                    int prio = strstr(line, "[RED]") ? ANDROID_LOG_ERROR : ANDROID_LOG_INFO;
                    __android_log_write(prio, LOG_TAG, line);
                }
                lpos = 0;
            } else {
                line[lpos++] = c;
            }
        }
    }
    return NULL;
}

static void setup_logging(const char *log_path) {
    if (log_path && *log_path) {
        g_log_fd = open(log_path, O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0664);
    }
    int pf[2];
    if (pipe2(pf, O_CLOEXEC) == 0) {
        g_pipe_r = pf[0];
        dup2(pf[1], STDOUT_FILENO);
        dup2(pf[1], STDERR_FILENO);
        close(pf[1]);
        setvbuf(stdout, NULL, _IOLBF, 0);
        setvbuf(stderr, NULL, _IONBF, 0);
        pthread_t lt;
        if (pthread_create(&lt, NULL, logcat_thread, NULL) == 0) {
            pthread_detach(lt);
        }
    }
}

static void *host_thread(void *p) {
    (void)p;
    prctl(PR_SET_NAME, "ll_nxhost");
    fprintf(stderr, "[lastlight] starting nxhost_main (8 MiB stack)\n");
    int rc = nxhost_main(g_run.argc, g_run.argv);
    fprintf(stderr, "[lastlight] nxhost_main exited with %d\n", rc);
    struct timespec ts = {0, 250000000};
    nanosleep(&ts, NULL);
    _exit(rc);
}

static char *dup_jstr(JNIEnv *env, jstring s) {
    if (!s) return strdup("");
    const char *c = (*env)->GetStringUTFChars(env, s, NULL);
    char *r = strdup(c ? c : "");
    if (c) (*env)->ReleaseStringUTFChars(env, s, c);
    return r;
}

JNIEXPORT jboolean JNICALL
Java_dev_lastlight_reborn_nativecore_NativeBridge_startHost(
    JNIEnv *env, jclass cls, jstring jPatchedDir, jobjectArray jEnvVars, jstring jLogPath) {
    (void)cls;
    if (atomic_exchange(&g_started, 1) != 0) return JNI_FALSE;

    char *log_path = dup_jstr(env, jLogPath);
    setup_logging(log_path);
    free(log_path);

    if (jEnvVars) {
        jsize n = (*env)->GetArrayLength(env, jEnvVars);
        for (jsize i = 0; i < n; i++) {
            jstring s = (jstring)(*env)->GetObjectArrayElement(env, jEnvVars, i);
            char *kv = dup_jstr(env, s);
            char *eq = strchr(kv, '=');
            if (eq && eq > kv) {
                *eq = 0;
                setenv(kv, eq + 1, 1);
                fprintf(stderr, "[lastlight] env %s=%s\n", kv, eq + 1);
            }
            free(kv);
            (*env)->DeleteLocalRef(env, s);
        }
    }

    char *patched = dup_jstr(env, jPatchedDir);
    g_run.argc = 2;
    g_run.argv = calloc(3, sizeof(char *));
    g_run.argv[0] = strdup("lastlight");
    g_run.argv[1] = patched;

    pthread_attr_t attr;
    pthread_attr_init(&attr);
    pthread_attr_setstacksize(&attr, HOST_STACK);
    pthread_t th;
    int err = pthread_create(&th, &attr, host_thread, NULL);
    pthread_attr_destroy(&attr);
    if (err != 0) {
        fprintf(stderr, "[RED] [lastlight] pthread_create failed: %d\n", err);
        return JNI_FALSE;
    }
    pthread_detach(th);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_dev_lastlight_reborn_nativecore_NativeBridge_setWindow(JNIEnv *env, jclass cls, jobject jSurface) {
    (void)cls;
    ANativeWindow *w = jSurface ? ANativeWindow_fromSurface(env, jSurface) : NULL;
    nxhost_set_window(w);
    if (w) ANativeWindow_release(w);
}

JNIEXPORT void JNICALL
Java_dev_lastlight_reborn_nativecore_NativeBridge_lifecycle(JNIEnv *env, jclass cls, jint event) {
    (void)env; (void)cls;
    nxhost_lifecycle((int)event);
}

JNIEXPORT void JNICALL
Java_dev_lastlight_reborn_nativecore_NativeBridge_audioInterrupt(JNIEnv *env, jclass cls, jboolean on) {
    (void)env; (void)cls;
    nxhost_audio_interrupt(on == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_dev_lastlight_reborn_nativecore_NativeBridge_touchControlsEnable(JNIEnv *env, jclass cls, jboolean on) {
    (void)env; (void)cls;
    nxhost_touch_controls_enable(on == JNI_TRUE);
}

JNIEXPORT jint JNICALL
Java_dev_lastlight_reborn_nativecore_NativeBridge_inputDeviceAdded(
    JNIEnv *env, jclass cls, jint dev, jstring jName, jint labelled) {
    (void)cls;
    char *name = dup_jstr(env, jName);
    int p = nxhost_input_device_added((int)dev, name, (int)labelled);
    free(name);
    return (jint)p;
}

JNIEXPORT void JNICALL
Java_dev_lastlight_reborn_nativecore_NativeBridge_inputDeviceRemoved(JNIEnv *env, jclass cls, jint dev) {
    (void)env; (void)cls;
    nxhost_input_device_removed((int)dev);
}

JNIEXPORT jboolean JNICALL
Java_dev_lastlight_reborn_nativecore_NativeBridge_inputKey(
    JNIEnv *env, jclass cls, jint dev, jint keycode, jboolean down) {
    (void)env; (void)cls;
    return nxhost_input_key((int)dev, (int)keycode, down == JNI_TRUE) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_dev_lastlight_reborn_nativecore_NativeBridge_inputAxes(
    JNIEnv *env, jclass cls, jint dev, jintArray jAxes, jfloatArray jVals, jint count) {
    (void)cls;
    if (!jAxes || !jVals || count <= 0 || count > 16) return JNI_FALSE;
    jint axes[16];
    jfloat vals[16];
    (*env)->GetIntArrayRegion(env, jAxes, 0, count, axes);
    (*env)->GetFloatArrayRegion(env, jVals, 0, count, vals);
    return nxhost_input_axes((int)dev, axes, vals, (int)count) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_dev_lastlight_reborn_nativecore_NativeBridge_inputDeadzone(JNIEnv *env, jclass cls, jfloat dz) {
    (void)env; (void)cls;
    nxhost_input_deadzone((float)dz);
}

JNIEXPORT void JNICALL
Java_dev_lastlight_reborn_nativecore_NativeBridge_fpsCapSet(JNIEnv *env, jclass cls, jint fps) {
    (void)env; (void)cls;
    nxhost_fps_cap_set((int)fps);
}

JNIEXPORT jint JNICALL
Java_dev_lastlight_reborn_nativecore_NativeBridge_getFps(JNIEnv *env, jclass cls, jfloatArray jOut) {
    (void)cls;
    if (!jOut) return 0;
    jsize n = (*env)->GetArrayLength(env, jOut);
    if (n > 6) n = 6;
    float tmp[6] = {0};
    int r = nxhost_fps(tmp, (int)n);
    (*env)->SetFloatArrayRegion(env, jOut, 0, n, tmp);
    return (jint)r;
}

JNIEXPORT void JNICALL
Java_dev_lastlight_reborn_nativecore_NativeBridge_terminateProcess(JNIEnv *env, jclass cls) {
    (void)env; (void)cls;
    _exit(0);
}
