package ephyra.data.cache

import java.io.File
import java.io.IOException

/**
 * Writes a cache file through a sibling temporary file and publishes it only after the
 * write completes. A process death or source failure must never leave a truncated file at
 * the final cache path where readers would treat it as valid image data.
 */
internal fun writeFileAtomically(target: File, write: (File) -> Unit) {
    val parent = target.parentFile
    parent?.mkdirs()
    val temporary = File(parent, ".${target.name}.${System.nanoTime()}.tmp")
    try {
        write(temporary)
        if (!temporary.renameTo(target)) {
            throw IOException("Could not publish cache file: $target")
        }
    } finally {
        temporary.delete()
    }
}
