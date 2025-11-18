package ru.DmN.bul

import org.objectweb.asm.*
import org.objectweb.asm.tree.*

/**
 * Процессор для обработки байткода классов.
 *
 * Выполняет преобразование вызовов методов OpcodeInsertion в соответствующие
 * инструкции байткода ASM. Работает с классами, помеченными аннотацией @BytecodeProcessor.
 *
 * Основная функциональность:
 * - Обработка выделения памяти (alloc)
 * - Обработка вызовов конструкторов (init)
 * - Генерация операций доступа к полям (get/put static/field)
 * - Генерация вызовов методов (invoke)
 * - Обработка динамических вызовов (indy)
 */
class ClassProcessor : ClassNode(Opcodes.ASM9) {

    private companion object {
        /** Дескриптор аннотации, активирующей обработку байткода */
        const val PROCESSOR_ANNOTATION = "Lru/DmN/bul/BytecodeProcessor;"

        /** Класс, методы которого подлежат преобразованию в инструкции */
        const val OPCODE_INSERTION_CLASS = "ru/DmN/bul/OpcodeInsertion"
        const val INDY_HANDLE_CLASS = "ru/DmN/bul/IndyHandle"
        const val INDY_HANDLE_ARG_CLASS = "ru/DmN/bul/IndyHandleArg"

        /** Набор инструкций, которые можно безопасно удалять при очистке */
        val INSTRUCTION_CLEANUP_OPS = setOf(-1, Opcodes.NOP, Opcodes.DUP)

        /** Набор инструкций для загрузки целочисленных констант */
        val CONSTANT_INT_OPS = setOf(
            Opcodes.ICONST_0, Opcodes.ICONST_1, Opcodes.ICONST_2,
            Opcodes.ICONST_3, Opcodes.ICONST_4, Opcodes.ICONST_5
        )
    }

    /**
     * Вызывается при завершении посещения класса.
     *
     * Если класс помечен аннотацией @BytecodeProcessor, выполняет преобразование
     * всех методов класса. В противном случае передает управление родительскому классу.
     */
    override fun visitEnd() {
        if (hasBytecodeProcessorAnnotation()) {
            processMethods()
        }
        super.visitEnd()
    }

    /**
     * Проверяет наличие аннотации @BytecodeProcessor у текущего класса.
     *
     * @return true если класс должен обрабатываться, иначе false
     */
    private fun hasBytecodeProcessorAnnotation(): Boolean =
        invisibleAnnotations?.any { it.desc == PROCESSOR_ANNOTATION } == true

    /**
     * Обрабатывает все методы класса, преобразуя вызовы OpcodeInsertion.
     */
    private fun processMethods() {
        methods.forEach { method ->
            processInstructions(method.instructions)
        }
    }

    /**
     * Обрабатывает инструкции метода, заменяя вызовы OpcodeInsertion на соответствующие инструкции ASM.
     *
     * @param instructions список инструкций метода
     */
    private fun processInstructions(instructions: InsnList) {
        safeCycle(instructions) { instr, index ->
            if (isOpcodeInsertionCall(instr)) {
                processOpcodeInsertion(instructions, instr as MethodInsnNode, index)
                true // перезапускаем итерацию, так как инструкции могли измениться
            } else false
        }
    }

    /**
     * Проверяет, является ли инструкция вызовом метода OpcodeInsertion.
     *
     * @param instr проверяемая инструкция
     * @return true если это вызов метода OpcodeInsertion, иначе false
     */
    private fun isOpcodeInsertionCall(instr: AbstractInsnNode): Boolean =
        instr is MethodInsnNode && instr.owner == OPCODE_INSERTION_CLASS

    /**
     * Обрабатывает вызов метода OpcodeInsertion в зависимости от его имени.
     *
     * @param instructions список инструкций метода
     * @param instr инструкция вызова метода
     * @param index индекс инструкции в списке
     */
    private fun processOpcodeInsertion(instructions: InsnList, instr: MethodInsnNode, index: Int) {
        when (instr.name) {
            "alloc" -> processAlloc(instructions, instr, index)
            "init" -> processInit(instructions, instr, index)
            "putStatic", "getStatic", "putField", "getField" -> processFieldAccess(instructions, instr, index)
            "invoke" -> processInvoke(instructions, instr, index)
            "indy" -> processInvokeDynamic(instructions, instr, index)
            else -> processSpecialCases(instructions, instr)
        }
    }

