package com.wjthinkbig.meetstudy.data

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

actual fun readTextFile(path: String): String? = File(path).takeIf { it.exists() }?.readText()

actual fun writeTextFile(path: String, text: String) {
    val target = File(path)
    target.absoluteFile.parentFile?.mkdirs()
    val tmp = File(target.path + ".tmp")
    tmp.writeText(text)
    Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
}

// Throws on failure so callers never proceed as if the file had been moved aside.
actual fun renameFile(from: String, to: String) {
    Files.move(File(from).toPath(), File(to).toPath())
}
