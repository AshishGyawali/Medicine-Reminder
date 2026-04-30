package com.example.medicinereminderapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class MedicineImageStore {
    private static final String TAG = "MedicineImageStore";
    private static final String DIR_NAME = "medicine_images";

    private MedicineImageStore() {}

    public static String saveCopy(Context context, Uri sourceUri) throws IOException {
        File dir = new File(context.getFilesDir(), DIR_NAME);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Cannot create image directory");
        }
        File dest = new File(dir, "med_" + System.currentTimeMillis() + ".jpg");
        try (InputStream is = context.getContentResolver().openInputStream(sourceUri);
             OutputStream os = new FileOutputStream(dest)) {
            if (is == null) throw new IOException("Cannot open source URI");
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) os.write(buf, 0, n);
        }
        return dest.getAbsolutePath();
    }

    public static Bitmap loadBitmap(String path, int maxDim) {
        if (path == null || path.isEmpty()) return null;
        File f = new File(path);
        if (!f.exists()) return null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, bounds);
            int sample = 1;
            int max = Math.max(bounds.outWidth, bounds.outHeight);
            while (max / sample > maxDim) sample *= 2;
            BitmapFactory.Options decode = new BitmapFactory.Options();
            decode.inSampleSize = sample;
            return BitmapFactory.decodeFile(path, decode);
        } catch (Exception e) {
            Log.e(TAG, "loadBitmap failed for " + path, e);
            return null;
        }
    }

    public static void delete(String path) {
        if (path == null || path.isEmpty()) return;
        try {
            File f = new File(path);
            if (f.exists()) f.delete();
        } catch (Exception e) {
            Log.w(TAG, "delete failed", e);
        }
    }
}
