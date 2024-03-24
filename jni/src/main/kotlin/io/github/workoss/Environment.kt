package io.github.workoss.io.github.workoss

class Environment {

    companion object {
        internal val osName: String by lazy {
            System.getProperty("os.name")
        }

        internal val osArch: String by lazy {
            System.getProperty("os.arch")
        }

        val systemTempDir: String by lazy {
            System.getProperty("java.io.tmpdir")
        }
    }

}