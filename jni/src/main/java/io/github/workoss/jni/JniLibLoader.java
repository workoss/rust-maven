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

package io.github.workoss.jni;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

/**
 * JniLibLoader
 *
 * @author workoss
 */
@SuppressWarnings("ALL")
public final class JniLibLoader {

    private static final Logger LOG = LoggerFactory.getLogger("io.github.workoss.jni.JniLibLoader");

    private JniLibLoader() {
    }

    private static volatile JniLibLoader instance = null;

    public synchronized boolean loadLibrary(final String libName, final boolean withPlatformDir) throws IOException {
        return loadLibrary(null, OS.tmpDir, libName, withPlatformDir);
    }

    public synchronized boolean loadLibrary(final ClassLoader classLoader, final String libName,
                                            final boolean withPlatformDir) throws IOException {
        return loadLibrary(classLoader, OS.tmpDir, libName, withPlatformDir);
    }

    public synchronized boolean loadLibrary(final ClassLoader classLoader, final String tmpDir, final String libName,
                                            final boolean withPlatformDir) throws IOException {
        return loadLibrary(classLoader, tmpDir, null, libName, withPlatformDir);
    }

    /**
     * 加载动态链接库
     *
     * @param classLoader     类加载器，动态链接库所在的库classloader
     * @param tmpDir          系统临时目录
     * @param prefix          前缀文件目录
     * @param libName         动态链接库名称
     * @param withPlatformDir 是否平台目录
     * @return 是否加载成功
     * @throws IOException 加载失败异常
     */
    public synchronized boolean loadLibrary(final ClassLoader classLoader, final String tmpDir, final String prefix,
                                            final String libName, final boolean withPlatformDir) throws IOException {
        boolean isLoadSystem = Stream.of(getJniLibNameWithoutSuffix(libName), libName).anyMatch(lib -> {
            try {
                loadSystemLibrary(libName);
                return true;
            } catch (UnsatisfiedLinkError e) {
                LOG.warn("[LIB] load system lib {} error: {}", lib, e.getMessage());
                return false;
            }
        });
        if (isLoadSystem) {
            return true;
        }
        // Load from JAR
        try {
            loadLibraryFromJar(classLoader, tmpDir, prefix, libName, withPlatformDir);
            LOG.info("[LIB] load jar lib {} success", libName);
            return true;
        } catch (UnsatisfiedLinkError e) {
            throw new IOException("[LIB] load " + libName + " error:" + e.getMessage(), e);
        }
    }

    /**
     * 获取JniLoader 实例
     *
     * @return 实例
     */
    public static JniLibLoader getInstance() {
        if (instance != null) {
            return instance;
        }
        synchronized (JniLibLoader.class) {
            if (instance == null) {
                instance = new JniLibLoader();
            }
        }
        return instance;
    }

    private static void loadSystemLibrary(final String libName) {
        System.loadLibrary(libName);
    }

    private static boolean loadLibraryFromJar(final ClassLoader classLoader, final String tmpDir, final String prefix,
                                              final String libName, final boolean withPlatformDir) throws IOException {
        ClassLoader actualClassLoader = classLoader != null ? classLoader : JniLibLoader.class.getClassLoader();
        String fullLibraryPath = getJniLibPath(prefix, libName, withPlatformDir);

        try (InputStream libInputStream = actualClassLoader.getResourceAsStream(fullLibraryPath)) {
            if (libInputStream == null) {
                throw new RuntimeException(libName + " was not found inside JAR.");
            }
            Path tmpLibFullPath = Paths.get(tmpDir + fullLibraryPath).toAbsolutePath();

            File tmpLibFile = tmpLibFullPath.toFile();
            if (tmpLibFile.exists()) {
                LOG.info("{} was deleted", tmpLibFullPath);
                tmpLibFile.delete();
            }

            File parentFile = tmpLibFile.getParentFile();
            if (!parentFile.exists()) {
                parentFile.mkdirs();
            }

            Files.copy(libInputStream, tmpLibFullPath, StandardCopyOption.REPLACE_EXISTING);
            System.load(tmpLibFullPath.toString());
        }
        return true;
    }

    private static String getLibName(final String jniLibName) {
        String osArch = "-" + OS.os + "-" + OS.arch;
        String libName = jniLibName.replace(osArch, "");
        String libSuffix = OS.isWindows() ? ".dll" : OS.isOSX() ? ".dylib" : ".so";
        libName = libName.replace(libSuffix, "");
        if (!OS.isWindows() && libName.startsWith("lib")) {
            libName = libName.replaceFirst("lib", "");
        }
        return libName;
    }

    private static String getJniLibNameWithoutSuffix(final String libName) {
        String libPrefix = OS.isWindows() ? "" : "lib";
        return libPrefix + libName.replace("-", "_");
    }

    private static String getJniLibName(final String libName) {
        String platformLibName = libName.replace("-", "_");
        String libPrefix = OS.isWindows() ? "" : "lib";
        String libSuffix = OS.isWindows() ? ".dll" : OS.isOSX() ? ".dylib" : ".so";
        return libPrefix + platformLibName + libSuffix;
    }

    private static String getJniLibPath(final String prefix, final String libName, final boolean withPlatformDir) {
        String actualPrefix = prefix != null ? prefix + "/" : "";
        String libPrefix = OS.isWindows() ? "" : "lib";
        String libSuffix = OS.isWindows() ? ".dll" : OS.isOSX() ? ".dylib" : ".so";
        String platformLibName = libName.replace("-", "_");
        if (withPlatformDir) {
            return actualPrefix + OS.os + "-" + OS.arch + "/" + libPrefix + platformLibName + libSuffix;
        }
        return actualPrefix + libPrefix + platformLibName + "-" + OS.os + "-" + OS.arch + libSuffix;
    }
}
