package ru.DmN.bul;

import java.io.PrintStream;
import java.lang.invoke.*;

@BytecodeProcessor
public class Test {
    public static CallSite bootstrap(MethodHandles.Lookup lookup, String name, MethodType type, MethodHandle handle) throws Exception {
        if (!type.equals(MethodType.methodType(void.class)))
            throw new RuntimeException("Неожиданный тип вызова: " + type);
        var printlnHandle = lookup.findVirtual(PrintStream.class, name, MethodType.methodType(void.class, String.class)).bindTo(System.out);
        var printlnWithHello = MethodHandles.insertArguments(printlnHandle, 0, String.valueOf(handle));
        return new ConstantCallSite(printlnWithHello.asType(type));
    }

    public static void test() {
        OpcodeInsertion.indy(
                "println",
                "()V",
                new IndyHandle(Opcode.H_INVOKE_STATIC, "ru/DmN/bul/Test", "bootstrap", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;)Ljava/lang/invoke/CallSite;", false),
                new IndyHandleArg.Handle(new IndyHandle(Opcode.H_INVOKE_STATIC, "ru/DmN/bul/Test", "foo", "()V", false))
        ).end();
    }

    public static void foo() {
        System.out.println("Foo!");
    }
}
