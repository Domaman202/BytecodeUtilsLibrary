package ru.DmN.bul

abstract class IndyHandleArg {
    class Int(val value: kotlin.Int) : IndyHandleArg()
    class Long(val value: kotlin.Long) : IndyHandleArg()
    class Float(val value: kotlin.Float) : IndyHandleArg()
    class Double(val value: kotlin.Double) : IndyHandleArg()
    class String(val value: kotlin.String) : IndyHandleArg()
    class Type(val value: kotlin.String) : IndyHandleArg()
    class Handle(val value: IndyHandle) : IndyHandleArg()
}