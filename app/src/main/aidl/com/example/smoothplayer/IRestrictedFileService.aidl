package com.example.smoothplayer;

interface IRestrictedFileService {
    ParcelFileDescriptor openFile(String path);
}
