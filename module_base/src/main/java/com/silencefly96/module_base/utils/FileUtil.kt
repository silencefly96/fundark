@file:Suppress("MemberVisibilityCanBePrivate")

package com.silencefly96.module_base.utils

import android.os.Environment
import java.io.*
import java.math.BigInteger
import java.security.MessageDigest
import java.util.*

/**
 * 文件工具类
 * @author Silence
 * @date 2019/5/20
 */
@Suppress("unused")
object FileUtil {

    /**
     * 生成文件夹
     * @param filePath 文件目录
     * @throws IOException 未捕获异常，请在协程中处理
     */
    fun makeDir(filePath: String) = File(filePath).apply {
        //mkdir () 只能在已经存在的目录中创建创建文件夹
        //mkdirs () 可以在不存在的目录中创建文件夹
        if (!exists()) mkdirs()
    }

    /**
     * 生成文件
     * @param filePath 文件路径
     * @param fileName 文件名称
     * @return File? 生成的文件
     * @throws IOException 未捕获异常，请在协程中处理
     */
    fun makeFile(filePath: String, fileName: String) = makeFile(filePath + fileName)

    /**
     * 生成文件
     * @param path 文件路径
     * @return File? 生成的文件
     * @throws IOException 未捕获异常，请在协程中处理
     */
    fun makeFile(path: String) = File(path).apply {
        //如果父目录非空且未创建，创建父目录
        parentFile?.let { dir->
            if (!dir.exists()) dir.mkdirs()
        }

        //如果文件不存在，创建文件
        if (!exists()) createNewFile()

        // File(path).writeText("use kotlin is so easy a!")
    }

    /**
     * 以追加形式，将字符串写入到文件中
     * 考虑使用kotlin写文件：File(path).appendText("use kotlin is so easy a!")
     * @param content 字符串内容
     * @param path 文件路径
     * @throws IOException 未捕获异常，请在协程中处理
     */
    fun appendFile(content: String, path: String) = makeFile(path).let { file ->
        //追加写入，使用 try-with-resource
        RandomAccessFile(file, "rwd").use { raf->
            raf.seek(file.length())
            raf.write(content.toByteArray())
        }
    }

    /**
     * 以追加形式，将字符串写入到文件中
     * @param content 字符串内容
     * @param filePath 文件路径
     * @param fileName 文件名称
     * @throws IOException 未捕获异常，请在协程中处理
     */
    fun appendFile(content: String, filePath: String, fileName: String) =
        appendFile(content, filePath + fileName)

    /**
     * 以不追加的形式，将字符串写入到文件中
     * 考虑使用kotlin写文件：File(path).writeText("use kotlin is so easy a!")
     * @param content 字符串内容
     * @param path 文件路径
     * @throws IOException 未捕获异常，请在协程中处理
     */
    fun writeFile(content: String, path: String) = makeFile(path).let { file ->
        //通过FileWriter写入
        FileWriter(file).use {
            it.write(content)
            it.flush()
        }

//            FileOutputStream(file).use {
//                it.write(content.toByteArray())
//                it.flush()
//            }
    }

    /**
     * 以不追加的形式，将字符串写入到文件中
     * 考虑使用kotlin写文件：File(path).writeText("use kotlin is so easy a!")
     * @param content 字符串内容
     * @param filePath 文件路径
     * @param fileName 文件名称
     * @throws IOException 未捕获异常，请在协程中处理
     */
    fun writeFile(content: String, filePath: String, fileName: String) =
        writeFile(content, filePath + fileName)

    /**
     * 以不追加的形式，将字符串写入到文件中
     * 考虑使用kotlin读文件：File(path).readText("use kotlin is so easy a!")
     * @param path 文件名称
     * @throws IOException 未捕获异常，请在协程中处理
     */
    fun read(path: String): String {
        return makeFile(path).let {
            //通过FileWriter写入
//            FileReader(it).use { fw->
//                val sb = StringBuilder()
//                val buf = CharArray(1024)
//                var len = 0
//                while (fw.read(buf).also { len = it } != 1) {
//                    sb.append(String(buf, 0, len))
//                }
//                sb.toString()
//            }

            //通过FileInputStream读取
            FileInputStream(it).use {  fs->
                val b = ByteArray(fs.available())
                fs.read(b)
                String(b)
            }
        }
    }

