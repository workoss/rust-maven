
package io.github.workoss.plugin

import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import java.nio.file.Paths

/**
 * An example of a Maven plugin.
 */
@Mojo(name = "build", defaultPhase = LifecyclePhase.COMPILE, threadSafe = true)
class CargoBuildMojo : AbstractCargoMojoBase() {
    /**
     * Location to copy the built Rust binaries to.
     * If unset, the binaries are not copied and remain in the target directory.
     *
     *
     * See also `copyWithPlatformDir`.
     */
    @Parameter(property = "copyTo")
    private val copyTo: String? = null

    /**
     * Further nest copy into a child directory named through the target's platform.
     * The computed name matches that of the `io.github.workoss.jni.OS $os-$arch` method.
     *
     *
     * See also `copyTo`.
     */
    @Parameter(property = "copyWithPlatformDir")
    private val copyWithPlatformDir = false

    @Throws(MojoExecutionException::class, MojoFailureException::class)
    override fun execute() {
        val crate = Crate(
            crateRoot,
            targetRootDir,
            extractCrateParams()
        )
        crate.setLog(log)
        crate.build()
        crate.copyArtifacts()
    }

    @Throws(MojoExecutionException::class)
    private fun extractCrateParams(): Crate.Params {
        val params = commonCrateParams
        if (copyTo != null) {
            var copyToDir = Paths.get(copyTo)
            if (!copyToDir.isAbsolute) {
                copyToDir = project!!.basedir.toPath().resolve(copyToDir)
            }
            params.copyToDir = copyToDir
        }
        params.copyWithPlatformDir = copyWithPlatformDir
        return params
    }
}