    /**
     * Обрабатывает выделение памяти для нового объекта.
     * Заменяет вызов alloc на инструкции NEW и DUP.
     *
     * @param instructions список инструкций метода
     * @param instr инструкция вызова alloc
     * @param index индекс инструкции в списке
     */
    private fun processAlloc(instructions: InsnList, instr: MethodInsnNode, index: Int) {
        val (newIndex, className) = popLastString(instructions, index, "alloc")
        instructions.insertBefore(instr, TypeInsnNode(Opcodes.NEW, className))
        instructions.set(instr, InsnNode(Opcodes.DUP))
    }

    /**
     * Обрабатывает вызов конструктора объекта.
     * Заменяет вызов init на INVOKESPECIAL <init>.
     *
     * @param instructions список инструкций метода
     * @param instr инструкция вызова init
     * @param index индекс инструкции в списке
     */
    private fun processInit(instructions: InsnList, instr: MethodInsnNode, index: Int) {
        val (index1, desc) = popLastString(instructions, index, "init")
        val (_, className) = popLastString(instructions, index1, "init")
        instructions.set(instr, MethodInsnNode(Opcodes.INVOKESPECIAL, className, "<init>", desc, false))
    }

    /**
     * Обрабатывает операции доступа к статическим и нестатическим полям.
     * Поддерживает GETSTATIC, PUTSTATIC, GETFIELD, PUTFIELD.
     *
     * @param instructions список инструкций метода
     * @param instr инструкция вызова операции доступа к полю
     * @param index индекс инструкции в списке
     */
    private fun processFieldAccess(instructions: InsnList, instr: MethodInsnNode, index: Int) {
        val opcode = when (instr.name) {
            "putStatic" -> Opcodes.PUTSTATIC
            "getStatic" -> Opcodes.GETSTATIC
            "putField" -> Opcodes.PUTFIELD
            "getField" -> Opcodes.GETFIELD
            else -> throw AssertionError("Недопустимая операция доступа к полю")
        }

        val (index1, desc) = popLastString(instructions, index, instr.name)
        val (index2, name) = popLastString(instructions, index1, instr.name)
        val (_, owner) = popLastString(instructions, index2, instr.name)

        instructions.set(instr, FieldInsnNode(opcode, owner, name, desc))
    }

    /**
     * Обрабатывает вызовы методов (статические, виртуальные, специальные и т.д.).
     *
     * @param instructions список инструкций метода
     * @param instr инструкция вызова invoke
     * @param index индекс инструкции в списке
     */
    private fun processInvoke(instructions: InsnList, instr: MethodInsnNode, index: Int) {
        val (index1, itf) = popLastBoolean(instructions, index, "invoke")
        val (index2, desc) = popLastString(instructions, index1, "invoke")
        val (index3, name) = popLastString(instructions, index2, "invoke")
        val (index4, owner) = popLastString(instructions, index3, "invoke")
        val (_, opcode) = popLastOpcode(instructions, index4, "invoke")

        instructions.set(instr, MethodInsnNode(opcode.opcode, owner, name, desc, itf))
    }

    /**
     * Обрабатывает динамические вызовы методов (invokedynamic).
     *
     * @param instructions список инструкций метода
     * @param instr инструкция вызова indy
     * @param index индекс инструкции в списке
     */
    private fun processInvokeDynamic(instructions: InsnList, instr: MethodInsnNode, index: Int) {
        val (index1, handleArgs) = popLastIndyHandleArgs(instructions, index, "indy")
        val (index2, handle) = popLastIndyHandle(instructions, index1, "indy")
        val (index3, desc) = popLastString(instructions, index2, "indy")
        val (_, name) = popLastString(instructions, index3, "indy")

        instructions.set(instr, createInvokeDynamicNode(name, desc, handle, handleArgs))
    }

    /**
     * Создает узел InvokeDynamicInsnNode на основе параметров.
     *
     * @param name имя динамического метода
     * @param desc дескриптор метода
     * @param handle обработчик метода (bootstrap method)
     * @param handleArgs аргументы для обработчика
     * @return узел инструкции invokedynamic
     */
    private fun createInvokeDynamicNode(
        name: String,
        desc: String,
        handle: IndyHandle,
        handleArgs: Array<IndyHandleArg>
    ): InvokeDynamicInsnNode {
        val asmHandle = Handle(handle.tag.opcode, handle.owner, handle.name, handle.desc, handle.itf)
        val asmArgs = handleArgs.map { it.toAsmType() }.toTypedArray()

        return InvokeDynamicInsnNode(name, desc, asmHandle, *asmArgs)
    }

