package ru.DmN.bul

/**
 * Аннотация для пометки классов, которые должны обрабатываться
 * BytecodeAnnotationProcessor во время компиляции.
 *
 * Применение:
 * ```
 * @BytecodeProcessor
 * class MyClass {
 *     // Код класса будет трансформирован во время компиляции
 * }
 * ```
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS)
annotation class BytecodeProcessor