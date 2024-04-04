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

import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter

@Suppress("unused")
@Mojo(name = "test", defaultPhase = LifecyclePhase.TEST, threadSafe = true)
class CargoTestMojo : AbstractCargoMojoBase() {
  /** Skips running tests when building with `mvn package -DskipTests=true`. */
  @Parameter(property = "skipTests", defaultValue = "false") private val skipTests = false

  @Throws(MojoExecutionException::class, MojoFailureException::class)
  override fun execute() {
    if (skipTests) {
      log.info("Skipping tests")
      return
    }
    val crate = Crate(crateRoot, targetRootDir, commonCrateParams)
    crate.setLog(log)
    crate.test()
  }
}
