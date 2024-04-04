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

import io.github.workoss.jni.JniLibLoader
import io.github.workoss.jni.OS
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.nio.file.*
import java.util.*
import java.util.concurrent.Executors
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugin.logging.Log
import org.tomlj.Toml
import org.tomlj.TomlArray
import org.tomlj.TomlInvalidTypeException
import org.tomlj.TomlTable

/** Controls running tasks on a Rust crate. */
@Suppress("NAME_SHADOWING")
class Crate(crateRoot: Path, targetRootDir: Path, params: Params) {
  private val crateRoot: Path
  private val targetDir: Path
  private val params: Params
  private var cargoToml: TomlTable
  private var packageName: String? = null
  private var log: Log

  init {
    this.log = nullLog()
    this.crateRoot = crateRoot
    this.targetDir = targetRootDir.resolve(dirName)
    this.params = params

    val tomlPath: Path = crateRoot.resolve("Cargo.toml")
    if (!Files.exists(tomlPath, LinkOption.NOFOLLOW_LINKS)) {
      throw MojoExecutionException("Cargo.toml file expected under: $crateRoot")
    }
    try {
      this.cargoToml = Toml.parse(tomlPath)
    } catch (e: IOException) {
      throw MojoExecutionException("Failed to parse Cargo.toml file: " + e.message)
    }

    try {
      packageName = cargoToml.getString("package.name")
      if (packageName == null) {
        throw MojoExecutionException("Missing required `package.name` from Cargo.toml file")
      }
    } catch (e: TomlInvalidTypeException) {
      throw MojoExecutionException(
          "Failed to extract `package.name` from Cargo.toml file: " + e.message)
    }
  }

  fun setLog(log: Log) {
    this.log = log
  }

  private val dirName: String
    get() = crateRoot.fileName.toString()

  private val profile: String
    get() = if (params.release) "release" else "debug"

  private fun hasCdylib(): Boolean {
    try {
      val crateTypes: TomlArray = crateTypes ?: return false

      for (index in 0 until crateTypes.size()) {
        val crateType: String? = crateTypes.getString(index)
        if (crateType != null && crateType == "cdylib") {
          return true
        }
      }

      return false
    } catch (_: TomlInvalidTypeException) {
      return false
    }
  }

  private val crateTypes: TomlArray?
    get() {
      val crateTypes: TomlArray? = cargoToml.getArray("lib.crate-type")
      if (crateTypes == null) {
        val crateTypeLegacyKey = "lib.crate_type"
        return cargoToml.getArray(crateTypeLegacyKey)
      }
      return crateTypes
    }

  @get:Throws(MojoExecutionException::class)
  private val cdylibName: String?
    get() {
      var name: String?
      try {
        name = cargoToml.getString("lib.name")
      } catch (e: TomlInvalidTypeException) {
        throw MojoExecutionException(
            "Failed to extract `lib.name` from Cargo.toml file: " + e.message)
      }

      // The name might be missing, but the lib section might be present.
      if ((name == null) && hasCdylib()) {
        name = packageName
      }

      return name
    }

  @get:Throws(MojoExecutionException::class)
  private val binNames: List<String?>
    get() {
      val binNames: MutableList<String?> = ArrayList()

      var defaultBin: String? = null
      if (Files.exists(crateRoot.resolve("src").resolve("main.rs"))) {
        // Expecting default bin, given that there's no lib.
        defaultBin = packageName
        binNames.add(defaultBin)
      }

      val bins: TomlArray?
      try {
        bins = cargoToml.getArray("bin")
      } catch (e: TomlInvalidTypeException) {
        throw MojoExecutionException("Failed to extract `bin`s from Cargo.toml file: " + e.message)
      }

      if (bins == null) {
        return binNames
      }

      for (index in 0 until bins.size()) {
        val bin: TomlTable =
            bins.getTable(index)
                ?: throw MojoExecutionException(
                    "Failed to extract `bin`s from Cargo.toml file: " +
                        "expected a `bin` table at index " +
                        index)

        var name: String?
        try {
          name = bin.getString("name")
        } catch (_: TomlInvalidTypeException) {
          throw MojoExecutionException(
              "Failed to extract `bin`s from Cargo.toml file: " +
                  "expected a string at index " +
                  index +
                  " `name` key")
        }

        if (name == null) {
          throw MojoExecutionException(
              "Failed to extract `bin`s from Cargo.toml file: " +
                  "missing `name` key at `bin` with index " +
                  index)
        }

        var path: String?
        try {
          path = bin.getString("path")
        } catch (_: TomlInvalidTypeException) {
          throw MojoExecutionException(
              "Failed to extract `bin`s from Cargo.toml file: " +
                  "expected a string at index " +
                  index +
                  " `path` key")
        }

        // Handle special case where the default bin is renamed.
        if ((path != null) && (path == "src/main.rs")) {
          defaultBin = name
          binNames.removeAt(0)
          binNames.add(0, defaultBin)
        }

        // This `[[bin]]` entry just configures the default bin.
        // It's already been added.
        if (name != defaultBin) {
          binNames.add(name)
        }
      }

      return binNames
    }

