package io.github.workoss.plugin

import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.nio.file.Path
import java.nio.file.Paths

abstract class AbstractCargoMojoBase : AbstractMojo() {
    @Parameter(property = "project", readonly = true)
    protected var project: MavenProject? = null

    @Parameter(property = "environmentVariables")
    private val environmentVariables: Map<String, String> = emptyMap()

    /**
     * Path to the `cargo` command. If unset or set to "cargo", uses $PATH.
     */
    @Parameter(property = "cargoPath", defaultValue = "cargo")
    private val cargoPath: String = ""

    /**
     * Path to the Rust crate to build.
     */
    @Parameter(property = "path", required = true)
    private val path: String = ""

    /**
     * Build artifacts in release mode, with optimizations.
     * Defaults to "false" and creates a debug build.
     * Equivalent to Cargo's `--release` option.
     */
    @Parameter(property = "release", defaultValue = "false")
    private val release = false

    /**
     * List of features to activate.
     * If not specified, default features are activated.
     * Equivalent to Cargo's `--features` option.
     */
    @Parameter(property = "features")
    private val features: Array<String> = emptyArray()

    /**
     * Activate all available features.
     * Defaults to "false".
     * Equivalent to Cargo's `--all-features` option.
     */
    @Parameter(property = "all-features", defaultValue = "false")
    private val allFeatures = false

    /**
     * Do not activate the `default` feature.
     * Defaults to "false".
     * Equivalent to Cargo's `--no-default-features` option.
     */
    @Parameter(property = "no-default-features", defaultValue = "false")
    private val noDefaultFeatures = false

    /**
     * Set the verbosity level, forwarded to Cargo.
     * Valid values are "", "-q", "-v", "-vv".
     */
    @get:Throws(MojoExecutionException::class)
    @Parameter(property = "verbosity")
    protected val verbosity: String? = null
        get() {
            if (field == null) {
                return null
            }
            return when (field) {
                "" -> null
                "-q", "-v", "-vv" -> field
                else -> throw MojoExecutionException("Invalid verbosity: $field")
            }
        }

    /**
     * Additional args to pass to cargo.
     */
    @Parameter(property = "extra-args")
    private val extraArgs: Array<String> = emptyArray()

    protected val crateRoot: Path
        get() {
            var crateRoot = Paths.get(path)
            if (!crateRoot.isAbsolute) {
                crateRoot = project!!.basedir.toPath().resolve(path)
            }
            return crateRoot
        }

    protected val targetRootDir: Path
        get() = Paths.get(
            project!!.build.directory,
            "rust-maven-plugin"
        )

    @get:Throws(MojoExecutionException::class)
    protected val commonCrateParams: Crate.Params
        get() {
            val params = Crate.Params()
            params.verbosity = verbosity
            params.environmentVariables = environmentVariables
            params.cargoPath = cargoPath
            params.release = release
            params.features = features
            params.allFeatures = allFeatures
            params.noDefaultFeatures = noDefaultFeatures
            params.extraArgs = extraArgs
            return params
        }
}