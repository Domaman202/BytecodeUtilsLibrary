package ru.DmN.bul

object OpcodeInsertion {
    @JvmStatic
    fun alloc(clazz: String): OpcodeInsertion = throw Error()
    fun init(clazz: String, desc: String): OpcodeInsertion = throw Error()

    @JvmStatic
    fun putStatic(value: Any?, owner: String, name: String, desc: String): Unit = throw Error()
    @JvmStatic
    fun putStatic(value: Double, owner: String, name: String, desc: String): Unit = throw Error()
    @JvmStatic
    fun putStatic(value: Float, owner: String, name: String, desc: String): Unit = throw Error()
    @JvmStatic
    fun putStatic(value: Long, owner: String, name: String, desc: String): Unit = throw Error()
    @JvmStatic
    fun putStatic(value: Int, owner: String, name: String, desc: String): Unit = throw Error()
    @JvmStatic
    fun putStatic(value: Char, owner: String, name: String, desc: String): Unit = throw Error()
    @JvmStatic
    fun putStatic(value: Short, owner: String, name: String, desc: String): Unit = throw Error()
    @JvmStatic
    fun putStatic(value: Byte, owner: String, name: String, desc: String): Unit = throw Error()
    @JvmStatic
    fun putStatic(value: Boolean, owner: String, name: String, desc: String): Unit = throw Error()
    @JvmStatic
    fun getStatic(owner: String, name: String, desc: String): OpcodeInsertion = throw Error()

    @JvmStatic
    fun putField(instance: Any?, value: Any?, owner: String, name: String, desc: String): Unit = throw Error()
    @JvmStatic
    fun putField(instance: Any?, value: Double, owner: String, name: String, desc: String): Unit = throw Error()
    @JvmStatic
    fun putField(instance: Any?, value: Float, owner: String, name: String, desc: String): Unit = throw Error()
    @JvmStatic
    fun putField(instance: Any?, value: Long, owner: String, name: String, desc: String): Unit = throw Error()
    @JvmStatic
    fun putField(instance: Any?, value: Int, owner: String, name: String, desc: String): Unit = throw Error()
    @JvmStatic
    fun putField(instance: Any?, value: Char, owner: String, name: String, desc: String): Unit = throw Error()
    @JvmStatic
    fun putField(instance: Any?, value: Short, owner: String, name: String, desc: String): Unit = throw Error()
    @JvmStatic
    fun putField(instance: Any?, value: Byte, owner: String, name: String, desc: String): Unit = throw Error()
    @JvmStatic
    fun putField(instance: Any?, value: Boolean, owner: String, name: String, desc: String): Unit = throw Error()
    @JvmStatic
    fun getField(instance: Any?, owner: String, name: String, desc: String): OpcodeInsertion = throw Error()

    @JvmStatic
    fun invoke(opcode: Opcode, owner: String, name: String, desc: String, itf: Boolean): OpcodeInsertion = throw Error()
    @JvmStatic
    fun indy(name: String, desc: String, handle: IndyHandle, vararg args: IndyHandleArg): OpcodeInsertion = throw Error()

    fun argA(value: Any?): OpcodeInsertion = throw Error()
    fun argD(value: Double): OpcodeInsertion = throw Error()
    fun argF(value: Float): OpcodeInsertion = throw Error()
    fun argL(value: Long): OpcodeInsertion = throw Error()
    fun argI(value: Int): OpcodeInsertion = throw Error()
    fun argC(value: Char): OpcodeInsertion = throw Error()
    fun argS(value: Short): OpcodeInsertion = throw Error()
    fun argB(value: Byte): OpcodeInsertion = throw Error()
    fun argZ(value: Boolean): OpcodeInsertion = throw Error()

    fun <T> endA(): T = throw Error()
    fun endD(): Double = throw Error()
    fun endF(): Float = throw Error()
    fun endL(): Long = throw Error()
    fun endI(): Int = throw Error()
    fun endC(): Char = throw Error()
    fun endS(): Short = throw Error()
    fun endB(): Byte = throw Error()
    fun endZ(): Boolean = throw Error()
    fun end(): Unit = throw Error()
}