package com.microauto.script

class ScriptExitException : RuntimeException() {
    override fun fillInStackTrace(): Throwable = this
}

class ScriptTimeoutException : RuntimeException("脚本执行超过 10 分钟") {
    override fun fillInStackTrace(): Throwable = this
}