  @get:Throws(MojoExecutionException::class)
  val artifactPaths: List<Path>
    get() {
      val paths: MutableList<Path> = ArrayList<Path>()
      val profile = profile

      val libName = cdylibName
      if (libName != null) {
        val libPath: Path = targetDir.resolve(profile).resolve(pinLibName(libName))
        paths.add(libPath)
      }

      for (binName in binNames) {
        val binPath: Path = targetDir.resolve(profile).resolve(pinBinName(binName))
        paths.add(binPath)
      }

      return paths
    }

  private val cargoPath: String
    get() {
      var path = params.cargoPath
      // Expand "~" to user's home directory.
      // This works around a limitation of ProcessBuilder.
      if (!OS.isWindows && path.startsWith("~/")) {
        path = System.getProperty("user.home") + path.substring(1)
      }
      return path
    }

  @Throws(IOException::class, InterruptedException::class, MojoExecutionException::class)
  private fun runCommand(args: List<String?>) {
    val processBuilder = ProcessBuilder(args)
    processBuilder.redirectErrorStream(true)
    processBuilder.environment().putAll(params.environmentVariables)

    // Set the current working directory for the cargo command.
    processBuilder.directory(crateRoot.toFile())
    val process = processBuilder.start()
    Executors.newSingleThreadExecutor().submit {
      BufferedReader(InputStreamReader(process.inputStream)).lines().forEach { charSequence: String?
        ->
        log.info(charSequence)
      }
    }

    val exitCode = process.waitFor()
    if (exitCode != 0) {
      throw MojoExecutionException("Cargo command failed with exit code $exitCode")
    }
  }

  @Throws(MojoExecutionException::class, MojoFailureException::class)
  private fun cargo(args: List<String?>) {
    val cargoPath = cargoPath
    val cmd: MutableList<String?> = ArrayList()
    cmd.add(cargoPath)
    cmd.addAll(args)
    log.info("Working directory: $crateRoot")
    if (params.environmentVariables.isNotEmpty()) {
      log.info("Environment variables:")
      for (key in params.environmentVariables.keys) {
        log.info("  $key=" + params.environmentVariables[key]?.let { Shlex.quote(it) })
      }
    }
    log.info("Running: " + Shlex.quote(cmd))
    try {
      runCommand(cmd)
    } catch (e: IOException) {

      CargoInstalledChecker.INSTANCE.check(cargoPath)
      throw MojoFailureException("Failed to invoke cargo", e)
    } catch (e: InterruptedException) {
      CargoInstalledChecker.INSTANCE.check(cargoPath)
      throw MojoFailureException("Failed to invoke cargo", e)
    }
  }

  private fun addCargoArgs(args: MutableList<String?>) {
    if (params.verbosity != null) {
      args.add(params.verbosity)
    }

    args.add("--target-dir")
    args.add(targetDir.toAbsolutePath().toString())

    if (params.release) {
      args.add("--release")
    }

    if (params.allFeatures) {
      args.add("--all-features")
    }

    if (params.noDefaultFeatures) {
      args.add("--no-default-features")
    }

    val cleanedFeatures = params.cleanedFeatures()
    if (cleanedFeatures.isNotEmpty()) {
      args.add("--features")
      args.add(java.lang.String.join(",", *cleanedFeatures))
    }

    if (params.tests) {
      args.add("--tests")
    }

    //        if (params.extraArgs != null) {
    Collections.addAll(args, *params.extraArgs)
    //        }
  }

  @Throws(MojoExecutionException::class, MojoFailureException::class)
  fun build() {
    val args: MutableList<String?> = ArrayList()
    args.add("build")
    addCargoArgs(args)
    cargo(args)
  }

