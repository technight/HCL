package exceptions

/**
 * Class used to log type errors.
 * @param operator The function in which error occurred
 * @param operands A list of types that are non-compatible
 */
class TypeError(
    lineNumber: Int,
    fileName: String,
    lineIndex: Int,
    lineText: String,
    private val operator: String,
    private val operands: List<String>
)
                : ParserException(lineNumber, fileName, lineIndex, lineText) {
    private val types: String = findTypes()

    override val errorMessage = "Function '$operator' not defined for types: $types."
    override val helpText = "Try casting your types to match eachother."

    private fun findTypes() = when (operands.size) {
        0 -> "No type"
        1 -> "'${operands.first()}'"
        else -> "${operands.dropLast(1).joinToString { "'$it'" }} and '${operands.last()}'"
    }
}
