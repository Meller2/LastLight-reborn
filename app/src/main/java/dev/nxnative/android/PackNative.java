package dev.nxnative.android;

import android.content.Context;
import android.system.Os;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Direct JNI bridge to libnxpack.so (peanuts-native/android/ondevice/dm_pack.c).
 * Runs embedded CPython 3.14 tools (pack_android.py, shader_split.py, vk_android_build.py) on-device.
 */
public final class PackNative {
    private static final String TAG = "LastLightPack";
    private static volatile boolean loaded = false;
    private static File unpackedRoot = null;

    private PackNative() {}

    public static native int init0(String pythonHome);

    public static native int run0(String tool, String[] args, String[] env, String logPath);

    public static native int interrupt0();

    public static boolean isAvailable(Context context) {
        try (InputStream in = context.getAssets().open("pack/version.txt")) {
            return in.read() != -1;
        } catch (IOException e) {
            return false;
        }
    }

    public static synchronized File ensureStarted(Context context) throws IOException {
        if (unpackedRoot != null) return unpackedRoot;
        Context app = context.getApplicationContext();
        String version;
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(app.getAssets().open("pack/version.txt"), StandardCharsets.UTF_8))) {
            version = br.readLine().trim();
        }
        File base = new File(app.getFilesDir(), "pack");
        File dir = new File(base, version);
        if (!dir.isDirectory()) {
            File tmp = new File(base, version + ".tmp");
            deleteTree(tmp);
            unzipAsset(app, "pack/python.zip", new File(tmp, "python"));
            unzipAsset(app, "pack/tools.zip", new File(tmp, "repo"));
            if (!tmp.renameTo(dir)) {
                throw new IOException("Cannot finalize unpacked packer dir: " + dir);
            }
        }
        File[] existing = base.listFiles();
        if (existing != null) {
            for (File f : existing) {
                if (!f.getName().equals(version)) deleteTree(f);
            }
        }
        if (!loaded) {
            System.loadLibrary("nxpack");
            loaded = true;
        }
        File pyHome = new File(dir, "python");
        int rc = init0(pyHome.getAbsolutePath());
        if (rc != 0) {
            throw new IOException("dm_pack_init failed with code " + rc);
        }
        unpackedRoot = dir;
        return dir;
    }

    public static int runTool(Context context, String relToolPath, String[] argv, Map<String, String> envMap, String logPath) throws IOException {
        File root = ensureStarted(context);
        String[] envArr = new String[envMap.size()];
        int i = 0;
        for (Map.Entry<String, String> e : envMap.entrySet()) {
            envArr[i++] = e.getKey() + "=" + e.getValue();
            try {
                Os.setenv(e.getKey(), e.getValue(), true);
            } catch (Exception ignored) {}
        }
        File toolFile = new File(new File(root, "repo"), relToolPath);
        Log.i(TAG, "runTool: " + toolFile.getAbsolutePath());
        return run0(toolFile.getAbsolutePath(), argv, envArr, logPath);
    }

    public static void interruptAsync() {
        if (!loaded) return;
        Thread t = new Thread(() -> {
            try {
                interrupt0();
            } catch (Throwable ignored) {}
        }, "ll-pack-interrupt");
        t.setDaemon(true);
        t.start();
    }

    private static void unzipAsset(Context app, String assetPath, File dstDir) throws IOException {
        dstDir.mkdirs();
        String canonicalBase = dstDir.getCanonicalPath() + File.separator;
        byte[] buf = new byte[65536];
        try (InputStream is = app.getAssets().open(assetPath); ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File out = new File(dstDir, entry.getName());
                if (!out.getCanonicalPath().startsWith(canonicalBase)) {
                    throw new IOException("Blocked zip-slip entry: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    out.mkdirs();
                } else {
                    File p = out.getParentFile();
                    if (p != null) p.mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(out)) {
                        int n;
                        while ((n = zis.read(buf)) > 0) {
                            fos.write(buf, 0, n);
                        }
                    }
                    if (entry.getName().endsWith(".so") || entry.getName().contains("/bin/")) {
                        out.setExecutable(true, false);
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private static void deleteTree(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) {
                for (File k : kids) deleteTree(k);
            }
        }
        f.delete();
    }
}
