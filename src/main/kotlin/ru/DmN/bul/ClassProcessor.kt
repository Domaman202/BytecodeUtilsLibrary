package ru.DmN.bul

import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.TypeInsnNode

class ClassProcessor : ClassNode(Opcodes.ASM9) {
    override fun visitEnd() {
        if (this.invisibleAnnotations?.any { it.desc == "Lru/DmN/bul/BytecodeProcessor;" } == true) {
            this.methods.forEach { method ->
                safeCycle(method.instructions) { instr, index ->
                    if (instr is MethodInsnNode && instr.owner == "ru/DmN/bul/OpcodeInsertion") {
                        when (instr.name) {
                            "alloc" -> {
                                method.instructions.insertBefore(instr, TypeInsnNode(Opcodes.NEW, popLastString(method.instructions, index, "alloc").second))
                                method.instructions.set(instr, InsnNode(Opcodes.DUP))
                            }
                            "init" -> {
                                val (index1, desc) = popLastString(method.instructions, index, "init")
                                val (_, clazz) = popLastString(method.instructions, index1, "init")
                                method.instructions.set(instr, MethodInsnNode(Opcodes.INVOKESPECIAL, clazz, "<init>", desc, false))
                            }
                            "putStatic",
                            "getStatic",
                            "putField",
                            "getField" -> {
                                val (index1, desc) = popLastString(method.instructions, index, instr.name)
                                val (index2, name) = popLastString(method.instructions, index1, instr.name)
                                val (_, owner) = popLastString(method.instructions, index2, instr.name)
                                method.instructions.set(
                                    instr,
                                    FieldInsnNode(
                                        when (instr.name) {
                                            "putStatic" -> Opcodes.PUTSTATIC
                                            "getStatic" -> Opcodes.GETSTATIC
                                            "putField" -> Opcodes.PUTFIELD
                                            "getField" -> Opcodes.GETFIELD
                                            else -> throw Error("Unreachable")
                                        },
                                        owner,
                                        name,
                                        desc
                                    )
                                )
                            }
                            "invoke" -> {
                                val (index1, itf) = popLastBoolean(method.instructions, index, "invoke")
                                val (index2, desc) = popLastString(method.instructions, index1, "invoke")
                                val (index3, name) = popLastString(method.instructions, index2, "invoke")
                                val (index4, owner) = popLastString(method.instructions, index3, "invoke")
                                val (_, opcode) = popLastOpcode(method.instructions, index4, "invoke")
                                method.instructions.set(instr, MethodInsnNode(opcode.opcode, owner, name, desc, itf))
                            }
                            "indy" -> {
                                val (index1, handleArgs) = popLastIndyHandleArgs(method.instructions, index, "indy")
                                val (index2, handle) = popLastIndyHandle(method.instructions, index1, "indy")
                                val (index3, desc) = popLastString(method.instructions, index2, "indy")
                                val (_, name) = popLastString(method.instructions, index3, "indy")
                                method.instructions.set(
                                    instr,
                                    InvokeDynamicInsnNode(
                                        name,
                                        desc,
                                        Handle(handle.tag.opcode, handle.owner, handle.name, handle.desc, handle.itf),
                                        *handleArgs.map {
                                            when (it) {
                                                is IndyHandleArg.Int ->  it.value
                                                is IndyHandleArg.Long -> it.value
                                                is IndyHandleArg.Float -> it.value
                                                is IndyHandleArg.Double -> it.value
                                                is IndyHandleArg.String -> it.value
                                                is IndyHandleArg.Type -> Type.getType(it.value)
                                                is IndyHandleArg.Handle -> Handle(it.value.tag.opcode, it.value.owner, it.value.name, it.value.desc, it.value.itf)
                                                else -> throw Error("Unreachable")
                                            }
                                        }.toTypedArray()
                                    )
                                )
                            }
                            else -> {
                                if (instr.name.startsWith("arg") || instr.name.startsWith("end"))
                                    method.instructions.remove(instr)
                                else return@safeCycle false
                            }
                        }
                        true
                    } else false
                }
            }
        }
        super.visitEnd()
    }

