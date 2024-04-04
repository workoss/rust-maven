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
package io.github.workoss.jni

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import org.slf4j.LoggerFactory

@Suppress("UNUSED", "NAME_SHADOWING")
class JniLibLoader {
  @Synchronized
  @Throws(IOException::class)
  fun loadLibrary(
      libName: String,
      withPlatformDir: Boolean = false,
  ): Boolean {
    return loadLibrary(null, OS.tmpDir, libName, withPlatformDir)
  }

  @Synchronized
  @Throws(IOException::class)
  fun loadLibrary(
      classLoader: ClassLoader?,
      libName: String,
      withPlatformDir: Boolean = false,
  ): Boolean {
    return loadLibrary(classLoader, OS.tmpDir, libName, withPlatformDir)
  }

  @Synchronized
  @Throws(IOException::class)
  fun loadLibrary(
      classLoader: ClassLoader?,
      tmpDir: String,
      libName: String,
      withPlatformDir: Boolean = false,
  ): Boolean {
    // load jar
    return loadLibrary(classLoader, tmpDir, null, libName, withPlatformDir)
  }

  @Synchronized
  @Throws(IOException::class)
  fun loadLibrary(
      classLoader: ClassLoader?,
      tmpDir: String,
      prefix: String?,
      libName: String,
      withPlatformDir: Boolean = false,
  ): Boolean {
    val isLoadSystem: Boolean =
        listOf(getJniLibNameWithoutSuffix(libName), libName).stream().anyMatch {
          loadSystemLibrary(libName)
              .onFailure { e -> log.warn("[LIB] load system lib {} error:{}", it, e.message) }
              .isSuccess
        }
    // load jar
    return isLoadSystem ||
        loadLibraryFromJar(classLoader, tmpDir, prefix, libName, withPlatformDir)
            .onSuccess { log.info("[LIB] load jar lib {} success", libName) }
            .onFailure { e -> throw IOException("[LIB] load $libName error:${e.message}") }
            .isSuccess
  }

  companion object {
    private val log = LoggerFactory.getLogger("io.github.workoss.jni.JniLibLoader")

    @JvmStatic
    val instance: JniLibLoader by
        lazy(
            mode = LazyThreadSafetyMode.SYNCHRONIZED,
            initializer = ::JniLibLoader,
        )

    fun getLibName(jniLibName: String): String {
      var libName = jniLibName
      val libSuffix =
          if (OS.isWindows) {
            ".dll"
          } else if (OS.isOSX) {
            ".dylib"
          } else {
            ".so"
          }
      libName = libName.replace(libSuffix, "")

      if (!OS.isWindows && libName.startsWith("lib")) {
        libName = libName.replaceFirst("lib", "")
      }
      return libName
    }

    fun getJniLibNameWithoutSuffix(libName: String): String {
      val libName = libName.replace("-", "_")
      val libPrefix = if (OS.isWindows) "" else "lib"
      return "$libPrefix$libName"
    }

    fun getJniLibName(libName: String): String {
      val libName = libName.replace("-", "_")
      val libPrefix = if (OS.isWindows) "" else "lib"
      val libSuffix =
          if (OS.isWindows) {
            ".dll"
          } else if (OS.isOSX) {
            ".dylib"
          } else {
            ".so"
          }
      return "$libPrefix$libName$libSuffix"
    }

    fun getJniLibPath(
        libName: String,
        withPlatformDir: Boolean = false,
    ): String {
      return getJniLibPath(null, libName, withPlatformDir)
    }

    private fun getJniLibPath(
        prefix: String?,
        libName: String,
        withPlatformDir: Boolean = false,
    ): String {
      val prefix = if (prefix != null) "$prefix/" else ""
      val libPrefix = if (OS.isWindows) "" else "lib"
      val libSuffix =
          if (OS.isWindows) {
            ".dll"
          } else if (OS.isOSX) {
            ".dylib"
          } else {
            ".so"
          }
      val libName = libName.replace("-", "_")
      if (withPlatformDir) {
        return "$prefix${OS.os}-${OS.arch}/$libPrefix$libName$libSuffix"
      }
      return "$prefix$libPrefix$libName-${OS.os}-${OS.arch}$libSuffix"
    }

    private fun loadSystemLibrary(libName: String): Result<Unit> = runCatching {
      System.loadLibrary(libName)
    }

    private fun loadLibraryFromJar(
        classLoader: ClassLoader?,
        tmpDir: String,
        prefix: String?,
        libName: String,
        withPlatformDir: Boolean = false,
    ): Result<Unit> = runCatching {
      val classLoader = classLoader ?: JniLibLoader::class.java.classLoader
      val fullLibraryPath = getJniLibPath(prefix, libName, withPlatformDir)
      // tmp+ fullLibraryPath
      val tmpLibFulPath: Path = Paths.get("$tmpDir$fullLibraryPath").toAbsolutePath()

      tmpLibFulPath.toFile().exists().also {
        if (it) {
          log.info("$tmpLibFulPath was deleted")
        }
      }

      // create parent dir if not exists
      val parentFile = tmpLibFulPath.parent.toFile()
      if (!parentFile.exists()) {
        parentFile.mkdirs()
      }
      classLoader.getResourceAsStream(fullLibraryPath).use {
        if (it == null) {
          throw RuntimeException("$libName was not found inside JAR.")
        }
        Files.copy(it, tmpLibFulPath, StandardCopyOption.REPLACE_EXISTING)
      }
      System.load(tmpLibFulPath.toString())
    }
  }
}
