package ru.DmN.bul

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import java.io.File
import java.net.URLClassLoader

class TestClassLoader : URLClassLoader(emptyArray()) {
    init {
        File("dump").deleteRecursively()
    }

    override fun loadClass(name: String): Class<*> {
        if (name.startsWith("ru")) {
            val loaded = this.findLoadedClass(name)
            if (loaded != null)
                return loaded
            val file = "${name.replace('.', '/')}.class"
            val stream = this.getResourceAsStream(file)
            stream ?: throw ClassNotFoundException(name);
            val bytes = stream.readBytes()
            val processor = ClassProcessor()
            ClassReader(bytes).accept(processor, 0)
            val processed = ClassWriter(ClassWriter.COMPUTE_MAXS).apply { processor.accept(this) }.toByteArray()
            File("dump/${file.take(file.lastIndexOf('/'))}").mkdirs()
            File("dump/$file").writeBytes(processed)
            return this.defineClass(name, processed, 0, processed.size)
        }
        return super.loadClass(name)
    }
}