    /**
     * Обрабатывает специальные случаи вызовов (аргументы и завершающие операции).
     * Удаляет инструкции, начинающиеся с "arg" или "end".
     *
     * @param instructions список инструкций метода
     * @param instr инструкция для обработки
     */
    private fun processSpecialCases(instructions: InsnList, instr: MethodInsnNode) {
        if (instr.name.startsWith("arg") || instr.name.startsWith("end")) {
            instructions.remove(instr)
        }
    }

    // region Обработка аргументов динамических методов

    /**
     * Извлекает массив аргументов для обработчика динамического метода.
     *
     * @param list список инструкций
     * @param start начальный индекс для поиска
     * @param insertion имя вставки для сообщений об ошибках
     * @return пара (новый индекс, массив аргументов)
     */
    private fun popLastIndyHandleArgs(list: InsnList, start: Int, insertion: String): Pair<Int, Array<IndyHandleArg>> {
        val (index, _) = popLast(list, start, insertion) { it.opcode == Opcodes.ANEWARRAY }
        val (index1, size) = popLastInt(list, index, insertion)

        return index1 to Array(size) { processHandleArgElement(list, index1, insertion, it) }
    }

    /**
     * Обрабатывает один элемент массива аргументов динамического метода.
     *
     * @param list список инструкций
     * @param start начальный индекс для поиска
     * @param insertion имя вставки для сообщений об ошибках
     * @param index индекс элемента в массиве
     * @return объект аргумента динамического метода
     */
    private fun processHandleArgElement(list: InsnList, start: Int, insertion: String, index: Int): IndyHandleArg {
        skipNopDupLdc(list, start)
        val type = list[start] as? TypeInsnNode
            ?: throw RuntimeException("Неверный тип аргумента при вставке '$insertion'")

        list.remove(type)
        list.remove(list[start]) // Удаление DUP
        skipNopDup(list, start)

        return createHandleArg(type.desc, list, start, insertion)
    }

    /**
     * Создает объект аргумента динамического метода на основе его типа.
     *
     * @param desc дескриптор типа аргумента
     * @param list список инструкций
     * @param start начальный индекс для поиска
     * @param insertion имя вставки для сообщений об ошибках
     * @return объект аргумента соответствующего типа
     */
    private fun createHandleArg(desc: String, list: InsnList, start: Int, insertion: String): IndyHandleArg {
        return when (desc) {
            $$"$$INDY_HANDLE_ARG_CLASS$Int" -> IndyHandleArg.Int(popInt(list, start, insertion).second)
            $$"$$INDY_HANDLE_ARG_CLASS$Long" -> IndyHandleArg.Long(popLong(list, start, insertion).second)
            $$"$$INDY_HANDLE_ARG_CLASS$Float" -> IndyHandleArg.Float(popFloat(list, start, insertion).second)
            $$"$$INDY_HANDLE_ARG_CLASS$Double" -> IndyHandleArg.Double(popDouble(list, start, insertion).second)
            $$"$$INDY_HANDLE_ARG_CLASS$String" -> IndyHandleArg.String(popString(list, start, insertion).second)
            $$"$$INDY_HANDLE_ARG_CLASS$Type" -> IndyHandleArg.Type(popString(list, start, insertion).second)
            $$"$$INDY_HANDLE_ARG_CLASS$Handle" -> IndyHandleArg.Handle(popIndyHandle(list, start, insertion).second)
            else -> throw RuntimeException("Неизвестный тип аргумента '$desc' при вставке '$insertion'")
        }.also {
            list.remove(list[start]) // Удаление INVOKESPECIAL <init>
            list.remove(list[start]) // Удаление AASTORE
        }
    }
    // endregion

    // region Извлечение значений из байткода

    /**
     * Извлекает обработчик динамического метода, ища в обратном направлении.
     *
     * @param list список инструкций
     * @param start начальный индекс для поиска
     * @param insertion имя вставки для сообщений об ошибках
     * @return пара (новый индекс, объект обработчика)
     */
    private fun popLastIndyHandle(list: InsnList, start: Int, insertion: String): Pair<Int, IndyHandle> {
        var i = start
        while (i > 0) {
            val element = list[i]
            if (isIndyHandleConstructor(element)) {
                list.remove(element)
                return extractHandleData(list, i, insertion)
            }
            i--
        }
        throw RuntimeException("Обработчик не найден для вставки '$insertion'")
    }

