package dev.peanuts.importer;

/**
 * Direct JNI bridge to libnximport.so (peanuts-native/dm/peanuts/import/nximport_jni.c).
 * Unpacks Switch .nsp / .xci / .nsz / .xcz dumps into .nup packages and extracts icons/fonts on-device.
 */
public final class Native {
    public interface Progress {
        void on(String step, long done, long total);
    }

    private static volatile boolean loaded = false;

    public static synchronized void ensureLoaded() {
        if (!loaded) {
            System.loadLibrary("nximport");
            loaded = true;
        }
    }

    public static native int keys(String prodKeysPath);

    public static native String probe(String file);

    public static native int icon(String file, String titleId, String outPng);

    public static native int writeNup(String out, String[] inputs, String baseTitleId, Progress cb);

    public static native int firmwareFonts(String firmwareDir, String outDir);

    public static native String lastError();

    public static native void setThreads(int n);

    public static native void cancel(boolean on);

    private Native() {}
}
