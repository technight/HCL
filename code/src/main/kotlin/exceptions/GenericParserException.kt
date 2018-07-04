package exceptions
/**
 * Exception thrown when trying to re-declare a identifier that is already defined with same signature
 */
class GenericParserException(
    lineNumber: Int,
    fileName: String,
    lineIndex: Int,
    lineText: String,
    override val errorMessage: String,
    override val helpText: String = ""
)
    : ParserException(lineNumber, fileName, lineIndex, lineText)
