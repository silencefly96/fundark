package com.silencefly96.module_third.funnet.connection

import okhttp3.internal.Util
import java.io.IOException
import java.lang.RuntimeException

class RouteException(): RuntimeException() {
    var firstException: IOException? = null
    var lastException: IOException? = null

    constructor(cause: IOException?) : this() {
        firstException = cause
        lastException = cause
    }

    fun addConnectException(e: IOException?) {
        Util.addSuppressedIfPossible(firstException, e)
        lastException = e
    }
}