    /**
     * Извлекает обработчик динамического метода, ища в прямом направлении.
     *
     * @param list список инструкций
     * @param start начальный индекс для поиска
     * @param insertion имя вставки для сообщений об ошибках
     * @return пара (новый индекс, объект обработчика)
     */
    private fun popIndyHandle(list: InsnList, start: Int, insertion: String): Pair<Int, IndyHandle> {
        for (i in start until list.size()) {
            val element = list[i]
            if (element is TypeInsnNode && element.desc == INDY_HANDLE_CLASS) {
                list.remove(element)
                list.remove(list[i])
                continue
            }
            if (isIndyHandleConstructor(element)) {
                list.remove(element)
                return extractHandleData(list, i, insertion)
            }
        }
        throw RuntimeException("Обработчик не найден для вставки '$insertion'")
    }

    /**
     * Проверяет, является ли инструкция вызовом конструктора IndyHandle.
     *
     * @param instr проверяемая инструкция
     * @return true если это конструктор IndyHandle, иначе false
     */
    private fun isIndyHandleConstructor(instr: AbstractInsnNode): Boolean =
        instr is MethodInsnNode && instr.owner == INDY_HANDLE_CLASS && instr.name == "<init>"

    /**
     * Извлекает данные обработчика динамического метода из инструкций.
     *
     * @param list список инструкций
     * @param index текущий индекс в списке
     * @param insertion имя вставки для сообщений об ошибках
     * @return пара (новый индекс, объект обработчика)
     */
    private fun extractHandleData(list: InsnList, index: Int, insertion: String): Pair<Int, IndyHandle> {
        val (i1, itf) = popLastBoolean(list, index, insertion)
        val (i2, desc) = popLastString(list, i1, insertion)
        val (i3, name) = popLastString(list, i2, insertion)
        val (i4, owner) = popLastString(list, i3, insertion)
        val (i5, tag) = popLastOpcode(list, i4, insertion)

        return i5 to IndyHandle(tag, owner, name, desc, itf)
    }

    /**
     * Извлекает опкод из статического поля, ища в обратном направлении.
     *
     * @param list список инструкций
     * @param start начальный индекс для поиска
     * @param insertion имя вставки для сообщений об ошибках
     * @return пара (новый индекс, объект опкода)
     */
    private fun popLastOpcode(list: InsnList, start: Int, insertion: String): Pair<Int, Opcode> {
        val (index, instr) = popLast(list, start, insertion) {
            it is FieldInsnNode && it.owner == "ru/DmN/bul/Opcode"
        }
        return index to Opcode.valueOf((instr as FieldInsnNode).name)
    }

    // Методы для извлечения различных типов констант...

    /**
     * Извлекает строковое значение, ища в обратном направлении.
     */
    private fun popLastString(list: InsnList, start: Int, insertion: String): Pair<Int, String> =
        extractConstantBackwards(list, start, insertion) { it is LdcInsnNode && it.cst is String }
            .let { (index, node) -> index to (node as LdcInsnNode).cst as String }

    /**
     * Извлекает строковое значение, ища в прямом направлении.
     */
    private fun popString(list: InsnList, start: Int, insertion: String): Pair<Int, String> =
        extractConstant(list, start, insertion) { it is LdcInsnNode && it.cst is String }
            .let { (index, node) -> index to (node as LdcInsnNode).cst as String }

    /**
     * Извлекает значение double, ища в прямом направлении.
     */
    private fun popDouble(list: InsnList, start: Int, insertion: String): Pair<Int, Double> =
        extractConstant(list, start, insertion, ::isDoubleConstant).let { (index, node) ->
            index to when (node.opcode) {
                Opcodes.DCONST_0 -> 0.0
                Opcodes.DCONST_1 -> 1.0
                else -> (node as LdcInsnNode).cst as Double
            }
        }

