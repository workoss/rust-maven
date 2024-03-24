package io.github.workoss

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicBoolean


class JniLibLoader {

    private val log = LoggerFactory.getLogger("io.github.workoss.JniLibLoader")

    companion object {

        private var isLoaded = AtomicBoolean(false)

        val instance: JniLibLoader by lazy(
            mode = LazyThreadSafetyMode.SYNCHRONIZED, initializer = ::JniLibLoader
        )


        private fun tryLoadLibrary(dir: String,fullLibraryName: String): Boolean{
            var fullLibraryPath = try {
                Paths.get(dir,fullLibraryName)
            }catch (ignored: InvalidPathException){
                return false;
            }
            if (!Files.exists(fullLibraryPath)){
                return false;
            }
            Files.createTempDirectory(dir).toAbsolutePath().toString().also {
                Files.copy(fullLibraryPath,Paths.get(it,fullLibraryName))
            }


            return TODO("Provide the return value")
        }

    }

}