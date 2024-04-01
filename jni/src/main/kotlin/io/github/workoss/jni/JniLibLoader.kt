package io.github.workoss.jni

import io.github.workoss.jni.OS.arch
import io.github.workoss.jni.OS.isOSX
import io.github.workoss.jni.OS.isWindows
import io.github.workoss.jni.OS.os
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import kotlin.io.path.deleteIfExists


class JniLibLoader {

    @Synchronized
    @Throws(IOException::class)
    fun loadLibrary(
        libName: String,
        withPlatformDir: Boolean = false
    ): Boolean {
        return loadLibrary(null, System.getProperty("java.io.tmpdir"), libName, withPlatformDir)
    }

    @Synchronized
    @Throws(IOException::class)
    fun loadLibrary(
        classLoader: ClassLoader?,
        libName: String,
        withPlatformDir: Boolean = false
    ): Boolean {
        return loadLibrary(classLoader, System.getProperty("java.io.tmpdir"), libName, withPlatformDir)
    }

    @Synchronized
    @Throws(IOException::class)
    fun loadLibrary(
        classLoader: ClassLoader?,
        tmpDir: String,
        libName: String,
        withPlatformDir: Boolean = false
    ): Boolean {
        //load jar
        return loadLibrary(classLoader, tmpDir, null,libName, withPlatformDir)
    }

    @Synchronized
    @Throws(IOException::class)
    fun loadLibrary(
        classLoader: ClassLoader?,
        tmpDir: String,
        prefix: String?,
        libName: String,
        withPlatformDir: Boolean = false
    ): Boolean {
        //load java lib
        for (libraryName in arrayOf(getJniLibPath(libName), getJniLibName(libName))) {
            try {
                System.loadLibrary(libraryName)
                log.info("[LIB] load {} success", libName)
                return true
            } catch (e: UnsatisfiedLinkError) {
                log.warn("[LIB] load {} error:{}", libName, e.message)
            }
        }
        //load jar
        return loadLibraryFromJar(classLoader, tmpDir, prefix, libName, withPlatformDir)
    }

    companion object {

        private val log = LoggerFactory.getLogger("io.github.workoss.jni.JniLibLoader")

        @JvmStatic
        val instance: JniLibLoader by lazy(
            mode = LazyThreadSafetyMode.SYNCHRONIZED, initializer = ::JniLibLoader
        )

        fun getLibName(jniLibName: String): String {
            var libName = jniLibName
            val libSuffix = if (isWindows) ".dll" else if (isOSX) ".dylib" else ".so";
            libName = libName.replace(libSuffix, "")

            if (!isWindows && libName.startsWith("lib")) {
                libName = libName.replaceFirst("lib", "")
            }
            return libName
        }

        fun getJniLibName(libName: String): String {
            val libName = libName.replace("-", "_")
            val libPrefix = if (isWindows) "" else "lib";
            val libSuffix = if (isWindows) ".dll" else if (isOSX) ".dylib" else ".so";
            return "$libPrefix$libName$libSuffix"
        }

        fun getJniLibPath(libName: String, withPlatformDir: Boolean = false): String {
            return getJniLibPath(null, libName, withPlatformDir)
        }
        fun getJniLibPath(prefix: String?,libName: String, withPlatformDir: Boolean = false): String {
            var prefix = if (prefix != null) "$prefix/" else ""
            val libPrefix = if (isWindows) "" else "lib";
            val libSuffix = if (isWindows) ".dll" else if (isOSX) ".dylib" else ".so";
            val libName = libName.replace("-", "_")
            if (withPlatformDir) {
                return "$prefix$os-$arch/$libPrefix$libName$libSuffix"
            }
            return "$prefix$libPrefix$libName-$os-$arch$libSuffix"
        }


        @Throws(IOException::class)
        private fun loadLibraryFromJar(
            classLoader: ClassLoader?,
            tmpDir: String,
            prefix: String?,
            libName: String,
            withPlatformDir: Boolean = false
        ): Boolean {
            var classLoader = classLoader ?: JniLibLoader::class.java.classLoader
            val fullLibraryPath = getJniLibPath(prefix,libName, withPlatformDir)
            // tmp+ fullLibraryPath
            val tmpLibFulPath = Paths.get("$tmpDir$fullLibraryPath").toAbsolutePath()
            tmpLibFulPath.deleteIfExists().also {
                if (it) {
                    log.info("$tmpLibFulPath was deleted")
                }
            }
            //create parent dir if not exists
            val parentFile = tmpLibFulPath.parent.toFile()
            if (!parentFile.exists()) {
                parentFile.mkdirs()
            }
            var resourceStream: InputStream? = null;
            try {
                resourceStream = classLoader.getResourceAsStream(fullLibraryPath)
                if (resourceStream == null) {
                    throw RuntimeException("$libName was not found inside JAR.")
                }
                Files.copy(resourceStream, tmpLibFulPath, StandardCopyOption.REPLACE_EXISTING)
                System.load(tmpLibFulPath.toString())
                return true
            } catch (e: InvalidPathException) {
                log.error("Invalid path: $tmpLibFulPath", e)
                return false
            } finally {
                if (resourceStream != null) {
                    resourceStream.close()
                }
            }
        }

    }

}