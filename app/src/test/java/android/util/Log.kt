package android.util

/**
 * Shadows the stub that throws "not mocked" on the local JVM. The runtime classpath puts test
 * classes ahead of the Android stub. Unlike testOptions.unitTests.isReturnDefaultValues, this leaves
 * every other unmocked Android call failing loudly.
 */
object Log {
    const val VERBOSE = 2
    const val DEBUG = 3
    const val INFO = 4
    const val WARN = 5
    const val ERROR = 6
    const val ASSERT = 7

    @JvmStatic fun v(tag: String?, msg: String?): Int = 0
    @JvmStatic fun v(tag: String?, msg: String?, tr: Throwable?): Int = 0
    @JvmStatic fun d(tag: String?, msg: String?): Int = 0
    @JvmStatic fun d(tag: String?, msg: String?, tr: Throwable?): Int = 0
    @JvmStatic fun i(tag: String?, msg: String?): Int = 0
    @JvmStatic fun i(tag: String?, msg: String?, tr: Throwable?): Int = 0
    @JvmStatic fun w(tag: String?, msg: String?): Int = 0
    @JvmStatic fun w(tag: String?, msg: String?, tr: Throwable?): Int = 0
    @JvmStatic fun w(tag: String?, tr: Throwable?): Int = 0
    @JvmStatic fun e(tag: String?, msg: String?): Int = 0
    @JvmStatic fun e(tag: String?, msg: String?, tr: Throwable?): Int = 0
    @JvmStatic fun wtf(tag: String?, msg: String?): Int = 0
    @JvmStatic fun wtf(tag: String?, tr: Throwable?): Int = 0
    @JvmStatic fun wtf(tag: String?, msg: String?, tr: Throwable?): Int = 0
    @JvmStatic fun println(priority: Int, tag: String?, msg: String?): Int = 0
    @JvmStatic fun isLoggable(tag: String?, level: Int): Boolean = false
    @JvmStatic fun getStackTraceString(tr: Throwable?): String = tr?.stackTraceToString().orEmpty()
}
