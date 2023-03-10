package com.silencefly96.module_views.widget.utils

import android.text.TextUtils

import android.os.Environment
import androidx.core.util.Consumer
import com.silencefly96.module_views.ViewApplication

import com.silencefly96.module_views.widget.LazyImageView.IFileHelper
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.lang.Exception
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore


/**
 * @author: silence
 * @date: 2021-05-27
 * @description: 简单文件处理工具
 */
class FileHelper : IFileHelper {
    //线程池
    val threadPool: ExecutorService = Executors.newFixedThreadPool(8)

    //相同链接的锁, 这里用LinkedHashMap限制一下储存的数量
    private val mUrlLockMap: MutableMap<String, Semaphore?> =
        object : LinkedHashMap<String, Semaphore?>() {
            override fun removeEldestEntry(eldest: Map.Entry<String?, Semaphore?>?): Boolean {
                return size >= 64 * 0.75
            }
        }

    //下载文件
    override fun download(url: String, resultHandler: Consumer<File>) {
        threadPool.execute {
            var downloadFile: File?

            //空路径
            if (TextUtils.isEmpty(url)) {
                //注意使用旧版本Consumer
                resultHandler.accept(null)
                return@execute
            }

            //检查本地缓存
            downloadFile = File(getLocalCacheFileName(url))
            if (downloadFile.exists()) {
                resultHandler.accept(downloadFile)
                return@execute
            }


            //同时下载文件会对同一个文件做修改，需要使用锁机制，使用信号量简单点
            var semaphore: Semaphore?
            synchronized(mUrlLockMap) {
                semaphore = mUrlLockMap[url]
                if (null == semaphore) {
                    semaphore = Semaphore(1)
                    mUrlLockMap[url] = semaphore
                }
            }

            //保证锁一定解锁
            try {
                semaphore!!.acquire()

                //再次检查是否有本地缓存,解开锁之后可能下载完成
                downloadFile = File(getLocalCacheFileName(url))
                if (downloadFile.exists()) {
                    resultHandler.accept(downloadFile)
                    return@execute
                }

                //网络下载部分
                var conn: HttpURLConnection? = null
                var inputStream: BufferedInputStream? = null
                var outputStream: FileOutputStream? = null
                val randomAccessFile: RandomAccessFile
                var cacheFile = File(getLocalCacheFileName(url))

                //要下载文件大小
                var remoteFileSize: Long = 0
                var sum: Long = 0
                val buffer = ByteArray(BUFFER_SIZE)
                try {
                    val conUrl = URL(url)
                    conn = conUrl.openConnection() as HttpURLConnection
                    remoteFileSize = conn.getHeaderField("Content-Length").toLong()
                    if (cacheFile.exists() && cacheFile.length() != remoteFileSize) {
                        var cacheFileSize: Long = cacheFile.length()
                        if (cacheFileSize > remoteFileSize) {
                            //如果出现文件错误，要删除
                            cacheFile.delete()
                            cacheFile = File(getLocalCacheFileName(url))
                            cacheFileSize = 0
                        }
                        conn.disconnect() // must reconnect
                        conn = conUrl.openConnection() as HttpURLConnection
                        conn.connectTimeout = 30000
                        conn.readTimeout = 30000
                        conn.instanceFollowRedirects = true
                        conn.setRequestProperty("User-Agent", "VcareCity")
                        conn.setRequestProperty("RANGE", "buffer=$cacheFileSize-")
                        conn.setRequestProperty(
                            "Accept",
                            "image/gif,image/x-xbitmap,application/msword,*/*"
                        )

                        //随机访问
                        randomAccessFile = RandomAccessFile(cacheFile, "rw")
                        randomAccessFile.seek(cacheFileSize)
                        inputStream = BufferedInputStream(conn.inputStream)

                        //继续写入文件
                        var size: Int
                        sum = cacheFileSize
                        while (inputStream.read(buffer).also { size = it } > 0) {
                            randomAccessFile.write(buffer, 0, size)
                            sum += size.toLong()
                        }
                        randomAccessFile.close()
                    } else {
                        conn.connectTimeout = 30000
                        conn.readTimeout = 30000
                        conn.instanceFollowRedirects = true
                        if (!cacheFile.exists()) {
                            cacheFile.createNewFile()
                        }
                        inputStream = BufferedInputStream(conn.inputStream)
                        outputStream = FileOutputStream(cacheFile)
                        var size: Int
                        while (inputStream.read(buffer).also { size = it } > 0) {
                            outputStream.write(buffer, 0, size)
                            sum += size.toLong()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    try {
                        conn?.disconnect()
                        inputStream?.close()
                        outputStream?.close()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                //下载结束
                val dwonloadFileSize: Long = cacheFile.length()
                if (dwonloadFileSize == remoteFileSize && dwonloadFileSize > 0) {
                    //成功
                    resultHandler.accept(File(getLocalCacheFileName(url)))
                } else {
                    resultHandler.accept(null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                //异常的话传递空值
                resultHandler.accept(null)
            } finally {
                //释放信号量
                semaphore!!.release()
            }
        }
    }

    //获取缓存文件名
    private fun getLocalCacheFileName(url: String): String {
        return CACHE_DIR + url.substring(url.lastIndexOf("/"))
    }

    override fun clearCache(url: String) {
        val path = getLocalCacheFileName(url)
        try {
            File(path).delete()
        }catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        //缓存路径，应用默认储存路径
        private val CACHE_DIR = "/data" + Environment.getDataDirectory().absolutePath + "/" +
                ViewApplication.context.packageName + "/cache/"

        //缓存大小
        private const val BUFFER_SIZE = 1024
    }
}