    private fun popLastIndyHandleArgs(list: InsnList, start: Int, insertion: String): Pair<Int, Array<IndyHandleArg>> {
        val (index, _) = popLast(list, start, insertion) { it.opcode == Opcodes.ANEWARRAY }
        val (index1, size) = popLastInt(list, index, insertion)
        val array = arrayOfNulls<IndyHandleArg>(size)
        var i = 0
        var index2 = index1
        while (i < size) {
            skipNopDupLdc(list, index2)
            val type = list[index2]
            if (type !is TypeInsnNode)
                throw RuntimeException("Получен неверный тип аргумента '$type'. При вставке '$insertion'")
            list.remove(type) // new ru.DmN.bul.IndyHandleArg$X
            list.remove(list[index2]) // DUP
            skipNopDup(list, index2)
            when (type.desc) {
                $$"ru/DmN/bul/IndyHandleArg$Int" -> {
                    val value = popInt(list, index2, insertion)
                    index2 = value.first
                    array[i] = IndyHandleArg.Int(value.second)
                }
                $$"ru/DmN/bul/IndyHandleArg$Long" -> {
                    val value = popLong(list, index2, insertion)
                    index2 = value.first
                    array[i] = IndyHandleArg.Long(value.second)
                }
                $$"ru/DmN/bul/IndyHandleArg$Float" -> {
                    val value = popFloat(list, index2, insertion)
                    index2 = value.first
                    array[i] = IndyHandleArg.Float(value.second)
                }
                $$"ru/DmN/bul/IndyHandleArg$Double" -> {
                    val value = popDouble(list, index2, insertion)
                    index2 = value.first
                    array[i] = IndyHandleArg.Double(value.second)
                }
                $$"ru/DmN/bul/IndyHandleArg$String" -> {
                    val value = popString(list, index2, insertion)
                    index2 = value.first
                    array[i] = IndyHandleArg.String(value.second)
                }
                $$"ru/DmN/bul/IndyHandleArg$Type" -> {
                    val value = popString(list, index2, insertion)
                    index2 = value.first
                    array[i] = IndyHandleArg.Type(value.second)
                }
                $$"ru/DmN/bul/IndyHandleArg$Handle" -> {
                    val value = popIndyHandle(list, index2, insertion)
                    index2 = value.first
                    array[i] = IndyHandleArg.Handle(value.second)
                }
                else -> throw RuntimeException("Получен неверный тип аргумента '${type.desc}'. При вставке '$insertion'")
            }
            list.remove(list[index2]) // INVOKESPECIAL <init>
            list.remove(list[index2]) // AASTORE
            i++
        }
        return Pair(index2, array as Array<IndyHandleArg>)
    }

    private fun popLastIndyHandle(list: InsnList, start: Int, insertion: String): Pair<Int, IndyHandle> {
        var i = start
        while (i > 0) {
            val element = list[i]
            if (element is MethodInsnNode && element.owner == "ru/DmN/bul/IndyHandle" && element.name == "<init>") {
                list.remove(element) // <init>
                val (i1, itf) = popLastBoolean(list, i, insertion)
                val (i2, desc) = popLastString(list, i1, insertion)
                val (i3, name) = popLastString(list, i2, insertion)
                val (i4, owner) = popLastString(list, i3, insertion)
                val (i5, tag) = popLastOpcode(list, i4, insertion)
                while (true) {
                    val element = list[i]
                    if (element is TypeInsnNode && element.desc == "ru/DmN/bul/IndyHandle") {
                        list.remove(element) // new ru.DmN.bul.IndyHandle
                        list.remove(list[i]) // DUP
                        break
                    }
                    i--
                }
                return Pair(i5, IndyHandle(tag, owner, name, desc, itf))
            }
            i--
        }
        throw RuntimeException("Не удалось найти аргумент для вставки '$insertion'")
    }

