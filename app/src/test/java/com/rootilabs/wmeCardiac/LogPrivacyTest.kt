package com.rootilabs.wmeCardiac

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Release builds keep android.util.Log calls, and logcat is readable through adb or a bug report.
 * Scans every source line so a new call that interpolates patient data fails here.
 */
class LogPrivacyTest {

    private val sensitiveLogCall = Regex(
        """\bLog\.\w+\(.*\$\{?[\w.?]*(patientId|institutionId|others|symptom)""",
        RegexOption.IGNORE_CASE
    )

    @Test
    fun `no log call interpolates patient identifiers or symptom text`() {
        // Gradle runs unit tests from the module directory.
        val sources = File("src/main/java").walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue("No sources found under ${File("src/main/java").absolutePath}", sources.isNotEmpty())

        val offending = sources.flatMap { file ->
            file.readLines().withIndex()
                .filter { (_, line) -> sensitiveLogCall.containsMatchIn(line) }
                .map { (index, line) -> "${file.path}:${index + 1}: ${line.trim()}" }
        }

        assertEquals(emptyList<String>(), offending)
    }
}
