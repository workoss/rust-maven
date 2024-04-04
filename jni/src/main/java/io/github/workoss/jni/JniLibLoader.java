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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author workoss
 */
@SuppressWarnings("ALL")
public final class JniLibLoader {

  private static final Logger log = LoggerFactory.getLogger("io.github.workoss.jni.JniLibLoader");

  private JniLibLoader() {}

  private static volatile JniLibLoader INSTANCE = null;

  public synchronized boolean loadLibrary(String libName, boolean withPlatformDir)
      throws IOException {
    return loadLibrary(null, OS.tmpDir, libName, withPlatformDir);
  }

  public synchronized boolean loadLibrary(
      ClassLoader classLoader, String libName, boolean withPlatformDir) throws IOException {
    return loadLibrary(classLoader, OS.tmpDir, libName, withPlatformDir);
  }

  public synchronized boolean loadLibrary(
      ClassLoader classLoader, String tmpDir, String libName, boolean withPlatformDir)
      throws IOException {
    return loadLibrary(classLoader, tmpDir, null, libName, withPlatformDir);
  }

  public synchronized boolean loadLibrary(
      ClassLoader classLoader,
      String tmpDir,
      String prefix,
      String libName,
      boolean withPlatformDir)
      throws IOException {
    boolean isLoadSystem =
        Stream.of(getJniLibName(libName), libName)
            .anyMatch(
                lib -> {
                  try {
                    loadSystemLibrary(libName);
                    return true;
                  } catch (UnsatisfiedLinkError e) {
                    log.warn("[LIB] load system lib {} error: {}", lib, e.getMessage());
                    return false;
                  }
                });
    if (isLoadSystem) {
      return true;
    }
    // Load from JAR
    try {
      loadLibraryFromJar(classLoader, tmpDir, prefix, libName, withPlatformDir);
      log.info("[LIB] load jar lib {} success", libName);
      return true;
    } catch (UnsatisfiedLinkError e) {
      throw new IOException("[LIB] load " + libName + " error:" + e.getMessage(), e);
    }
  }

  public static JniLibLoader getInstance() {
    if (INSTANCE != null) {
      return INSTANCE;
    }
    synchronized (JniLibLoader.class) {
      if (INSTANCE == null) {
        INSTANCE = new JniLibLoader();
      }
    }
    return INSTANCE;
  }

  private static void loadSystemLibrary(String libName) {
    System.loadLibrary(libName);
  }

  private static boolean loadLibraryFromJar(
      ClassLoader classLoader,
      String tmpDir,
      String prefix,
      String libName,
      boolean withPlatformDir)
      throws IOException {
    classLoader = classLoader != null ? classLoader : JniLibLoader.class.getClassLoader();
    String fullLibraryPath = getJniLibPath(prefix, libName, withPlatformDir);

    Path tmpLibFullPath = Paths.get(tmpDir + fullLibraryPath).toAbsolutePath();

    File tmpLibFile = tmpLibFullPath.toFile();
    if (tmpLibFile.exists()) {
      log.info("{} was deleted", tmpLibFullPath);
      tmpLibFile.delete();
    }

    File parentFile = tmpLibFile.getParentFile();
    if (!parentFile.exists()) {
      parentFile.mkdirs();
    }

    try (InputStream libInputStream = classLoader.getResourceAsStream(fullLibraryPath)) {
      if (libInputStream == null) {
        throw new RuntimeException(libName + " was not found inside JAR.");
      }
      Files.copy(libInputStream, tmpLibFullPath, StandardCopyOption.REPLACE_EXISTING);
      System.load(tmpLibFullPath.toString());
    }
    return true;
  }

  private static String getJniLibName(String jniLibName) {
    String libName = jniLibName;
    String libSuffix = OS.isWindows() ? ".dll" : OS.isOSX() ? ".dylib" : ".so";
    libName = libName.replace(libSuffix, "");

    if (!OS.isWindows() && libName.startsWith("lib")) {
      libName = libName.replaceFirst("lib", "");
    }
    return libName;
  }

  private static String getJniLibPath(String prefix, String libName, boolean withPlatformDir) {
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