    /**
     * 清除文件内容
     * @param path 文件路径
     * @throws IOException 未捕获异常，请在协程中处理
     */
    fun clearFile(path: String) = writeFile("", path)

    /**
     * 删除文件
     * @param path 文件路径
     * @throws IOException 未捕获异常，请在协程中处理
     */
    fun delete(path: String) = makeFile(path).run {
        delete()
    }

    /**
     * 复制单个文件
     * 考虑使用kotlin，File(oldPath).copyTo(File(newPath), true)
     * @param oldPath 原文件路径 如：c:/fqf.txt
     * @param newPath 复制后路径 如：f:/fqf.txt
     * @param bufferSize 默认缓存大小
     */
    fun copyFile(oldPath: String, newPath: String, bufferSize: Int = 1024) {
        val buffer = ByteArray(bufferSize)
        var len: Int

        val newFile = makeFile(newPath)
        val oldFile = makeFile(oldPath)

        //双层带资源的流，先读再写
        FileOutputStream(newFile).use { fos->
            FileInputStream(oldFile).use { fis->
                while (fis.read(buffer).also { len = it } != -1) {
                    //读取后立马写入
                    fos.write(buffer, 0, len)
                }
                //全部读取完成，刷新文件
                fos.flush()
            }
        }
    }

    /**
     * 复制整个文件夹内容
     * 考虑使用kotlin，File(oldPath).copyRecursively(File(newPath), true)
     * @param oldPath String 原文件路径 如：c:/fqf
     * @param newPath String 复制后路径 如：f:/fqf/ff
     */
    fun copyFolder(oldPath: String, newPath: String) {
        val oldDir = makeDir(oldPath)
        oldDir.listFiles()?.let { fileList->
            for (file in fileList) {
                if (file.isFile) {
                    //复制文件
                    copyFile(file.absolutePath,newPath + File.separator + file.name)
                }else {
                    //递归复制文件夹
                    copyFolder(file.absolutePath,newPath + File.separator + file.name)
                }
            }
        }
    }

    /**
     * 检测SD卡是否存在
     */
    fun checkSdcard() = Environment.MEDIA_MOUNTED == Environment.getExternalStorageState()

    /**
     * 获取单个文件的MD5值
     * @param file 文件
     * @return MD5值
     */
    fun getFileMD5(file: File): String {
        if (!file.isFile) {
            throw IllegalArgumentException("this file is not a file!")
        }

        val digest = MessageDigest.getInstance("MD5")

        //读取文件并计算
        val buffer = ByteArray(1024)
        var len: Int
        FileInputStream(file).use { inputStream->
            while (inputStream.read(buffer).also { len = it } != -1) {
                digest.update(buffer, 0, len)
            }
        }

        val bigInt = BigInteger(1, digest.digest())
        return bigInt.toString(16)
    }

    /**
     * 获取文件夹中文件的MD5值
     * @param file 文件目录
     * @param isContainDir 是否递归子目录中的文件
     * @return 所有文件MD5值的Map
     */
    fun getDirMD5(file: File, isContainDir: Boolean = true): Map<String, String> {
        if (!file.isDirectory) {
            throw IllegalArgumentException("this file is not a directory!")
        }

        val map: MutableMap<String, String> = HashMap()
        file.listFiles()?.let { fileList->
            for (tmp in fileList) {
                if (tmp.isDirectory && isContainDir) {
                    //递归获取子目录文件MD5值
                    map.putAll(getDirMD5(tmp, isContainDir))
                } else {
                    map[tmp.path] = getFileMD5(tmp)
                }
            }
        }
        return map
    }

}