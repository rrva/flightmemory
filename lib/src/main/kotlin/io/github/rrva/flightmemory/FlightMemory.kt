package io.github.rrva.flightmemory

import jdk.jfr.Configuration
import jdk.jfr.Recording
import jdk.jfr.consumer.RecordedEvent
import jdk.jfr.consumer.RecordedFrame
import jdk.jfr.consumer.RecordingFile
import kotlinx.coroutines.debug.DebugProbes
import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.concurrent.thread
import kotlin.io.path.deleteIfExists

object FlightMemory {

    @JvmStatic
    fun startRecording(configName: String = "profile"): Recording {
        val configuration: Configuration = Configuration.getConfiguration(configName)
        val recording = Recording(configuration)
        recording.start()
        return recording
    }

    @JvmStatic
    @JvmOverloads
    fun recordingAsZip(
        profileDuration: Duration? = Duration.ofSeconds(10),
        defaultRecordingDuration: Duration? = null,
        filenamePrefix: String = "flightmemory",
        dumpCoroutines: Boolean = false,
    ): CompletableFuture<InputStream> {

        val future = CompletableFuture<InputStream>()
        thread {
            val profileRecordingFile = Files.createTempFile("$filenamePrefix-profile-", ".jfr")
            val defaultRecordingFile = Files.createTempFile("$filenamePrefix-default-", ".jfr")

            try {
                val coroutinesDump = if(dumpCoroutines) coroutinesDump() else null
                record("profile", profileDuration, profileRecordingFile)
                record("default", defaultRecordingDuration, defaultRecordingFile)
                val stacks = stacksFromFlightRecording(profileRecordingFile)
                val zipFile =
                    createZip(filenamePrefix, profileRecordingFile, defaultRecordingFile, stacks, coroutinesDump)

                future.complete(zipFile.pipeToInputStream())
            } finally {
                profileRecordingFile.deleteIfExists()
                defaultRecordingFile.deleteIfExists()
            }
        }
        return future
    }

    private fun record(configName: String, duration: Duration?, recordingFile: Path?) {
        if (duration != null && !duration.isZero) {
            val profileRecording = startRecording(configName)
            Thread.sleep(duration.toMillis())
            profileRecording.dump(recordingFile)
        }
    }

    private fun createZip(
        filenamePrefix: String,
        profileRecordingFile: Path,
        defaultRecordingFile: Path,
        stacks: ByteArrayOutputStream,
        coroutinesDump: ByteArrayOutputStream?
    ): ByteArrayOutputStream {
        val zipFile = ByteArrayOutputStream()
        val zipOutput = ZipOutputStream(BufferedOutputStream(zipFile))

        zipOutput.use {
            it.putNextEntry(ZipEntry("$filenamePrefix/$filenamePrefix-profile-recording.jfr"))
            Files.copy(profileRecordingFile, zipOutput)
            it.closeEntry()
            it.putNextEntry(ZipEntry("$filenamePrefix/$filenamePrefix-default-recording.jfr"))
            Files.copy(defaultRecordingFile, zipOutput)
            it.closeEntry()
            it.putNextEntry(ZipEntry("$filenamePrefix/$filenamePrefix-stacks-folded.txt"))
            stacks.writeTo(zipOutput)
            it.closeEntry()
            if(coroutinesDump != null) {
                it.putNextEntry(ZipEntry("$filenamePrefix/$filenamePrefix-coroutines-dump-kotlin.txt"))
                coroutinesDump.writeTo(zipOutput)
                it.closeEntry()
            }
        }
        return zipFile
    }

    @JvmStatic
    fun stacksFromFlightRecording(recordingFile: Path): ByteArrayOutputStream {
        val traceCounts = stackTraces(recordingFile)
        return writeStacks(traceCounts)
    }

    private fun coroutinesDump(): ByteArrayOutputStream {
        val bas = ByteArrayOutputStream()
        val printStream = PrintStream(bas)

        if (DebugProbes.isInstalled) {
            DebugProbes.dumpCoroutines(printStream)
        } else {
            printStream.println("DebugProbes not installed. Install with DebugProbes.install() at start of application")
        }
        return bas
    }

    private fun ByteArrayOutputStream.pipeToInputStream(): PipedInputStream {
        val inputStream = PipedInputStream()
        val outputStream = PipedOutputStream(inputStream)
        thread(name = "flightRecording-copier") {
            outputStream.use { outputStream ->
                this.writeTo(outputStream)
            }
        }
        return inputStream
    }

    private fun writeStacks(traceCounts: Map<String, Int>): ByteArrayOutputStream {
        val out = ByteArrayOutputStream()
        OutputStreamWriter(out).use { writer ->
            traceCounts.forEach { (trace: String, count: Int) ->
                try {
                    writer.write(String.format("%s %d%n", trace, count))
                } catch (e: IOException) {
                    throw RuntimeException(e)
                }
            }
        }
        return out
    }

    private fun stackTraces(input: Path): Map<String, Int> {
        RecordingFile(input).use { recording ->
            return recording.asSequence()
                .filter { it.eventType.name.equals("jdk.ExecutionSample", ignoreCase = true) }
                .map { it.trace() }
                .groupingBy { it }.eachCount()
        }
    }

    private fun RecordedEvent.trace(): String {
        if (stackTrace == null) {
            return ""
        }
        return stackTrace.frames
            .filter { it.isJavaFrame }
            .map { it.frameName() }
            .reversed().joinToString(";")
    }

    private fun RecordedFrame.frameName(): String = "${method.type.name}::${method.name}"
}

internal fun RecordingFile.asSequence(): Sequence<RecordedEvent> = generateSequence {
    when {
        this.hasMoreEvents() -> this.readEvent()
        else -> null
    }
}
