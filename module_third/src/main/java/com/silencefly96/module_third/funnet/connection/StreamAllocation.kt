package com.silencefly96.module_third.funnet.connection

import java.io.IOException

class StreamAllocation {

    fun release() {}

    fun streamFailed(e: IOException?) {}

    fun route(): Route {
        return Route()
    }

    fun codec(): HttpCodec? {
        return HttpCodec()
    }
}

class Route() {}