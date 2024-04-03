
package io.github.workoss.plugin

import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter

@Suppress("unused")
@Mojo(name = "test", defaultPhase = LifecyclePhase.TEST, threadSafe = true)
class CargoTestMojo : AbstractCargoMojoBase() {
    /**
     * Skips running tests when building with `mvn package -DskipTests=true`.
     */
    @Parameter(property = "skipTests", defaultValue = "false")
    private val skipTests = false

    @Throws(MojoExecutionException::class, MojoFailureException::class)
    override fun execute() {
        if (skipTests) {
            log.info("Skipping tests")
            return
        }
        val crate = Crate(
            crateRoot,
            targetRootDir,
            commonCrateParams
        )
        crate.setLog(log)
        crate.test()
    }
}