    private fun popIndyHandle(list: InsnList, start: Int, insertion: String): Pair<Int, IndyHandle> {
        var i = start
        while (i < list.size()) {
            val element = list[i]
            if (element is TypeInsnNode && element.desc == "ru/DmN/bul/IndyHandle") {
                list.remove(element) // new ru.DmN.bul.IndyHandle
                list.remove(list[i]) // DUP
                continue
            }
            if (element is MethodInsnNode && element.owner == "ru/DmN/bul/IndyHandle" && element.name == "<init>") {
                list.remove(element) // <init>
                val (index1, itf) = popLastBoolean(list, i, insertion)
                val (index2, desc) = popLastString(list, index1, insertion)
                val (index3, name) = popLastString(list, index2, insertion)
                val (index4, owner) = popLastString(list, index3, insertion)
                val (index5, tag) = popLastOpcode(list, index4, insertion)
                list.remove(list[i]) // <init>
                return Pair(index5, IndyHandle(tag, owner, name, desc, itf))
            }
            i++
        }
        throw RuntimeException("Не удалось найти аргумент для вставки '$insertion'")
    }

    private fun popLastOpcode(list: InsnList, start: Int, insertion: String): Pair<Int, Opcode> {
        val (index, instr) = popLast(list, start, insertion) { it is FieldInsnNode && it.owner == "ru/DmN/bul/Opcode" }
        return Pair(index, Opcode.valueOf((instr as FieldInsnNode).name))
    }

    private fun popLastString(list: InsnList, start: Int, insertion: String): Pair<Int, String> {
        val (index, instr) = popLast(list, start, insertion) { it is LdcInsnNode && it.cst is String }
        return Pair(index, (instr as LdcInsnNode).cst as String)
    }

    private fun popString(list: InsnList, start: Int, insertion: String): Pair<Int, String> {
        val (index, instr) = pop(list, start, insertion) { it is LdcInsnNode && it.cst is String }
        return Pair(index, (instr as LdcInsnNode).cst as String)
    }

    private fun popDouble(list: InsnList, start: Int, insertion: String): Pair<Int, Double> {
        val (index, instr) = pop(list, start, insertion) {
            when (it.opcode) {
                Opcodes.DCONST_0,
                Opcodes.DCONST_1 -> true
                else -> it is LdcInsnNode && it.cst is Double
            }
        }
        return Pair(
            index,
            when (instr.opcode) {
                Opcodes.DCONST_0 -> 0.0
                Opcodes.DCONST_1 -> 1.0
                else -> (instr as LdcInsnNode).cst as Double
            }
        )
    }

    private fun popFloat(list: InsnList, start: Int, insertion: String): Pair<Int, Float> {
        val (index, instr) = pop(list, start, insertion) {
            when (it.opcode) {
                Opcodes.FCONST_0,
                Opcodes.FCONST_1,
                Opcodes.FCONST_2 -> true
                else -> it is LdcInsnNode && it.cst is Float
            }
        }
        return Pair(
            index,
            when (instr.opcode) {
                Opcodes.FCONST_0 -> 0f
                Opcodes.FCONST_1 -> 1f
                Opcodes.FCONST_2 -> 2f
                else -> (instr as LdcInsnNode).cst as Float
            }
        )
    }

    private fun popLong(list: InsnList, start: Int, insertion: String): Pair<Int, Long> {
        val (index, instr) = pop(list, start, insertion) {
            when (it.opcode) {
                Opcodes.LCONST_0,
                Opcodes.LCONST_1 -> true
                else -> it is LdcInsnNode && it.cst is Long
            }
        }
        return Pair(
            index,
            when (instr.opcode) {
                Opcodes.LCONST_0 -> 0L
                Opcodes.LCONST_1 -> 1L
                else -> (instr as LdcInsnNode).cst as Long
            }
        )
    }

    private fun popLastInt(list: InsnList, start: Int, insertion: String): Pair<Int, Int> {
        val (index, instr) = popLast(list, start, insertion) {
            when (it.opcode) {
                Opcodes.ICONST_0,
                Opcodes.ICONST_1,
                Opcodes.ICONST_2,
                Opcodes.ICONST_3,
                Opcodes.ICONST_4,
                Opcodes.ICONST_5,
                Opcodes.BIPUSH,
                Opcodes.SIPUSH -> true
                else -> it is LdcInsnNode && it.cst is Int
            }
        }
        return Pair(
            index,
            when (instr.opcode) {
                Opcodes.ICONST_0 -> 0
                Opcodes.ICONST_1 -> 1
                Opcodes.ICONST_2 -> 2
                Opcodes.ICONST_3 -> 3
                Opcodes.ICONST_4 -> 4
                Opcodes.ICONST_5 -> 5
                Opcodes.BIPUSH,
                Opcodes.SIPUSH -> (instr as IntInsnNode).operand
                else -> (instr as LdcInsnNode).cst as Int
            }
        )
    }

