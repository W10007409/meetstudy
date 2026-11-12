package com.wjthinkbig.meetstudy.data

/** Returns null if the file does not exist. */
expect fun readTextFile(path: String): String?

/** Atomic where the platform allows, so a crash mid-write cannot corrupt the file. */
expect fun writeTextFile(path: String, text: String)

/** Renames a file (used to quarantine corrupt data). */
expect fun renameFile(from: String, to: String)
