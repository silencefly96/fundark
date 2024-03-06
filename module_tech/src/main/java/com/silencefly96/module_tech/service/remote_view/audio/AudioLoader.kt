@file:Suppress("unused")

package com.silencefly96.module_tech.service.remote_view.audio

import android.app.Activity
import android.app.LoaderManager
import android.content.ContentUris
import android.content.Context
import android.content.CursorLoader
import android.content.Loader
import android.database.Cursor
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Parcelable
import android.provider.MediaStore
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.ExecutorService


@Suppress("DeprecatedCallableAddReplaceWith")
public object AudioLoader {

    /**
     * 使用ContentResolver获取所有音频文件(耗时操作)
     *
     * @param context 上下文
     * @param path 在Music路径下的相对路径，不填全局搜索
     * @return 所有音频文件列表
     */
    fun loadAudiosByMediaStore(context: Context, path: String? = null): List<Audio> {
        val result: MutableList<Audio> = ArrayList()

        // 查询音频的uri
        val uri: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        // 查询条目
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE
        )

        // 查询条件
        var selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        var selectionArg = ""

        // 是否增加路径
        path?.let {
            // Android 10，路径保存在RELATIVE_PATH
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                selection += " AND ${MediaStore.Audio.AudioColumns.RELATIVE_PATH} like ?"
                selectionArg += "%" + Environment.DIRECTORY_MUSIC + File.separator + it + "%"
            } else {
                val dstPath = StringBuilder().let { sb ->
                    sb.append(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)!!.path)
                    sb.append(File.separator)
                    sb.append(it)
                    sb.toString()
                }
                selection += " AND ${MediaStore.Video.Media.DATA} like ?"
                selectionArg += "%$dstPath%"
            }
        }


        // 排序
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        // 查询
        val cursor = context.contentResolver.query(
            uri,
            projection,
            selection,
            if (selectionArg.isNotEmpty()) arrayOf(selectionArg) else null,
            sortOrder
        )

        // 处理结果
        cursor?.let {
            while (it.moveToNext()) {
                try {
                    val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                    result.add(
                        Audio(
                        id,
                        it.getString(it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)),
                        it.getString(it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)),
                        it.getString(it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)),
                        it.getLong(it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)),
                        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                        it.getLong(it.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE))
                    )
                    )
                }catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        cursor?.close()
        return result
    }

    /**
     * 使用协程获取所有音频文件
     *
     * @param context 上下文
     * @param callback 结果回调
     * @param lifecycle 生命周期，用于提供协程作用域
     * @param path 在Music路径下的相对路径，不填全局搜索
     * @return 所有音频文件列表
     */
    public fun loadAudioByMediaStoreCoroutines(
        context: Context,
        callback: OnAudioPrepared,
        lifecycle: Lifecycle,
        path: String? = null
    ){
        // 使用lifecycle的协程作用域，在lifeowner注销时停止协程
        lifecycle.coroutineScope.launch(Dispatchers.IO) {
            val result = loadAudiosByMediaStore(context, path)
            lifecycle.coroutineScope.launch(Dispatchers.Main) {
                callback.onAudioPrepared(result)
            }
        }
    }

    /**
     * 通过AudioAsyncTask实现异步加载
     *
     * @param activity 上下文，用来到主线程返回结果
     * @param callback 结果回调
     * @param threadPool 运行线程的线程池，设置为null的话直接用threaa运行
     * @param path 在Music路径下的相对路径，不填全局搜索
     * @return 所有音频文件列表
     */
    public fun loadAudiosByMediaStoreAsync(
        activity: Activity,
        callback: OnAudioPrepared,
        threadPool: ExecutorService? = null,
        path: String? = null
    ) {
        val runnable = Runnable {
            val result = loadAudiosByMediaStore(activity, path)
            // 切换到主线程
            activity.runOnUiThread {
                callback.onAudioPrepared(result)
            }
        }

        // 优先使用线程池运行
        if (threadPool != null) {
            threadPool.execute(runnable)
        } else {
            Thread(runnable).start()
        }
    }

    /**
     * 通过AudioAsyncTask实现异步加载
     *
     * @param context 上下文
     * @param callback 结果回调
     * @param path 在Music路径下的相对路径，不填全局搜索
     * @return 所有音频文件列表
     */
    @Deprecated("AudioAsyncTask被废弃了")
    public fun loadAudiosByMediaStoreAsync(
        context: Context, callback: OnAudioPrepared, path: String? = null) {
        AudioAsyncTask(context, callback, path).execute()
    }

    /**
     * 自定义AsyncTask
     */
    private class AudioAsyncTask(
        context: Context,
        val callback: OnAudioPrepared,
        val path: String? = null
    ) : AsyncTask<String, Void, List<Audio>?>() {

        // 弱引用防止内存泄露
        private val contextRef: WeakReference<Context>
        init {
            contextRef = WeakReference(context)
        }

        @Deprecated("Deprecated in Java")
        override fun doInBackground(vararg params: String?): List<Audio>? {
            var result: List<Audio>? = null
            contextRef.get()?.let {
                result = loadAudiosByMediaStore(it, path)
            }
            return result
        }

        @Deprecated("Deprecated in Java")
        public override fun onPostExecute(result: List<Audio>?) {
            callback.onAudioPrepared(result)
        }
    }


    /**
     * 通过Loader机制实现音频查找
     *
     * @param activity 上下文，用来到主线程返回结果
     * @param callback 结果回调
     * @param path 在Music路径下的相对路径，不填全局搜索
     * @return 所有音频文件列表
     */
    @Deprecated("LoaderManager在targetSdkVersion28被废弃")
    public fun loadAudiosByLoader(
        activity: Activity,
        callback: OnAudioPrepared,
        path: String? = null
    ) {
        // 查询音频的uri
        val uri: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        // 查询条目
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE
        )

        // 查询条件
        var selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        var selectionArg = ""

        // 是否增加路径
        path?.let {
            // Android 10，路径保存在RELATIVE_PATH
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                selection += " AND ${MediaStore.Audio.AudioColumns.RELATIVE_PATH} like ?"
                selectionArg += "%" + Environment.DIRECTORY_MUSIC + File.separator + it + "%"
            } else {
                val dstPath = StringBuilder().let { sb ->
                    sb.append(activity.getExternalFilesDir(Environment.DIRECTORY_MUSIC)!!.path)
                    sb.append(File.separator)
                    sb.append(it)
                    sb.toString()
                }
                selection += " AND ${MediaStore.Video.Media.DATA} like ?"
                selectionArg += "%$dstPath%"
            }
        }

        // 排序
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        // 初始化Loader
        activity.loaderManager.initLoader(0, null,
            object : LoaderManager.LoaderCallbacks<Cursor> {

                @Deprecated("Deprecated in Java")
                override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor> {
                    // 创建loader
                    return CursorLoader(
                        activity,
                        uri,
                        projection,
                        selection,
                        if (selectionArg.isNotEmpty()) arrayOf(selectionArg) else null,
                        sortOrder
                    )
                }

                @Deprecated("Deprecated in Java")
                override fun onLoadFinished(loader: Loader<Cursor>?, data: Cursor?) {
                    val result: MutableList<Audio> = ArrayList()
                    // 获取结果
                    data?.let {
                        while (it.moveToNext()) {
                            try {
                                val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                                result.add(
                                    Audio(
                                    id,
                                    it.getString(it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)),
                                    it.getString(it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)),
                                    it.getString(it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)),
                                    it.getLong(it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)),
                                    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                                    it.getLong(it.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE))
                                )
                                )
                            }catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }

                    data?.close()
                    callback.onAudioPrepared(result)
                }

                @Deprecated("Deprecated in Java")
                override fun onLoaderReset(loader: Loader<Cursor>?) {}
            })
    }

    /**
     * 音频数据类
     */
    @Parcelize
    public data class Audio(
        val id: Long,
        val title: String,
        val artist: String,
        val album: String,
        val duration: Long,
        val uri: Uri,
        val size: Long
    ) : Parcelable

    /**
     * 异步获取结果回调
     */
    public interface OnAudioPrepared {
        fun onAudioPrepared(result: List<Audio>?)
    }
}