import io.github.rrva.flightmemory.FlightMemory
import kotlinx.coroutines.*
import kotlinx.coroutines.debug.DebugProbes
import java.io.File
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

@DelicateCoroutinesApi
fun main() {

    DebugProbes.install()
    GlobalScope.launch {
        sleeper()
    }
    thread(name = "sleeper") {
        while (true) {
            TimeUnit.SECONDS.sleep(1)
        }
    }
    println("Starting 10 second flight recording")
    val zipFile = FlightMemory.recordingAsZip("test", Duration.ofSeconds(10))
    val tempFile = File.createTempFile("test", ".zip")
    tempFile.writeBytes(zipFile.get().readAllBytes())
    println("Wrote to $tempFile")
}

private suspend fun sleeper() {
    while (true) {
        delay(1000)
    }
}
