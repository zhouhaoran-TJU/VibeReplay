package com.example.smoothplayer;

import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class RestrictedFileService extends IRestrictedFileService.Stub {
    private static final String TAG = "RestrictedFileService";

    public RestrictedFileService() {
        Log.i(TAG, "Shizuku user service started");
    }

    @Override
    public ParcelFileDescriptor openFile(String path) throws RemoteException {
        try {
            return ParcelFileDescriptor.open(new File(path), ParcelFileDescriptor.MODE_READ_ONLY);
        } catch (FileNotFoundException exception) {
            throw new RemoteException("Cannot open file: " + exception.getMessage());
        }
    }

    @Override
    public String[] listFiles(String path) throws RemoteException {
        File dir = new File(path);
        File[] files = dir.listFiles();
        if (files == null) {
            throw new RemoteException("Cannot list directory: " + path);
        }
        List<String> entries = new ArrayList<>();
        for (File file : files) {
            String prefix = file.isDirectory() ? "D|" : "F|";
            entries.add(prefix + file.length() + "|" + file.lastModified() + "|" + file.getName()
                    + "|" + file.getAbsolutePath());
        }
        Collections.sort(entries, (left, right) -> {
            boolean leftDir = left.startsWith("D|");
            boolean rightDir = right.startsWith("D|");
            if (leftDir != rightDir) {
                return leftDir ? -1 : 1;
            }
            return left.toLowerCase(Locale.US).compareTo(right.toLowerCase(Locale.US));
        });
        return entries.toArray(new String[0]);
    }

    @Override
    public boolean deleteFile(String path) throws RemoteException {
        File file = new File(path);
        if (!file.exists() || file.isDirectory()) {
            return false;
        }
        return file.delete();
    }

    public void destroy() {
        Log.i(TAG, "Shizuku user service destroyed");
    }
}
