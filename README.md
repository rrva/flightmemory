# FlightMemory

## A java flight recording helper

Helper class for production profiling using java flight recorder (JFR)

```


```

Returns an InputStream to a zipfile which contains:

- _profile-XXX.jfr_
    - A java flight recorder file, open
      in [jdk mission control (JMC)](https://www.oracle.com/java/technologies/javase/products-jmc8-downloads.html)
- _stacks-XXX.folded_
    - A folded stacktrace file, can be visualized with Flame graph tools
      like [Brendan Greggs flame graph tools](https://github.com/brendangregg/FlameGraph)
- _coroutinesDump.txt
    - A kotlin coroutines dump. Debug probes must be installed beforehand (at start), with `DebugProbes.install()`
      available in . If debug probes are not installed the file is empty.

Requires the app to run in a JVM with flight recorder classes available (jdk.jfr package), for example java8u262 or
higher (jdk not jre) or java11

Example usage in a spring boot application (Kotlin, see below for Java):

```kotlin 
import org.springframework.core.io.InputStreamResource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import io.github.rrva.flightmemory.FlightMemory
import java.time.LocalDateTime

@RestController
class FlightRecordingController {

@GetMapping("/profile.jfr.zip")
    fun flightRecorder(): ResponseEntity<InputStreamResource> {
        val now = LocalDateTime.now()
        val inputStreamResource = InputStreamResource(FlightMemory.profileWithFlightRecorder(10))
        val httpHeaders = HttpHeaders()
        httpHeaders.contentType = MediaType.APPLICATION_OCTET_STREAM;

        httpHeaders.set("Content-Disposition", "attachment; filename=index-profile-${now}.zip");

        return ResponseEntity(inputStreamResource, httpHeaders, HttpStatus.OK)
    }
}
```

Java usage:

```java
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import io.github.rrva.flightmemory.FlightMemory;
import java.time.LocalDateTime;


@RestController
public class FlightRecordingController {

    @GetMapping("/profile.jfr.zip")
    public ResponseEntity<InputStreamResource> flightRecorder() {
        LocalDateTime now = LocalDateTime.now();
        InputStreamResource inputStreamResource = new InputStreamResource(FlightMemory.profileWithFlightRecorder(10));
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);

        httpHeaders.set("Content-Disposition", "attachment; filename=index-profile-"+now+".zip");

        return new ResponseEntity<InputStreamResource>(inputStreamResource, httpHeaders, HttpStatus.OK);
    }
}
```
