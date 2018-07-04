import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.versionOption
import com.github.ajalt.clikt.parameters.types.file
import exceptions.CompilationException
import interpreter.kotlin.KtInterpreter
import lexer.Lexer
import logger.Logger
import parser.kotlin.KtParser
import stdlib.Stdlib
import kotlin.system.exitProcess

/**
 * Main class responsible for overall compilation of HCL
 */
class HCL : CliktCommand() {
    init {
        versionOption("Version 0.19")
    }

    private val inputFiles by argument("input_files", help = "HCL input files to be compiled")
            .file(exists = true, folderOkay = false).multiple(false)


    override fun run() {
        if (inputFiles.isNotEmpty()) {
            val lexer = Lexer(
                    mapOf(
                            Stdlib.getStdlibContent()
                    ) + inputFiles.map { it.nameWithoutExtension to it.readText() }
            )
            val parser = KtParser(lexer)
            val logger = Logger()

            try {
                exitProcess(KtInterpreter(parser).run())
            } catch (exception: CompilationException) {
                logger.logCompilationError(exception)
                exitProcess(-1)
            }
        } else {
            REPL().start()
        }
    }
}
