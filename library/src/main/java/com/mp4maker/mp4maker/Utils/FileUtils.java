package com.mp4maker.mp4maker.Utils;

import java.io.File;
import java.io.IOException;

public final class FileUtils {
    public static boolean fileExsit(String filePath) throws IOException {
        File file = new File(filePath);
        return file.exists() && file.isFile();
    }

    public static boolean dirExsit(String filePath) throws IOException {
        File file = new File(filePath);
        return file.exists() && file.isDirectory();
    }

    public static boolean deleteFileOrDir(String filePath) throws IOException {
        File file = new File(filePath);
        return deleteRecursive(file);
    }

    public static boolean deleteRecursive(File fileOrDirectory) throws IOException {
        if (fileOrDirectory.isDirectory())
            for (File child : fileOrDirectory.listFiles())
                deleteRecursive(child);

        return fileOrDirectory.delete();
    }
}
