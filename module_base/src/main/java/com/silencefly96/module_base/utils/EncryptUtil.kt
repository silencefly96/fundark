package com.silencefly96.module_base.utils

import java.security.MessageDigest
import java.security.spec.AlgorithmParameterSpec
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec


/**
 * 加密工具类，支持Base64、MD5、SHA、AES、CAESAR
 *
 * @author fdk
 * @date 2021/07/13
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
object EncryptUtil {

    const val TYPE_BASE_64 = 1

    const val TYPE_MD5 = 2

    const val TYPE_SHA = 3

    const val TYPE_DES = 4

    const val TYPE_3DES = 5

    const val TYPE_AES = 6

    const val TYPE_RSA = 7

    const val TYPE_CAESAR = 8

    fun encode(str: String, type: Int): String = when(type) {
        TYPE_BASE_64 -> base64Encode(str)
        TYPE_MD5 -> makeMD5(str)
        TYPE_SHA -> makeSha(str)
        TYPE_DES -> throw IllegalArgumentException("TYPE_DES not support!")

        TYPE_3DES -> throw IllegalArgumentException("TYPE_3DES not support!")
        TYPE_AES -> AesUtil.encrypt(str.toByteArray(), "password").toString()
        TYPE_RSA -> throw IllegalArgumentException("TYPE_RSA not support!")
        TYPE_CAESAR -> caesarEncrypt(str)
        else -> ""
    }

    fun decode(str: String, type: Int): String = when(type) {
        TYPE_BASE_64 -> base64Decode(str)
        TYPE_MD5 -> throw IllegalArgumentException("TYPE_MD5 not support!")
        TYPE_SHA -> throw IllegalArgumentException("TYPE_SHA not support!")
        TYPE_DES -> throw IllegalArgumentException("TYPE_DES not support!")

        TYPE_3DES -> throw IllegalArgumentException("TYPE_3DES not support!")
        TYPE_AES -> AesUtil.decrypt(str.toByteArray(), "password").toString()
        TYPE_RSA -> throw IllegalArgumentException("TYPE_RSA not support!")
        TYPE_CAESAR -> caesarDecrypt(str)
        else -> ""
    }

    /**
     * Base64 编码，Java/安卓都提供了对应方法
     */
    fun base64Encode(code: String): String {
        return android.util.Base64.encodeToString(code.toByteArray(), android.util.Base64.DEFAULT)
    }

    /**
     * Base64 解码
     */
    fun base64Decode(code: String): String {
        return String(android.util.Base64.decode(code.toByteArray(), android.util.Base64.DEFAULT))
    }

    /**
     * 获取字符串的 md5值，字节数组形式
     * @param  str 需要获取md5的字符串
     * @return 字节数组形式 md5值
     */
    fun makeMD5Byte(str: String): ByteArray = MessageDigest.getInstance("MD5").run {
        update(str.toByteArray(charset("utf-8")))
        digest()
    }

    /**
     * 获取字符串的 md5值，字符串组形式
     * @param  str 需要获取md5的字符串
     * @return 字符串形式 md5值
     */
    fun makeMD5(str: String) = StringBuffer().run {
            makeMD5Byte(str).forEach {
                append(String.format("%02x", it))
            }
            toString()
        }

    /**
     * 获取字符串的 Sha值，字节数组形式,支持 SHA-1,SHA-224,SHA-256,SHA-384,SHA-512
     * @param  str 需要获取Sha的字符串
     * @return 字节数组形式 Sha值
     */
    fun makeShaByte(str: String): ByteArray = MessageDigest.getInstance("SHA-1").run {
        update(str.toByteArray(charset("utf-8")))
        digest()
    }

    /**
     * 获取字符串的 Sha值，字符串组形式
     * @param  str 需要获取Sha的字符串
     * @return 字符串形式 Sha值
     */
    fun makeSha(str: String) = StringBuffer().run {
        makeShaByte(str).forEach {
            append(String.format("%02x", it))
        }
        toString()
    }

    /**
     * Aes 加密工具类，TODO 好好优化
     */
    object AesUtil {
        private const val ALGORITHM = "AES"
        private val IV_SPEC: ByteArray

        init {
            // 这里使用算法来推算密钥，会比裸串好一点
            val rawPwd = "password"
            IV_SPEC = makeMD5Byte(rawPwd)
        }

        /**
         * 加密
         */
        @Throws(Exception::class)
        fun encrypt(data: ByteArray?, password: String): ByteArray {
            return getCipher(Cipher.ENCRYPT_MODE, password).doFinal(data)
        }

        /**
         * 解密
         */
        @Throws(Exception::class)
        fun decrypt(data: ByteArray?, password: String): ByteArray {
            return getCipher(Cipher.DECRYPT_MODE, password).doFinal(data)
        }

        @Throws(Exception::class)
        private fun getCipher(mode: Int, password: String): Cipher {
            val paramSpec: AlgorithmParameterSpec = IvParameterSpec(
                IV_SPEC
            )
            val encKey: ByteArray = makeMD5Byte(password)
            val key = SecretKeySpec(encKey, ALGORITHM)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(mode, key, paramSpec)
            return cipher
        }

    }

    /**
     * TODO 好好优化
     * Caesar加密，将小写字母转变成迁移一定位置的大写字母；将大写字母变成迁移一定位置的小写字母（迁移位置是Caesar密钥）
     */
    fun caesarEncrypt(code: String): String {
        val length = 3
        val sb = StringBuilder(code)
        for (i in sb.indices) {
            for (j in 0..25) {
                if (sb[i] == a[j]) {
                    sb.replace(
                        i,
                        i + 1,
                        b[(j + length) % 26].toString()
                    )
                    break
                } else if (sb[i] == b[j]) {
                    sb.replace(
                        i,
                        i + 1,
                        a[(j + length) % 26].toString()
                    )
                    break
                }
            }
        }
        return sb.toString()
    }

    /**
     * TODO 好好优化
     * Caesar解密，将小写字母转变成迁移一定位置的大写字母；将大写字母变成迁移一定位置的小写字母（迁移位置是Caesar密钥）
     */
    fun caesarDecrypt(code: String): String {
        val length = 3
        val sb = StringBuilder(code)
        for (i in sb.indices) {
            for (j in 0..25) {
                if (sb[i] == a[j]) {
                    sb.replace(
                        i,
                        i + 1,
                        b[(j + 26 - length) % 26].toString()
                    )
                    break
                } else if (sb[i] == b[j]) {
                    sb.replace(
                        i,
                        i + 1,
                        a[(j + 26 - length) % 26].toString()
                    )
                    break
                }
            }
        }
        return sb.toString()
    }

    private val a = charArrayOf(
        'a',
        'b',
        'c',
        'd',
        'e',
        'f',
        'g',
        'h',
        'i',
        'j',
        'k',
        'l',
        'm',
        'n',
        'o',
        'p',
        'q',
        'r',
        's',
        't',
        'u',
        'v',
        'w',
        'x',
        'y',
        'z'
    )

    private val b = charArrayOf(
        'A',
        'B',
        'C',
        'D',
        'E',
        'F',
        'G',
        'H',
        'I',
        'J',
        'K',
        'L',
        'M',
        'N',
        'O',
        'P',
        'Q',
        'R',
        'S',
        'T',
        'U',
        'V',
        'W',
        'X',
        'Y',
        'Z'
    )

}