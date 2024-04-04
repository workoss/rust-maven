/*
 * Copyright 2024-2024 workoss (https://www.workoss.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.workoss.plugin

import java.nio.file.Paths
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter

/** An example of a Maven plugin. */
@Suppress("unused")
@Mojo(name = "build", defaultPhase = LifecyclePhase.COMPILE, threadSafe = true)
class CargoBuildMojo : AbstractCargoMojoBase() {
  /**
   * Location to copy the built Rust binaries to. If unset, the binaries are not copied and remain
   * in the target directory.
   *
   * See also `copyWithPlatformDir`.
   */
  @Parameter(property = "copyTo") private val copyTo: String? = null

  /**
   * Further nest copy into a child directory named through the target's platform. The computed name
   * matches that of the `io.github.workoss.jni.OS $os-$arch` method.
   *
   * See also `copyTo`.
   */
  @Parameter(property = "copyWithPlatformDir") private val copyWithPlatformDir = false

  @Throws(MojoExecutionException::class, MojoFailureException::class)
  override fun execute() {
    val crate = Crate(crateRoot, targetRootDir, extractCrateParams())
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
