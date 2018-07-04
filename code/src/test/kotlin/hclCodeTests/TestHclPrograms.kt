package hclCodeTests

import exceptions.CompilationException
import generation.FilePair
import generation.cpp.ProgramGenerator
import hclTestFramework.codegen.compileAndExecuteCpp
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import parser.cpp.CppParser
import stdlib.Stdlib
import kotlin.system.exitProcess
import kotlin.test.assertEquals

fun generateFilesFromCode(fileName: String, code: String): List<FilePair> {
    val lexer = lexer.Lexer(mapOf(Stdlib.getStdlibContent(), fileName to code))
    val parser = CppParser(lexer)
    val logger = logger.Logger()
    val ast = try {
        parser.cppAst()
    } catch (exception: CompilationException) {
        logger.logCompilationError(exception)
        exitProcess(-1)
    }
    return ProgramGenerator().generate(ast)
}

object TestHclPrograms : Spek({
    val files = listOf(
        "mapTuples.hcl",
        "HelloWorld.hcl",
        "ReturnSimple.hcl",
        "HelloWorldAndReturn.hcl",
        "stringConcat.hcl",
        "thenElse.hcl",
        "while.hcl",
        "stringAt.hcl",
        "printTuple.hcl",
        "conclusionSnippet.hcl",
        "fizzBuzz.hcl",
        "MapFilter.hcl",
        "staticScope.hcl",
        "subText.hcl",
        "toUneven.hcl",
        "multiScope.hcl",
        "OOP.hcl",
        "OOP_V2.hcl",
        "PrintFibonacci.hcl",
        "Swap.hcl",
        "bubbleSort.hcl",
        "testFirstIndexWhereStdlib.hcl",
        "conclusionExampleNicolaj.hcl",
        "ListOutOfBounds.hcl",
        "aTupleInFunction.hcl"
    )
    files.filter { it.endsWith(".hcl") }.forEach { file ->
        given(file) {
            val fileContent = javaClass.classLoader.getResource(file).readText()
            val constraints = fileContent.split("\n").first().split("should ").drop(1).map { it.split(" ") }
            val expectedReturn = constraints.firstOrNull { it.first() == "return" }?.get(1)?.toInt() ?: 0
            val expectedPrint = constraints.firstOrNull { it.first() == "print" }?.drop(1)?.joinToString(" ") ?: ""
            if (fileContent.contains("TEST_DISABLED")) {
                it("should not be executed") {}
            } else it("should return $expectedReturn and print \"$expectedPrint\"") {
                val outputFiles = generateFilesFromCode(file, fileContent)
                val keepFiles = fileContent.contains("KEEP_FILES")
                val output = compileAndExecuteCpp(outputFiles, file.split(".").first(), keepFiles)!!
                assertEquals(expectedReturn, output.returnValue,
                    "expected RETURN_CODE=$expectedReturn. was ${output.returnValue}.\n" +
                        "full output:\n${output.string}")
                assertEquals(expectedPrint, output.string,
                    "Expected PRINT=$expectedPrint. was ${output.string}.\n")
            }
        }
    }
})
