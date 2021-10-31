package io.github.rrva.flightmemory

import jdk.jfr.consumer.RecordingFile
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.debug.DebugProbes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.junit.Assert
import org.junit.Test
import java.io.File
import java.nio.charset.Charset
import java.time.Duration
import java.util.zip.ZipInputStream

internal class FlightMemoryTest {

    private suspend fun sleepForever() {
        delay(Int.MAX_VALUE.toLong())
    }

    @Test
    fun `runs and produces some valid output`() {
        if (!DebugProbes.isInstalled) {
            DebugProbes.install()
        }

        GlobalScope.launch {
            sleepForever()
        }

        val inputStream = FlightMemory.recordingAsZip(Duration.ofSeconds(1), Duration.ofSeconds(1), "test", true)
        val fileContents = ZipInputStream(inputStream.get())
            .use { zipInputStream ->
                generateSequence { zipInputStream.nextEntry }
                    .map {
                        UnzippedFile(
                            filename = it.name,
                            content = zipInputStream.readAllBytes()
                        )
                    }.toList()
            }

        val files = fileContents.associateBy {
            val s = it.filename.removePrefix("test/test-")
            s.substring(0, s.indexOf("-"))
        }

        Assert.assertEquals(
            listOf("profile", "default", "stacks", "coroutines"),
            files.keys.toList()
        )

        val coroutinesDump = files["coroutines"]?.content?.toString(Charset.defaultCharset())
        Assert.assertTrue(coroutinesDump?.contains(this::sleepForever.name) == true)

        verifyFlightRecording(files["default"]!!)
    }

    private fun verifyFlightRecording(unzippedFile: UnzippedFile) {
        val input = File.createTempFile("recording", ".jfr")
        input.deleteOnExit()
        input.writeBytes(unzippedFile.content)
        val recording = RecordingFile(input.toPath())
        Assert.assertTrue(recording.hasMoreEvents())
    }
}

class UnzippedFile(val filename: String, val content: ByteArray) {
    override fun toString(): String {
        return "UnzippedFile(filename=$filename)"
    }
}