    private fun popInt(list: InsnList, start: Int, insertion: String): Pair<Int, Int> {
        val (index, instr) = pop(list, start, insertion) {
            when (it.opcode) {
                Opcodes.ICONST_0,
                Opcodes.ICONST_1,
                Opcodes.ICONST_2,
                Opcodes.ICONST_3,
                Opcodes.ICONST_4,
                Opcodes.ICONST_5,
                Opcodes.BIPUSH,
                Opcodes.SIPUSH -> true
                else -> it is LdcInsnNode && it.cst is Int
            }
        }
        return Pair(
            index,
            when (instr.opcode) {
                Opcodes.ICONST_0 -> 0
                Opcodes.ICONST_1 -> 1
                Opcodes.ICONST_2 -> 2
                Opcodes.ICONST_3 -> 3
                Opcodes.ICONST_4 -> 4
                Opcodes.ICONST_5 -> 5
                Opcodes.BIPUSH,
                Opcodes.SIPUSH -> (instr as IntInsnNode).operand
                else -> (instr as LdcInsnNode).cst as Int
            }
        )
    }

    private fun popLastBoolean(list: InsnList, start: Int, insertion: String): Pair<Int, Boolean> {
        val (index, instr) = popLast(list, start, insertion) {
            when (it) {
                is InsnNode if (it.opcode == Opcodes.ICONST_0 || it.opcode == Opcodes.ICONST_1) -> true
                is LdcInsnNode if it.cst is Boolean -> true
                else -> false
            }
        }
        return Pair(
            index,
            when (instr) {
                is InsnNode if instr.opcode == Opcodes.ICONST_1 -> true
                is LdcInsnNode if instr.cst == true -> true
                else -> false
            }
        )
    }

    private inline fun popLast(list: InsnList, start: Int, insertion: String, condition: (AbstractInsnNode) -> Boolean): Pair<Int, AbstractInsnNode> {
        var i = start
        while (i > 0) {
            val element = list[i]
            if (condition(element)) {
                list.remove(element)
                return Pair(i, element)
            }
            i--
        }
        throw RuntimeException("Не удалось найти аргумент для вставки '$insertion'")
    }

    private inline fun pop(list: InsnList, start: Int, insertion: String, condition: (AbstractInsnNode) -> Boolean): Pair<Int, AbstractInsnNode> {
        var i = start
        while (i < list.size()) {
            val element = list[i]
            if (condition(element)) {
                list.remove(element)
                return Pair(i, element)
            }
            i++
        }
        throw RuntimeException("Не удалось найти аргумент для вставки '$insertion'")
    }

    private fun skipNopDupLdc(list: InsnList, index: Int) {
        while (true) {
            val instr = list[index]
            when (instr.opcode) {
                -1,
                Opcodes.NOP,
                Opcodes.DUP,
                Opcodes.ICONST_0,
                Opcodes.ICONST_1,
                Opcodes.ICONST_2,
                Opcodes.ICONST_3,
                Opcodes.ICONST_4,
                Opcodes.ICONST_5,
                Opcodes.BIPUSH,
                Opcodes.SIPUSH,
                Opcodes.LDC -> list.remove(instr)
                else -> break
            }
        }
    }

    private fun skipNopDup(list: InsnList, index: Int) {
        while (true) {
            val instr = list[index]
            if (instr.opcode == -1 || instr.opcode == Opcodes.NOP || instr.opcode == Opcodes.DUP)
                list.remove(instr)
            else break
        }
    }

    private inline fun safeCycle(list: InsnList, block: (value: AbstractInsnNode, index: Int) -> Boolean) {
        var i = 0
        while (i < list.size()) {
            if (block(list[i], i))
                i = 0
            else i++
        }
    }
}