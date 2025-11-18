package ru.DmN.bul

import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic
import javax.tools.StandardLocation

/**
 * Annotation Processor для применения трансформаций байткода во время компиляции.
 *
 * Обрабатывает классы, помеченные аннотацией @BytecodeProcessor, и применяет
 * к ним трансформации с помощью ClassProcessor.
 */
@SupportedAnnotationTypes("ru.DmN.bul.BytecodeProcessor")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
class BytecodeAnnotationProcessor : AbstractProcessor() {

    private lateinit var filer: Filer
    private lateinit var messager: Messager

    /** Флаг, указывающий, что обработка уже выполнялась */
    private var processed = false

    /**
     * Инициализация процессора.
     *
     * @param processingEnv окружение обработки аннотаций
     */
    override fun init(processingEnv: ProcessingEnvironment) {
        super.init(processingEnv)
        filer = processingEnv.filer
        messager = processingEnv.messager
    }

    /**
     * Основной метод обработки аннотаций.
     *
     * @param annotations обрабатываемые аннотации
     * @param roundEnv окружение текущего раунда обработки
     * @return true если аннотации были обработаны, иначе false
     */
    override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {
        if (processed || annotations.isEmpty()) {
            return false
        }

        try {
            val annotatedElements = roundEnv.getElementsAnnotatedWith(
                processingEnv.elementUtils.getTypeElement("ru.DmN.bul.BytecodeProcessor")
            )

            if (annotatedElements.isNotEmpty()) {
                processAnnotatedClasses(annotatedElements)
                processed = true
            }
        } catch (e: Exception) {
            messager.printMessage(
                Diagnostic.Kind.ERROR,
                "Ошибка при обработке байткода: ${e.message}"
            )
            e.printStackTrace()
        }

        return true
    }

    /**
     * Обрабатывает все классы, помеченные аннотацией @BytecodeProcessor.
     *
     * @param elements элементы, помеченные аннотацией
     */
    private fun processAnnotatedClasses(elements: Set<*>) {
        messager.printMessage(Diagnostic.Kind.NOTE, "Начало трансформации байткода...")

        val classLoader = createClassLoader()
        val transformer = BytecodeTransformer(classLoader, messager)

        elements.forEach { element ->
            if (element is TypeElement) {
                processClass(element, transformer)
            }
        }

        messager.printMessage(Diagnostic.Kind.NOTE, "Трансформация байткода завершена")
    }

    /**
     * Обрабатывает отдельный класс.
     *
     * @param classElement элемент класса
     * @param transformer трансформер байткода
     */
    private fun processClass(classElement: TypeElement, transformer: BytecodeTransformer) {
        val className = classElement.qualifiedName.toString()
        val internalName = className.replace('.', '/')
        val classFileName = "$internalName.class"

        messager.printMessage(Diagnostic.Kind.NOTE, "Обработка класса: $className")

        try {
            // Читаем оригинальный байткод класса
            val originalBytes = readClassBytes(classFileName)
            if (originalBytes != null) {
                // Применяем трансформацию
                val transformedBytes = transformer.transform(originalBytes)

                // Записываем трансформированный класс
                writeClassFile(className, transformedBytes)
            }
        } catch (e: Exception) {
            messager.printMessage(
                Diagnostic.Kind.ERROR,
                "Ошибка при обработке класса $className: ${e.message}"
            )
        }
    }

    /**
     * Читает байткод класса из файловой системы компилятора.
     *
     * @param classFileName имя файла класса
     * @return байткод класса или null если файл не найден
     */
    private fun readClassBytes(classFileName: String): ByteArray? {
        return try {
            val resource = filer.getResource(StandardLocation.CLASS_OUTPUT, "", classFileName)
            resource.openInputStream().use { it.readAllBytes() }
        } catch (e: Exception) {
            messager.printMessage(
                Diagnostic.Kind.WARNING,
                "Не удалось прочитать класс $classFileName: ${e.message}"
            )
            null
        }
    }

    /**
     * Записывает трансформированный байткод класса.
     *
     * @param className полное имя класса
     * @param bytes байткод класса
     */
    private fun writeClassFile(className: String, bytes: ByteArray) {
        val classFile = filer.createClassFile(className)
        classFile.openOutputStream().use { output ->
            output.write(bytes)
        }
    }

    /**
     * Создает ClassLoader для загрузки классов во время трансформации.
     *
     * @return ClassLoader с доступом к классам компиляции
     */
    private fun createClassLoader(): ClassLoader {
        return object : ClassLoader() {
            override fun findClass(name: String): Class<*> {
                val classFileName = name.replace('.', '/') + ".class"
                val bytes = readClassBytes(classFileName)
                    ?: throw ClassNotFoundException("Класс не найден: $name")
                return defineClass(name, bytes, 0, bytes.size)
            }
        }
    }
}