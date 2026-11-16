package ephyra.buildlogic

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.process.ExecOperations
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject

interface CommandValueSourceParameters : ValueSourceParameters {
    val command: org.gradle.api.provider.ListProperty<String>
}

abstract class CommandValueSource : ValueSource<String, CommandValueSourceParameters> {
    @get:Inject
    abstract val execOperations: ExecOperations

    override fun obtain(): String {
        val output = java.io.ByteArrayOutputStream()
        execOperations.exec {
            commandLine = parameters.command.get()
            standardOutput = output
        }
        return output.toString().trim()
    }
}

// Git is needed in your system PATH for these commands to work.
// If it's not installed, you can return a random value as a workaround

/**
 * Commit count as a lazy [Provider]: the git command runs when the provider is first consumed
 * -- at execution time for task inputs such as `GenerateBuildConfig`'s `buildConfigFields` --
 * never during configuration. The previous eager helpers called `.get()` on the value source
 * inside the build script, executing git on every configuration and making the configuration
 * cache re-validate the source on every commit.
 */
fun Project.commitCountProvider(): Provider<String> = commandProvider("git rev-list --count HEAD")

fun Project.gitShaProvider(): Provider<String> = commandProvider("git rev-parse --short HEAD")

private val BUILD_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")

/**
 * Build time as a lazy [Provider].
 *
 * @param useLastCommitTime If `true`, the build time is based on the timestamp of the last Git commit;
 *                          otherwise, the current time is used. Both are in UTC and are resolved
 *                          when the consuming task executes, keeping the configuration phase
 *                          deterministic (a configuration-cache requirement).
 */
fun Project.buildTimeProvider(useLastCommitTime: Boolean): Provider<String> {
    return if (useLastCommitTime) {
        commandProvider("git log -1 --format=%ct").map { epoch ->
            Instant.ofEpochSecond(epoch.toLong()).atOffset(ZoneOffset.UTC).format(BUILD_TIME_FORMATTER)
        }
    } else {
        // Wall-clock build time: the clock is read at execution time by the consuming task.
        provider { Instant.now().atOffset(ZoneOffset.UTC).format(BUILD_TIME_FORMATTER) }
    }
}

private fun Project.commandProvider(command: String): Provider<String> {
    return providers.of(CommandValueSource::class.java) {
        parameters.command.set(command.split(" "))
    }
}