  @Throws(MojoExecutionException::class, MojoFailureException::class)
  fun test() {
    val args: MutableList<String?> = ArrayList()
    args.add("test")
    addCargoArgs(args)
    cargo(args)
  }

  @Throws(MojoExecutionException::class)
  private fun resolveCopyToDir(): Path? {
    var copyToDir: Path = params.copyToDir ?: return null

    if (params.copyWithPlatformDir) {
      copyToDir = copyToDir.resolve("${OS.os}-${OS.arch}")
    }

    if (!Files.exists(copyToDir, LinkOption.NOFOLLOW_LINKS)) {
      try {
        Files.createDirectories(copyToDir)
      } catch (e: IOException) {
        throw MojoExecutionException("Failed to create directory $copyToDir : ${e.message}", e)
      }
    }

    if (!Files.isDirectory(copyToDir)) {
      throw MojoExecutionException("$copyToDir is not a directory")
    }
    return copyToDir
  }

  @Throws(MojoExecutionException::class)
  fun copyArtifacts() {
    // Cargo nightly has support for `--out-dir`
    // which allows us to copy the artifacts directly to the desired path.
    // Once the feature is stabilized, copy the artifacts directly via:
    // args.add("--out-dir")
    // args.add(resolveCopyToDir());
    val copyToDir: Path = resolveCopyToDir() ?: return
    val artifactPaths: List<Path> = artifactPaths
    log.info(
        "Copying " +
            dirName +
            "'s artifacts to " +
            Shlex.quote(copyToDir.toAbsolutePath().toString()))

    for (artifactPath in artifactPaths) {
      val fileName: Path =
          if (params.copyWithPlatformDir) artifactPath.fileName
          else
              Paths.get(
                  JniLibLoader.getJniLibPath(
                      JniLibLoader.getLibName(artifactPath.fileName.toString()), false))
      val destPath: Path = copyToDir.resolve(fileName)
      log.info("fileName:${fileName}, destPath:${destPath}")
      try {
        Files.copy(artifactPath, destPath, StandardCopyOption.REPLACE_EXISTING)
      } catch (e: IOException) {
        throw MojoExecutionException(
            "Failed to copy " + artifactPath + " to " + copyToDir + ":" + e.message)
      }
      log.info("Copied " + Shlex.quote(fileName.toString()))
    }
  }

  class Params {
    var verbosity: String? = null
    var environmentVariables: Map<String, String> = emptyMap()
    var cargoPath: String = ""
    var release: Boolean = false
    var features: Array<String> = emptyArray()
    var allFeatures: Boolean = false
    var noDefaultFeatures: Boolean = false
    var tests: Boolean = false
    var extraArgs: Array<String> = emptyArray()
    var copyToDir: Path? = null
    var copyWithPlatformDir: Boolean = false

    /** Returns the features array with empty and null elements removed. */
    fun cleanedFeatures(): Array<String?> {
      //            if (features == null || features.size == 0) {
      //                return arrayOfNulls(0)
      //            }
      val cleanedFeatures: MutableList<String?> = ArrayList()
      for (feature in features) {
        //                if (feature != null) {
        val feature = feature.trim { it <= ' ' }
        if (feature.isNotEmpty()) {
          cleanedFeatures.add(feature)
        }
        //                }
      }
      return cleanedFeatures.toTypedArray<String?>()
    }
  }

  companion object {
    fun pinLibName(name: String): String {
      return Shlex.quote(JniLibLoader.getJniLibName(name))
    }

    fun pinBinName(name: String?): String {
      return name + if (OS.isWindows) ".exe" else ""
    }

    fun nullLog(): Log {
      return object : Log {
        override fun debug(content: CharSequence) {}

        override fun debug(content: CharSequence, error: Throwable) {}

        override fun debug(error: Throwable) {}

        override fun error(content: CharSequence) {}

        override fun error(content: CharSequence, error: Throwable) {}

        override fun error(error: Throwable) {}

        override fun info(content: CharSequence) {}

        override fun info(content: CharSequence, error: Throwable) {}

        override fun info(error: Throwable) {}

        override fun isDebugEnabled(): Boolean {
          return false
        }

        override fun isErrorEnabled(): Boolean {
          return false
        }

        override fun isInfoEnabled(): Boolean {
          return false
        }

        override fun isWarnEnabled(): Boolean {
          return false
        }

        override fun warn(content: CharSequence) {}

        override fun warn(content: CharSequence, error: Throwable) {}

        override fun warn(error: Throwable) {}
      }
    }
  }
}