    /**
     * Извлекает значение float, ища в прямом направлении.
     */
    private fun popFloat(list: InsnList, start: Int, insertion: String): Pair<Int, Float> =
        extractConstant(list, start, insertion, ::isFloatConstant).let { (index, node) ->
            index to when (node.opcode) {
                Opcodes.FCONST_0 -> 0f
                Opcodes.FCONST_1 -> 1f
                Opcodes.FCONST_2 -> 2f
                else -> (node as LdcInsnNode).cst as Float
            }
        }

    /**
     * Извлекает значение long, ища в прямом направлении.
     */
    private fun popLong(list: InsnList, start: Int, insertion: String): Pair<Int, Long> =
        extractConstant(list, start, insertion, ::isLongConstant).let { (index, node) ->
            index to when (node.opcode) {
                Opcodes.LCONST_0 -> 0L
                Opcodes.LCONST_1 -> 1L
                else -> (node as LdcInsnNode).cst as Long
            }
        }

    /**
     * Извлекает целочисленное значение, ища в обратном направлении.
     */
    private fun popLastInt(list: InsnList, start: Int, insertion: String): Pair<Int, Int> =
        extractConstantBackwards(list, start, insertion, ::isIntConstant).let { (index, node) ->
            index to getIntValue(node)
        }

    /**
     * Извлекает целочисленное значение, ища в прямом направлении.
     */
    private fun popInt(list: InsnList, start: Int, insertion: String): Pair<Int, Int> =
        extractConstant(list, start, insertion, ::isIntConstant).let { (index, node) ->
            index to getIntValue(node)
        }

    /**
     * Получает целочисленное значение из инструкции.
     *
     * @param node инструкция, содержащая целочисленное значение
     * @return извлеченное значение
     */
    private fun getIntValue(node: AbstractInsnNode): Int = when (node.opcode) {
        Opcodes.ICONST_0 -> 0
        Opcodes.ICONST_1 -> 1
        Opcodes.ICONST_2 -> 2
        Opcodes.ICONST_3 -> 3
        Opcodes.ICONST_4 -> 4
        Opcodes.ICONST_5 -> 5
        Opcodes.BIPUSH, Opcodes.SIPUSH -> (node as IntInsnNode).operand
        else -> (node as LdcInsnNode).cst as Int
    }

    /**
     * Извлекает логическое значение, ища в обратном направлении.
     */
    private fun popLastBoolean(list: InsnList, start: Int, insertion: String): Pair<Int, Boolean> =
        extractConstantBackwards(list, start, insertion, ::isBooleanConstant).let { (index, node) ->
            index to when (node) {
                is InsnNode if node.opcode == Opcodes.ICONST_1 -> true
                is LdcInsnNode if node.cst == true -> true
                else -> false
            }
        }

    /**
     * Универсальный метод для извлечения константы в прямом направлении.
     */
    private inline fun extractConstant(
        list: InsnList,
        start: Int,
        insertion: String,
        condition: (AbstractInsnNode) -> Boolean
    ): Pair<Int, AbstractInsnNode> = pop(list, start, insertion, condition)

    /**
     * Универсальный метод для извлечения константы в обратном направлении.
     */
    private inline fun extractConstantBackwards(
        list: InsnList,
        start: Int,
        insertion: String,
        condition: (AbstractInsnNode) -> Boolean
    ): Pair<Int, AbstractInsnNode> = popLast(list, start, insertion, condition)
    // endregion

    // region Вспомогательные функции

    /**
     * Извлекает инструкцию, удовлетворяющую условию, ища в обратном направлении.
     *
     * @param list список инструкций
     * @param start начальный индекс для поиска
     * @param insertion имя вставки для сообщений об ошибках
     * @param condition условие для поиска инструкции
     * @return пара (индекс найденной инструкции, сама инструкция)
     */
    private inline fun popLast(
        list: InsnList,
        start: Int,
        insertion: String,
        condition: (AbstractInsnNode) -> Boolean
    ): Pair<Int, AbstractInsnNode> {
        for (i in start downTo 0) {
            val element = list[i]
            if (condition(element)) {
                list.remove(element)
                return i to element
            }
        }
        throw RuntimeException("Аргумент не найден для вставки '$insertion'")
    }

