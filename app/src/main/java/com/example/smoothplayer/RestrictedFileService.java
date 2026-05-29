package com.example.smoothplayer;

import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;

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

    public void destroy() {
        Log.i(TAG, "Shizuku user service destroyed");
    }
}
