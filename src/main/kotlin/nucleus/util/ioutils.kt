package nucleus.util

import java.io.*
import kotlin.text.Charsets.UTF_8

fun fileExists(filename: String): Boolean {
    return File(filename).exists()
}

// Inspired by python syntax
inline fun open(filename: String, mode: Char, callback: (FileHandle)->Unit) {
    if (mode != 'w' && mode != 'r' && mode != 'a') throw IOException("Invalid mode")

    val handle = if (mode == 'r') ReadFileHandle(filename) else WriteFileHandle(filename, mode == 'a')

    handle.use(callback)
}

interface FileHandle : Iterable<String>, Closeable {

    fun read(): String

    fun readLines(): List<String>

    fun write(content: String)

    fun writeLines(lines: List<String>)
}

class WriteFileHandle(filename: String, append: Boolean) : FileHandle {
    val writer = BufferedWriter(OutputStreamWriter(FileOutputStream(filename, append), UTF_8))

    override fun read(): String {
        throw IOException("This handle is not opened for reading!")
    }

    override fun readLines(): List<String> {
        throw IOException("This handle is not opened for reading!")
    }

    override fun write(content: String) {
        writer.append(content)
    }

    override fun writeLines(lines: List<String>) {
        write(lines.joinToString("\n"))
    }

    override fun iterator(): Iterator<String> {
        throw IOException("This handle is not opened for reading!")
    }

    override fun close() {
        writer.close()
    }

}

class ReadFileHandle(filename: String) : FileHandle {
    val reader = BufferedReader(InputStreamReader(FileInputStream(filename), UTF_8))

    override fun read(): String {
        return reader.readText()
    }

    override fun readLines(): List<String> {
        return reader.readLines()
    }

    override fun write(content: String) {
        throw IOException("This handle is not opened for writing!")
    }

    override fun writeLines(lines: List<String>) {
        throw IOException("This handle is not opened for writing!")
    }

    override fun iterator(): Iterator<String> {
        return reader.lines().iterator()
    }

    override fun close() {
        reader.close()
    }
}
