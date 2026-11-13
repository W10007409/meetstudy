package com.wjthinkbig.meetstudy.data

import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

private val fm get() = NSFileManager.defaultManager

actual fun readTextFile(path: String): String? {
    if (!fm.fileExistsAtPath(path)) return null
    return NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null) ?: error("read failed")
}

actual fun writeTextFile(path: String, text: String) {
    fm.createDirectoryAtPath(path.substringBeforeLast('/'), true, null, null)
    // atomically = temp file + rename, like the Android actual.
    check(NSString.create(string = text).writeToFile(path, true, NSUTF8StringEncoding, null)) { "write failed" }
}

// Throws on failure so callers never proceed as if the file had been moved aside.
actual fun renameFile(from: String, to: String) {
    check(fm.moveItemAtPath(from, to, null)) { "rename failed" }
}

actual fun appFilesDir(): String {
    val dir = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).first() as String
    fm.createDirectoryAtPath(dir, true, null, null)
    return dir
}
