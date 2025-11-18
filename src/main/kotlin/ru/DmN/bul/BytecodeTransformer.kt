package ru.DmN.bul

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import javax.annotation.processing.Messager
import javax.tools.Diagnostic.Kind

/**
 * Трансформер байткода, который применяет ClassProcessor к классам.
 */
class BytecodeTransformer(
    private val classLoader: ClassLoader,
    private val messager: Messager?
) {

    /**
     * Применяет трансформации к байткоду класса.
     *
     * @param classBytes оригинальный байткод класса
     * @return трансформированный байткод
     */
    fun transform(classBytes: ByteArray): ByteArray {
        return try {
            val classReader = ClassReader(classBytes)
            val classProcessor = ClassProcessor()

            // Применяем наш процессор
            classReader.accept(classProcessor, ClassReader.EXPAND_FRAMES)

            // Записываем трансформированный класс
            val classWriter = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
            classProcessor.accept(classWriter)

            classWriter.toByteArray()

        } catch (e: Exception) {
            messager?.printMessage(
                Kind.ERROR,
                "Ошибка трансформации байткода: ${e.message}"
            )
            // В случае ошибки возвращаем оригинальный байткод
            classBytes
        }
    }

    /**
     * Применяет трансформации к нескольким классам.
     *
     * @param classes карта [имя класса -> байткод]
     * @return карта трансформированных классов
     */
    fun transformAll(classes: Map<String, ByteArray>): Map<String, ByteArray> {
        return classes.mapValues { (className, bytes) ->
            try {
                transform(bytes)
            } catch (e: Exception) {
                messager?.printMessage(
                    Kind.WARNING,
                    "Не удалось трансформировать класс $className: ${e.message}"
                )
                bytes
            }
        }
    }
}