    /**
     * Извлекает инструкцию, удовлетворяющую условию, ища в прямом направлении.
     *
     * @param list список инструкций
     * @param start начальный индекс для поиска
     * @param insertion имя вставки для сообщений об ошибках
     * @param condition условие для поиска инструкции
     * @return пара (индекс найденной инструкции, сама инструкция)
     */
    private inline fun pop(
        list: InsnList,
        start: Int,
        insertion: String,
        condition: (AbstractInsnNode) -> Boolean
    ): Pair<Int, AbstractInsnNode> {
        for (i in start until list.size()) {
            val element = list[i]
            if (condition(element)) {
                list.remove(element)
                return i to element
            }
        }
        throw RuntimeException("Аргумент не найден для вставки '$insertion'")
    }

    /**
     * Пропускает инструкции NOP, DUP и загрузки констант при очистке.
     *
     * @param list список инструкций
     * @param index текущий индекс
     */
    private fun skipNopDupLdc(list: InsnList, index: Int) {
        while (true) {
            val instr = list[index]
            when {
                instr.opcode in INSTRUCTION_CLEANUP_OPS -> list.remove(instr)
                isIntConstant(instr) -> list.remove(instr)
                instr.opcode == Opcodes.LDC -> list.remove(instr)
                else -> break
            }
        }
    }

    /**
     * Пропускает инструкции NOP и DUP при очистке.
     *
     * @param list список инструкций
     * @param index текущий индекс
     */
    private fun skipNopDup(list: InsnList, index: Int) {
        while (true) {
            val instr = list[index]
            if (instr.opcode in INSTRUCTION_CLEANUP_OPS) {
                list.remove(instr)
            } else break
        }
    }

    /**
     * Безопасно обходит список инструкций с возможностью перезапуска.
     *
     * Используется для обработки инструкций, когда изменения в списке
     * могут потребовать перепроверки с начала.
     *
     * @param list список инструкций
     * @param block функция обработки каждой инструкции
     */
    private inline fun safeCycle(list: InsnList, block: (value: AbstractInsnNode, index: Int) -> Boolean) {
        var i = 0
        while (i < list.size()) {
            if (block(list[i], i)) i = 0 else i++
        }
    }

    // Функции проверки типов констант...

    /**
     * Проверяет, является ли инструкция загрузкой константы double.
     */
    private fun isDoubleConstant(instr: AbstractInsnNode): Boolean = when (instr.opcode) {
        Opcodes.DCONST_0, Opcodes.DCONST_1 -> true
        else -> instr is LdcInsnNode && instr.cst is Double
    }

    /**
     * Проверяет, является ли инструкция загрузкой константы float.
     */
    private fun isFloatConstant(instr: AbstractInsnNode): Boolean = when (instr.opcode) {
        Opcodes.FCONST_0, Opcodes.FCONST_1, Opcodes.FCONST_2 -> true
        else -> instr is LdcInsnNode && instr.cst is Float
    }

    /**
     * Проверяет, является ли инструкция загрузкой константы long.
     */
    private fun isLongConstant(instr: AbstractInsnNode): Boolean = when (instr.opcode) {
        Opcodes.LCONST_0, Opcodes.LCONST_1 -> true
        else -> instr is LdcInsnNode && instr.cst is Long
    }

    /**
     * Проверяет, является ли инструкция загрузкой целочисленной константы.
     */
    private fun isIntConstant(instr: AbstractInsnNode): Boolean = when (instr.opcode) {
        in CONSTANT_INT_OPS, Opcodes.BIPUSH, Opcodes.SIPUSH -> true
        else -> instr is LdcInsnNode && instr.cst is Int
    }

    /**
     * Проверяет, является ли инструкция загрузкой логической константы.
     */
    private fun isBooleanConstant(instr: AbstractInsnNode): Boolean = when (instr) {
        is InsnNode if (instr.opcode == Opcodes.ICONST_0 || instr.opcode == Opcodes.ICONST_1) -> true
        is LdcInsnNode if instr.cst is Boolean -> true
        else -> false
    }
    // endregion
}

/**
 * Расширение для преобразования IndyHandleArg в соответствующий тип ASM.
 */
private fun IndyHandleArg.toAsmType(): Any = when (this) {
    is IndyHandleArg.Int -> value
    is IndyHandleArg.Long -> value
    is IndyHandleArg.Float -> value
    is IndyHandleArg.Double -> value
    is IndyHandleArg.String -> value
    is IndyHandleArg.Type -> Type.getType(value)
    is IndyHandleArg.Handle -> Handle(
        value.tag.opcode,
        value.owner,
        value.name,
        value.desc,
        value.itf
    )
    else -> throw AssertionError("Недопустимый тип аргумента")
}