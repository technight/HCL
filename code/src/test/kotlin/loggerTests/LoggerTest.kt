package loggerTests
import exceptions.*
import logger.TestLogger
import org.junit.jupiter.api.Assertions.assertEquals

class TokenTest {

    @org.junit.jupiter.api.Test
    fun testTypeError() {
        val error = TypeError(4, "test.hcl", 10, "var b = 4 < \"5\"", "<", listOf("num", "txt", "bool"))
        val logger = TestLogger()
        logger.logCompilationError(error)
        assertEquals(
                "\n- ERROR: TypeError found at test.hcl line 4 index 10:\n" +
                        " | var b = 4 < \"5\"\n" +
                        " |           ^--\n" +
                        " | Function '<' not defined for types: 'num', 'txt' and 'bool'.\n" +
                        " | -->help: Try casting your types to match eachother.\n\n", logger.buffer.toString())
    }
    @org.junit.jupiter.api.Test
    fun testTypeErrorSingleType() {
        val error = TypeError(6, "test.hcl", 12, "\"notText\" print", "print", listOf("num"))
        val logger = TestLogger()
        logger.logCompilationError(error)
        assertEquals(
                "\n- ERROR: TypeError found at test.hcl line 6 index 12:\n" +
                        " | \"notText\" print\n" +
                        " |             ^--\n" +
                        " | Function 'print' not defined for types: 'num'.\n" +
                        " | -->help: Try casting your types to match eachother.\n\n", logger.buffer.toString())
    }
    @org.junit.jupiter.api.Test
    fun testTypeErrorNoTypes() {
        val error = TypeError(6, "test.hcl", 0, "print", "print", listOf())
        val logger = TestLogger()
        logger.logCompilationError(error)
        assertEquals(
                "\n- ERROR: TypeError found at test.hcl line 6 index 0:\n" +
                        " | print\n" +
                        " | ^--\n" +
                        " | Function 'print' not defined for types: No type.\n" +
                        " | -->help: Try casting your types to match eachother.\n\n", logger.buffer.toString())
    }

    @org.junit.jupiter.api.Test
    fun testUndeclaredError() {
        val error = UndeclaredError(12, "test.hcl", 8, "txt s = textString", "textString")
        val logger = TestLogger()
        logger.logCompilationError(error)
        assertEquals(
                "\n- ERROR: UndeclaredError found at test.hcl line 12 index 8:\n" +
                        " | txt s = textString\n" +
                        " |         ^--\n" +
                        " | Undeclared identifier 'textString' found.\n" +
                        " | -->help: Declare identifier before use.\n\n", logger.buffer.toString())
    }

    @org.junit.jupiter.api.Test
    fun testUnitializedError() {
        val error = UninitializedError(12, "test.hcl", 4, "x = someUninitializedVariable + 10", "someUninitializedVariable")
        val logger = TestLogger()
        logger.logCompilationError(error)
        assertEquals(
                "\n- ERROR: UninitializedError found at test.hcl line 12 index 4:\n" +
                        " | x = someUninitializedVariable + 10\n" +
                        " |     ^--\n" +
                        " | Use of uninitialized variable 'someUninitializedVariable' found.\n" +
                        " | -->help: Try initializing variable before use.\n\n", logger.buffer.toString())
    }

    @org.junit.jupiter.api.Test
    fun testZeroDivisionError() {
        val error = ZeroDivisionError(7, "test.hcl", 6, "x = 42/0")
        val logger = TestLogger()
        logger.logCompilationError(error)
        assertEquals(
                "\n- ERROR: ZeroDivisionError found at test.hcl line 7 index 6:\n" +
                        " | x = 42/0\n" +
                        " |       ^--\n" +
                        " | Division by 0 found\n" +
                        " | -->help: Check your variables\n\n", logger.buffer.toString())
    }
    @org.junit.jupiter.api.Test
    fun testMissingArgumentError() {
        val error = MissingArgumentError(11, "test.hcl", 2, "2 someFunction", "someFunction")
        val logger = TestLogger()
        logger.logCompilationError(error)
        assertEquals(
                "\n- ERROR: MissingArgumentError found at test.hcl line 11 index 2:\n" +
                        " | 2 someFunction\n" +
                        " |   ^--\n" +
                        " | Missing argument for function 'someFunction'.\n" +
                        " | -->help: Have you included all arguments for function?\n\n", logger.buffer.toString())
    }
    @org.junit.jupiter.api.Test
    fun testMissingEncapsulation() {
        val error = MissingEncapsulationError(30, "test.hcl", 0, "({()}")
        val logger = TestLogger()
        logger.logCompilationError(error)
        assertEquals(
                "\n- ERROR: MissingEncapsulationError found at test.hcl line 30 index 0:\n" +
                        " | ({()}\n" +
                        " | ^--\n" +
                        " | Missing closing character for '('\n" +
                        " | -->help: Remember to always close encapsulations.\n\n", logger.buffer.toString())
    }

    @org.junit.jupiter.api.Test
    fun testInitializedFunctionParameterError() {
        val error = InitializedFunctionParameterError(0, "test.hcl", 15, "var x = (num z = 5): none {}")
        val logger = TestLogger()
        logger.logCompilationError(error)
        assertEquals(
                "\n- ERROR: InitializedFunctionParameterError found at test.hcl line 0 index 15:\n" +
                        " | var x = (num z = 5): none {}\n" +
                        " |                ^--\n" +
                        " | Cannot initialize function arguments in declaration.\n" +
                        " | -->help: Function arguments are initialized when the function is called.\n\n",
                logger.buffer.toString())
    }

    @org.junit.jupiter.api.Test
    fun testNoneAsInputError() {
        val error = NoneAsInputError(0, "test.hcl", 9, "var x = (none z): none {}")
        val logger = TestLogger()
        logger.logCompilationError(error)
        assertEquals(
                "\n- ERROR: NoneAsInputError found at test.hcl line 0 index 9:\n" +
                        " | var x = (none z): none {}\n" +
                        " |          ^--\n" +
                        " | Functions can not have input-parameter of type 'none'.\n" +
                        " | -->help: 'none' can only be used as a functions return-type.\n\n",
                logger.buffer.toString())
    }
    @org.junit.jupiter.api.Test
    fun testUnexpectedTypeError() {
        val error = UnexpectedTypeError(50, "test.hcl", 8, "num x = \"five\"", "num",
                "txt")
        val logger = TestLogger()
        logger.logCompilationError(error)
        assertEquals(
                "\n- ERROR: UnexpectedTypeError found at test.hcl line 50 index 8:\n" +
                        " | num x = \"five\"\n" +
                        " |         ^--\n" +
                        " | Cannot implicitly cast type 'txt' to type 'num'.\n" +
                        " | -->help: Try casting your types to match eachother.\n\n",
                logger.buffer.toString())
    }
}
