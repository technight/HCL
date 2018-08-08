package parser

import interpreter.kotlin.KotlinHclExpression
import interpreter.kotlin.KtInterpreter

/**
 * Tree structure of nodes used to build AST
 */
sealed class AstNode {
    sealed class Command : AstNode() {

        data class Declaration(
            val type: Type,
            val identifier: Expression.Value.Identifier,
            val expression: Expression? = null
        ) : Command()

        data class Assignment(val identifier: Expression.Value.Identifier, val expression: Expression) : Command()

        sealed class Expression : Command() {
            val type: Type get() = when (this) {
                is AstNode.Command.Expression.Value.Identifier -> this.innerType
                is AstNode.Command.Expression.Value.Literal.Number -> Type.Number
                is AstNode.Command.Expression.Value.Literal.Text -> Type.Text
                is AstNode.Command.Expression.Value.Literal.Bool -> Type.Bool
                is AstNode.Command.Expression.Value.Literal.Tuple -> Type.Tuple(this.elements.map { it.type })
                is AstNode.Command.Expression.Value.Literal.List -> Type.List(this.innerType)
                is AstNode.Command.Expression.LambdaExpression ->
                    Type.Func(paramDeclarations.map { it.type }, returnType)
                is AstNode.Command.Expression.LambdaBody -> {
                    val returnStatements = commands.filterIsInstance<AstNode.Command.Return>()
                    if (returnStatements.isEmpty()) AstNode.Type.None else {
                        if (returnStatements.all { returnStatements.first().expression.type == it.expression.type }) {
                            returnStatements.first().expression.type
                        } else {
                            throw Exception("Lambda body with multiple return types")
                        }
                    }
                }
                is AstNode.Command.Expression.FunctionCall -> this.identifier.innerType
            }
            sealed class Value : Expression() {
                data class Identifier(val name: String, val innerType: Type) : Value()
                sealed class Literal : Value() {
                    data class Number(val value: Double) : Literal()
                    data class Text(val value: String) : Literal()
                    data class Bool(val value: Boolean) : Literal()
                    data class Tuple(val elements: kotlin.collections.List<Expression>) : Literal()
                    data class List(val elements: kotlin.collections.List<Expression>, val innerType: Type) : Literal()
                }
            }
            data class LambdaExpression(
                val paramDeclarations: List<ParameterDeclaration>,
                val returnType: Type,
                val body: LambdaBody,
                val attributes: LambdaExpressionAttributes = DefaultLambdaAttributes
            ) : Expression()
            data class LambdaBody(val commands: List<Command>) : Expression()
            data class FunctionCall(
                val identifier: Value.Identifier,
                val arguments: List<Expression>,
                val expectedArgumentTypes: List<AstNode.Type>
            ) : Expression()
        }
        data class Return(val expression: Expression) : Command()
        data class KotlinFunction(val func: KtInterpreter.(List<KotlinHclExpression>) -> KotlinHclExpression) : Command()
    }
    sealed class Type : AstNode() {
        object Number : Type()
        object Text : Type()
        object Bool : Type()
        object None : Type()
        data class GenericType(val name: String) : Type()
        data class List(val elementType: Type) : Type()
        data class Func(val paramTypes: kotlin.collections.List<Type>, val returnType: Type) : Type()
        data class Tuple(val elementTypes: kotlin.collections.List<Type>) : Type()
    }

    data class ParameterDeclaration(val type: Type, val identifier: Command.Expression.Value.Identifier) : AstNode()
}

sealed class TypeWithImplicit
object VarDeclaration : TypeWithImplicit()
object ImplicitFuncDeclaration : TypeWithImplicit()
data class AstNodeType(val type: AstNode.Type) : TypeWithImplicit()

val AstNode.Type.typeName get(): String = when (this) {
    AstNode.Type.Number -> "Number"
    AstNode.Type.Text -> "Text"
    AstNode.Type.Bool -> "Boolean"
    AstNode.Type.None -> "Unit"
    is AstNode.Type.GenericType -> name
    is AstNode.Type.List -> "List[${elementType.typeName}]"
    is AstNode.Type.Func -> "Function(${paramTypes.joinToString { it.typeName }}): ${returnType.typeName}"
    is AstNode.Type.Tuple -> "Tuple(${elementTypes.joinToString { it.typeName }})"
}
