package io.github.zxjzerg.demo.media;

import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

public class MediaUtils {

    private static final String TAG = MediaUtils.class.getSimpleName();

    /** 获取视频文件 */
    public static void loadVideo(String url, OnResponseListener listener) {
        getMedia(url, getVideoCachePathFromUrl(url), listener);
    }

    public static void createCacheDir() {
        File videoDir = new File(MediaConstant.VIDEO_CACHE_DIR);
        if (!videoDir.exists()) {
            if (!videoDir.mkdirs()) {
                Log.e("createCacheDirs", "Directory not created");
            }
        }
    }

    /**
     * 根据视频的url获取本地缓存的地址
     *
     * @param url 语音的url
     * @return 本地缓存地址
     */
    private static String getVideoCachePathFromUrl(String url) {
        if (TextUtils.isEmpty(url)) {
            return null;
        } else {
            return MediaConstant.VIDEO_CACHE_DIR + getCacheKey(url);
        }
    }

    private static void getMedia(String url, String localPath, OnResponseListener listener) {
        if (TextUtils.isEmpty(url)) {
            Log.e(TAG, "无效的url");
            return;
        }
        File file = new File(localPath);
        File dir = file.getParentFile();
        final String fileName = file.getName();

        // 在缓存文件夹中搜索是否已存在该文件
        File[] matches = dir.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.startsWith(fileName);
            }
        });

        if (matches != null && matches.length != 0) {
            // 如果在缓存中存在则直接返回缓存中的路径
            // Log.d(TAG, "find file on storage");
            listener.onSuccess(matches[0].getPath());
        } else {
            // 如果在缓存中不存在则从服务器上下载
            Log.d(TAG, "download file from url: " + url);
            new DownloadTask(listener).execute(url, localPath);
        }
    }

    public interface OnResponseListener {

        void onSuccess(String path);

        void onError();

        void onProgress(int progress);
    }

    // usually, subclasses of AsyncTask are declared inside the activity class.
    // that way, you can easily modify the UI thread from here
    private static class DownloadTask extends AsyncTask<String, Integer, String> {

        private OnResponseListener mListener;
        private String mPath;

        public DownloadTask(OnResponseListener listener) {
            mListener = listener;
        }

        @Override
        protected String doInBackground(String... sUrl) {
            mPath = sUrl[1];
            InputStream input = null;
            OutputStream output = null;
            HttpURLConnection connection = null;
            try {
                URL url = new URL(sUrl[0]);
                connection = (HttpURLConnection) url.openConnection();
                connection.connect();

                // expect HTTP 200 OK, so we don't mistakenly save error report
                // instead of the file
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    return "Server returned HTTP " + connection.getResponseCode() + " " +
                        connection.getResponseMessage();
                }

                // this will be useful to display download percentage
                // might be -1: server did not report the length
                int fileLength = connection.getContentLength();

                // download the file
                input = connection.getInputStream();
                output = new FileOutputStream(mPath);

                byte[] data = new byte[4096];
                long total = 0;
                int count;
                while ((count = input.read(data)) != -1) {
                    // allow canceling with back button
                    if (isCancelled()) {
                        input.close();
                        return null;
                    }
                    total += count;
                    // publishing the progress....
                    if (fileLength > 0) // only if total length is known
                    {
                        publishProgress((int) (total * 100 / fileLength));
                    }
                    output.write(data, 0, count);
                }
            } catch (Exception e) {
                return e.toString();
            } finally {
                try {
                    if (output != null) {
                        output.close();
                    }
                    if (input != null) {
                        input.close();
                    }
                } catch (IOException ignored) {

                }
                if (connection != null) {
                    connection.disconnect();
                }
            }
            return null;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
            if (mListener != null) {
                mListener.onProgress(progress[0]);
            }
        }

        @Override
        protected void onPostExecute(String result) {
            if (result == null) {
                if (mListener != null) {
                    mListener.onSuccess(mPath);
                }
            } else {
                if (mListener != null) {
                    mListener.onError();
                }
            }
        }
    }

    /**
     * Creates a cache key for use with the L1 cache.
     *
     * @param url The URL of the request.
     */
    private static String getCacheKey(String url) {
        return md5(new StringBuilder(url.length() + 12).append(url).toString());
    }

    private static String md5(String s) {
        if (TextUtils.isEmpty(s)) {
            return "";
        }
        try {
            MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
            digest.update(s.getBytes("utf-8"));
            byte[] messageDigest = digest.digest();
            return toHexString(messageDigest);
        } catch (Exception e) {
            Log.e("StringUtils", "md5 error", e);
            return "";
        }
    }

    private static final char[] HEX_DIGITS = {
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
    };

    private static String toHexString(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (int i = 0; i < b.length; i++) {
            sb.append(HEX_DIGITS[(b[i] & 0xf0) >>> 4]);
            sb.append(HEX_DIGITS[b[i] & 0x0f]);
        }
        return sb.toString();
    }
}
