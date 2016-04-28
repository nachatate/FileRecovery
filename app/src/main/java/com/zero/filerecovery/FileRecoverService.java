package com.zero.filerecovery;

import android.app.Service;
import android.content.Intent;
import android.os.Environment;
import android.os.FileObserver;
import android.os.IBinder;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

public class FileRecoverService extends Service {
    public static final String ACTION_OBSERVE = "com.zero.filerecovery.OBSERVE";
    public static final String ACTION_RECOVER = "com.zero.filerecovery.RECOVER";
    public static final String EXTRA_PATH = "com.zero.filerecovery.EXTRA_PATH";
    private static final String TAG = FileRecoverService.class.getSimpleName();
    private static final String RECOVERY_DIR = Environment.getExternalStorageDirectory().getAbsolutePath() + "/recovery";
    private static final int BUFFER_SIZE = 1024;
    private static HashMap<File, RecoverFileObserver> myFileObserverHashMap = new HashMap<>();
    private static HashMap<String, RecoverInfo> recoverInfoHashMap = new HashMap<>();
    private static ConcurrentHashMap<String, Boolean> mRecoveredFiles = new ConcurrentHashMap<>();

    @Override
    public void onCreate() {
        super.onCreate();
        checkDirs();
    }

    private void checkDirs() {
        new File(RECOVERY_DIR).mkdirs();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_OBSERVE.equals(action)) {
                String path = intent.getStringExtra(EXTRA_PATH);
                Log.d(TAG, "observing " + path);
                if (path != null) {
                    observe(path);
                }
            } else if (ACTION_RECOVER.equals(action)) {
                recover();
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    private void recover() {
        for (String originPath : recoverInfoHashMap.keySet()) {
            Log.d(TAG, "recovering " + originPath);
            RecoverInfo info = recoverInfoHashMap.get(originPath);
            File recoverFile = new File(info.recoveryPath);
            if (!recoverFile.exists()) continue;
            String MD5 = null;
            try {
                MD5 = MD5Utils.getMd5ByFile(recoverFile);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            if (MD5 == null || !MD5.equals(info.originMd5)) continue;
            String path = originPath.substring(0, originPath.lastIndexOf("/"));
            File pathFile = new File(path);
            pathFile.mkdirs();
            recoverFile.renameTo(new File(originPath));
            Log.d(TAG, info.recoveryPath + " renameTo " + originPath);
        }
    }

    private void observe(String path) {
        File dir = new File(path);

        mRecoveredFiles.clear();
        recoverInfoHashMap.clear();
        myFileObserverHashMap.clear();

        for (File f : dir.listFiles()) {
            if (f.isDirectory()) {
                observe(f.getAbsolutePath());
            } else {
                Log.d(TAG, "observing file " + f);
                RecoverFileObserver fileObserver = new RecoverFileObserver(f.getAbsolutePath());
                fileObserver.startWatching();
                myFileObserverHashMap.put(f, fileObserver);
            }
        }
    }

    private void copyFile(String srcPath, String destPath) {
        FileInputStream inputStream = null;
        FileOutputStream outputStream = null;
        try {
            int bytesum = 0;
            int byteread = 0;
            inputStream = new FileInputStream(srcPath);
            outputStream = new FileOutputStream(destPath);
            byte[] buffer = new byte[BUFFER_SIZE];
            while ((byteread = inputStream.read(buffer)) != -1) {
                bytesum += byteread; // 字节数 文件大小
                outputStream.write(buffer, 0, byteread);
            }
        } catch (Exception e) {
            System.out.println("复制单个文件操作出错");
            e.printStackTrace();

        } finally {
            if (outputStream != null) {
                try {
                    outputStream.flush();
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void copyFile(FileInputStream inStream, String inputPath, String newPath, int length, String originMd5) {
        Boolean doing = mRecoveredFiles.get(inputPath);
        if (doing != null && doing) {
            Log.v(TAG, "done");
            return;
        }
        Log.e(TAG, "copyFile " + inputPath + " to " + newPath);
        mRecoveredFiles.put(inputPath, true);
        FileOutputStream fs = null;
        try {
            int bytesum = 0;
            int byteread = 0;
            fs = new FileOutputStream(newPath);
            byte[] buffer = new byte[BUFFER_SIZE];
            while ((byteread = inStream.read(buffer)) != -1) {
                bytesum += byteread; // 字节数 文件大小
                fs.write(buffer, 0, byteread);
            }
        } catch (Exception e) {
            System.out.println("复制单个文件操作出错");
            e.printStackTrace();
            try {
                byte[] buffer = new byte[length % BUFFER_SIZE];
                int byteread = inStream.read(buffer);
                if (byteread != -1 && fs != null) {
                    fs.write(buffer, 0, byteread);
                }
            } catch (IOException e1) {
                e1.printStackTrace();
            }

        } finally {
            if (fs != null) {
                try {
                    fs.flush();
                    inStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        String newMD5 = "";
        try {
            newMD5 = MD5Utils.getMd5ByFile(new File(newPath));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        Log.e(TAG, "copyFile Done" + inputPath + " to " + newPath);
        Log.e(TAG, "new md5 " + newMD5);
        Log.e(TAG, "old md5 " + originMd5);
        Log.e(TAG, "md5 " + (newMD5.equals(originMd5) ? "same" : "diff"));
    }

    class RecoverInfo {
        String originMd5;
        String recoveryPath;
    }

    class RecoverFileObserver extends FileObserver {
        private final String TAG = RecoverFileObserver.class.getSimpleName();
        String originMd5;
        FileInputStream fileInputStream;
        String path;
        String newPath;
        private int length = 0;

        public RecoverFileObserver(String path) {
            super(path);
            this.path = path;
            this.newPath = RECOVERY_DIR + "/" + MD5Utils.getMD5Str(path);

            try {
                originMd5 = MD5Utils.getMd5ByFile(new File(path));
                fileInputStream = new FileInputStream(path);
                length = fileInputStream.available();
            } catch (IOException e) {
                e.printStackTrace();
            }
            RecoverInfo info = new RecoverInfo();
            info.originMd5 = originMd5;
            info.recoveryPath = newPath;
            recoverInfoHashMap.put(path, info);
        }

        @Override
        public void onEvent(int event, String path) {
            if (event == FileObserver.ACCESS) return;
            Log.v(TAG, this.path + " | " + path + " : " + event);
            switch (event) {
                case FileObserver.ATTRIB:
                    copyFile(fileInputStream, this.path, this.newPath, length, originMd5);
                    break;
                case FileObserver.DELETE:
                    copyFile(fileInputStream, this.path, this.newPath, length, originMd5);
                    break;
                case FileObserver.DELETE_SELF:
                    copyFile(fileInputStream, this.path, this.newPath, length, originMd5);
                    break;
                case 32768:
                    stopWatching();
                    File file = new File(this.path);
                    if (file.exists()) {
                        RecoverFileObserver fileObserver = new RecoverFileObserver(this.path);
                        fileObserver.startWatching();
                        myFileObserverHashMap.put(file, fileObserver);
                    } else {
                        myFileObserverHashMap.remove(file);
                    }
                    break;
                default:
                    break;
            }
        }
    }
}
