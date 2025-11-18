package ru.DmN.bul

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        val loader = TestClassLoader()
        loader.loadClass("ru.DmN.bul.Test").getMethod("test").invoke(null)
    }
}