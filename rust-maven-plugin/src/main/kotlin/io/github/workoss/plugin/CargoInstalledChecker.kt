
package io.github.workoss.plugin

import org.apache.maven.plugin.MojoExecutionException
import java.io.File

class CargoInstalledChecker private constructor() {
    private val cache = HashMap<String, InstalledState>()

    @Synchronized
    @Throws(MojoExecutionException::class)
    fun check(cargoPath: String) {

        var cached = cache.getOrDefault(
            cargoPath, InstalledState.UNKNOWN
        )

        if (cached == InstalledState.UNKNOWN) {
            try {
                val pb = ProcessBuilder(
                    cargoPath, "--version"
                )
                val p = pb.start()
                val exitCode = p.waitFor()
                cached = if (exitCode == 0
                ) InstalledState.INSTALLED
                else InstalledState.BROKEN
                cache[cargoPath] = cached
            } catch (_: Exception) {
                cached = InstalledState.NOT_INSTALLED
                cache[cargoPath] = cached
            }
        }

        if (cached == InstalledState.INSTALLED) {
            return
        }

        val error = StringBuilder()

        if (cached == InstalledState.BROKEN) {
            if (cargoPath == "cargo") {
                error.append("Rust's `cargo` ")
            } else {
                error
                    .append("Rust's `cargo` at ")
                    .append(Shlex.quote(cargoPath))
            }
            error.append(
                " is a broken install: Running `cargo --version` " +
                        "returned non-zero exit code"
            )
        } else {  // cached == InstalledState.NOT_INSTALLED
            if (cargoPath == "cargo") {
                error
                    .append("Rust's `cargo` not found in PATH=")
                    .append(Shlex.quote(java.lang.String.join(File.pathSeparator, System.getenv("PATH"))))
            } else {
                error
                    .append("Rust's `cargo` not found at ")
                    .append(Shlex.quote(cargoPath))
            }
        }
        error.append(".\n\nSee https://www.rust-lang.org/tools/install")
        throw MojoExecutionException(error.toString())
    }

    private enum class InstalledState {
        UNKNOWN,
        NOT_INSTALLED,
        INSTALLED,
        BROKEN
    }

    companion object {
        @JvmField
        val INSTANCE: CargoInstalledChecker = CargoInstalledChecker()
    }
}
