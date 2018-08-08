package parser

import parser.typechecker.ITypeChecker
import parser.typechecker.TypeChecker
import lexer.ILexer
import lexer.PositionalToken
import lexer.Token
import parser.symboltable.EnterSymbolResult
import parser.symboltable.ISymbolTable
import parser.symboltable.SymbolTable
import utils.BufferedLaabStream
import utils.IBufferedLaabStream
import kotlin.coroutines.experimental.buildSequence

/**
 * Implementation of IParser interface
 * Used to generate AST from token sequence
 * @param lexer A lexer object that generates a token sequence
 */
open class Parser(val lexer: ILexer) : IParser, ITypeChecker by TypeChecker(), ISymbolTable by SymbolTable(),
        IBufferedLaabStream<PositionalToken> by BufferedLaabStream(lexer.getTokenSequence()) {
    // Used for recursive calls
    private var currentFunctionName: String? = null
    override fun commandSequence() = buildSequence {
        // Parse
        while (hasNext()) {
            if (current.token !in listOf(Token.SpecialChar.EndOfLine, Token.SpecialChar.EndOfFile)) {
                yield(parseCommand())
            } else moveNext()
        }
    }

    private fun parseCommand(): AstNode.Command {
        flushNewLine(false)
        val command = when (current.token) {
            is Token.Type -> parseDeclaration()
            is Token.Identifier -> when {
                genericTypeInScope((current.token as Token.Identifier).value) -> parseDeclaration()
                peek().token == Token.SpecialChar.Equals -> parseAssignment()
                else -> parseExpression()
            }
            is Token.Literal, // Fallthrough
            Token.SpecialChar.BlockStart,
            Token.SpecialChar.SquareBracketStart,
            Token.SpecialChar.Colon,
            Token.SpecialChar.ParenthesesStart -> parseExpression()
            Token.Return -> parseReturnStatement()
            else -> unexpectedTokenError(current.token)
        }
        flushNewLine(current.token !in listOf(Token.SpecialChar.BlockEnd))
        return command
    }

    private fun parseReturnStatement(): AstNode.Command.Return {
        accept<Token.Return>()
        val expression = parseExpression()
        return AstNode.Command.Return(expression)
    }

    private inline fun <reified T : Token> accept(): T {
        val token = current.token
        moveNext()
        if (token is T) {
            return token
        } else {
            wrongTokenTypeError(T::class.simpleName!!, token)
        }
    }

    private inline fun <reified T> tryAccept() = (current.token as? T)?.let { moveNext(); true } ?: false

    private fun flushNewLine(requireNewLine: Boolean = true) {
        if (hasNext() && requireNewLine) accept<Token.SpecialChar.EndOfLine>()
        while (current.token == Token.SpecialChar.EndOfLine && hasNext()) {
            accept<Token.SpecialChar.EndOfLine>()
        }
    }

    private fun acceptIdentifier(type: AstNode.Type) =
            AstNode.Command.Expression.Value.Identifier(accept<Token.Identifier>().value, type)

    private fun acceptIdentifierName() = accept<Token.Identifier>().value

    private fun acceptLiteral(): AstLiteral {
        val litToken = accept<Token.Literal>()
        return when (litToken) {
            is Token.Literal.Number -> AstNode.Command.Expression.Value.Literal.Number(litToken.value)
            is Token.Literal.Text -> AstNode.Command.Expression.Value.Literal.Text(litToken.value)
            is Token.Literal.Bool -> AstNode.Command.Expression.Value.Literal.Bool(litToken.value)
        }
    }

    private fun parseSingleParameter(): AstNode.ParameterDeclaration {
        val type = parseType(genericAllowed = true) as AstNodeType
        val identifier = acceptIdentifier(type.type)
        if (current.token == Token.SpecialChar.Equals) initializedFunctionParameterError()
        return AstNode.ParameterDeclaration(type.type, identifier)
    }

    private fun parseFunctionParameters(): List<AstNode.ParameterDeclaration> {
        val parameters = mutableListOf<AstNode.ParameterDeclaration>()

        accept<Token.SpecialChar.ParenthesesStart>()
        while (current.token != Token.SpecialChar.ParenthesesEnd) {
            parameters.add(parseSingleParameter())
            if (!tryAccept<Token.SpecialChar.ListSeparator>()) break
        }

        accept<Token.SpecialChar.ParenthesesEnd>()
        return parameters
    }

    private fun parseDeclaration(): AstNode.Command.Declaration {
        val type = parseType(implicitAllowed = true)
        val identifierName = acceptIdentifierName()

        return if (tryAccept<Token.SpecialChar.Equals>()) {
            // Only valid if upcoming expression is a function
            currentFunctionName = identifierName

            val expression = parseExpression()
            currentFunctionName = null
            if (type != ImplicitFuncDeclaration &&
                type != VarDeclaration &&
                expression.type != (type as AstNodeType).type)
                unexpectedTypeError(type::class.simpleName!!, expression.type::class.simpleName!!)

            when (enterSymbol(identifierName, expression.type)) {
                EnterSymbolResult.OverloadAlreadyDeclared ->
                    alreadyDeclaredException()
                EnterSymbolResult.OverloadDifferentParamNums ->
                    overloadWithDifferentAmountOfArgumentsException()
                EnterSymbolResult.IdentifierAlreadyDeclared -> alreadyDeclaredException()
                else -> AstNode.Command.Declaration(expression.type,
                        AstNode.Command.Expression.Value.Identifier(identifierName, expression.type), expression)
            }
        } else {
            if (type == ImplicitFuncDeclaration || type == VarDeclaration)
                uninitializedImplicitTypeError(identifierName)
            if (type is AstNodeType && type.type is AstNode.Type.Func)
                funcDeclaredWithoutBodyError(identifierName)

            enterSymbol(identifierName, (type as AstNodeType).type).let {
                if (it == EnterSymbolResult.IdentifierAlreadyDeclared)
                    alreadyDeclaredException()
            }

            AstNode.Command.Declaration(type.type,
                    AstNode.Command.Expression.Value.Identifier(identifierName, type.type), null)
        }
    }

    private fun parseAssignment(): AstNode.Command.Assignment {
        val identifierName = acceptIdentifierName()
        accept<Token.SpecialChar.Equals>()
        val expression = parseExpression()
        return if (!retrieveSymbol(identifierName).handle({ true }, { it == expression.type }, { false }))
            unexpectedTypeError(
                    retrieveSymbol(identifierName).identifier::class.simpleName!!,
                    expression.type::class.simpleName!!
            )
        else AstNode.Command.Assignment(AstNode.Command.Expression.Value.Identifier(
                identifierName, expression.type
        ), expression)
    }

    // region Type declarations
    private fun parseType(implicitAllowed: Boolean = false, genericAllowed: Boolean = false): TypeWithImplicit {
        val currentPosToken = current
        moveNext()
        return when (currentPosToken.token) {
            Token.Type.Number -> AstNodeType(AstNode.Type.Number)
            Token.Type.Text -> AstNodeType(AstNode.Type.Text)
            Token.Type.Bool -> AstNodeType(AstNode.Type.Bool)
            Token.Type.Var ->
                if (implicitAllowed) VarDeclaration
                else implicitTypeNotAllowedError()
            Token.Type.Func -> parseFuncType(implicitAllowed)
            Token.Type.Tuple -> AstNodeType(parseTupleType(genericAllowed))
            Token.Type.List -> AstNodeType(parseListType(genericAllowed))
        // generic
            is Token.Identifier ->
                if (genericAllowed || genericTypeInScope(currentPosToken.token.value))
                    AstNodeType(AstNode.Type.GenericType(currentPosToken.token.value))
                else unexpectedTokenError(currentPosToken.token)
            else -> unexpectedTokenError(currentPosToken.token)
        }
    }

    private fun parseTypes(parsingMethod: () -> AstNode.Type): List<AstNode.Type> {
        val elementTypes = mutableListOf<AstNode.Type>()

        while (true) {
            elementTypes.add(parsingMethod())
            if (!tryAccept<Token.SpecialChar.ListSeparator>()) return elementTypes
        }
    }

    private fun parseFuncType(implicitAllowed: Boolean): TypeWithImplicit {
        return when {
            current.token == Token.SpecialChar.SquareBracketStart -> {
                accept<Token.SpecialChar.SquareBracketStart>()

                val parameters = parseTypes({
                    if (peek().token == Token.SpecialChar.SquareBracketEnd && tryAccept<Token.Type.None>())
                        AstNode.Type.None
                    else (parseType(genericAllowed = true) as AstNodeType).type
                })
                accept<Token.SpecialChar.SquareBracketEnd>()
                val returnType = parameters.last()

                AstNodeType(AstNode.Type.Func(parameters.dropLast(1), returnType))
            }
            implicitAllowed -> ImplicitFuncDeclaration
            else -> implicitTypeNotAllowedError()
        }
    }

    private fun parseLambdaLiteral(): AstNode.Command.Expression.LambdaExpression {
        val parameters = parseFunctionParameters()
        accept<Token.SpecialChar.Colon>()
        val returnType = if (current.token == Token.Type.None) {
            moveNext()
            AstNode.Type.None
        } else (parseType(genericAllowed = true) as AstNodeType).type

        flushNewLine(false)

        // add recursive call if valid
        val recCall = currentFunctionName?.let { listOf(AstNode.ParameterDeclaration(
                AstNode.Type.Func(parameters.map { it.type }, returnType),
                AstNode.Command.Expression.Value.Identifier(it, returnType)))
        } ?: listOf()

        val lambda = parseLambdaBody(parameters + recCall)
        if (lambda.type != returnType && returnType != AstNode.Type.None)
            unexpectedReturnTypeError(returnType::class.simpleName!!, lambda.type::class.simpleName!!)
        return AstNode.Command.Expression.LambdaExpression(parameters, returnType, lambda.lambdaBody)
    }

    private val AstNode.Type.getGenerics get(): Set<AstNode.Type.GenericType> = when (this) {
        is AstNode.Type.GenericType -> setOf(this)
        is AstNode.Type.List -> this.elementType.getGenerics
        is AstNode.Type.Tuple -> this.elementTypes.flatMap { it.getGenerics }.toSet()
        is AstNode.Type.Func ->
            (this.paramTypes.flatMap { it.getGenerics } + returnType.getGenerics).toSet()
        else -> setOf()
    }

    private fun parseLambdaBody(inputParams: List<AstNode.ParameterDeclaration>): LambdaBodyWithType {
        val commands = mutableListOf<AstNode.Command>()
        flushNewLine(false)
        accept<Token.SpecialChar.BlockStart>()
        openScope()
        inputParams.forEach { it.type.getGenerics.forEach { enterType(it) } }
        inputParams.forEach { enterSymbol(it.identifier.name, it.type) }
        while (current.token != Token.SpecialChar.BlockEnd) {
            commands.add(parseCommand())
        }
        val body = AstNode.Command.Expression.LambdaBody(
                if (commands.size == 0 || commands.last() !is AstNode.Command.Expression)
                    commands //listOf(AstNode.Command.Return(commands[0] as AstNode.Command.Expression))
                else
                    commands.subList(0,commands.count()-1) + listOf(AstNode.Command.Return(commands.last() as AstNode.Command.Expression))
        )

        val ret = LambdaBodyWithType(body, body.type)
        closeScope()
        flushNewLine(false)
        accept<Token.SpecialChar.BlockEnd>()
        return ret
    }

    private fun parseTupleType(genericAllowed: Boolean): AstNode.Type.Tuple {
        accept<Token.SpecialChar.SquareBracketStart>()
        val elementTypes = parseTypes { (parseType(genericAllowed = genericAllowed) as AstNodeType).type }
        accept<Token.SpecialChar.SquareBracketEnd>()
        return AstNode.Type.Tuple(elementTypes)
    }

    private fun parseListType(genericAllowed: Boolean): AstNode.Type.List {
        accept<Token.SpecialChar.SquareBracketStart>()
        val elementType: AstNode.Type = (parseType(genericAllowed = genericAllowed) as AstNodeType).type
        accept<Token.SpecialChar.SquareBracketEnd>()
        return AstNode.Type.List(elementType)
    }
// endregion

    // region ExpressionParsing

    private fun parseExpression() = parsePotentialFunctionCall(
            if (current.token == Token.SpecialChar.BlockStart) null
            else parseExpressionAtomic()
    )

    private fun getUpcomingIdentifierNameForLambda(): String? {
        var scopeDepth = 1
        return findElement({
            when (it.token) {
                Token.SpecialChar.BlockStart -> scopeDepth += 1
                Token.SpecialChar.BlockEnd -> scopeDepth -= 1
                is Token.Identifier -> if (scopeDepth == 0) return@findElement it.token.value
                else -> {}
            }
            null
        }, { scopeDepth == 0 && it.token !is Token.Identifier }, 1)
    }

    private fun getLambdaParameter(
        func: List<AstNode.Type.Func>,
        index: Int,
        params: List<AstNode.Type> = emptyList()
    ): AstExpression {
        val funcAcceptingLambda = func.firstOrNull {
            it.paramTypes[index] is AstNode.Type.Func
        } ?: lambdaArgumentNotAcceptedError()
        val lambdaFuncType = funcAcceptingLambda.paramTypes[index] as AstNode.Type.Func
        val lambdaParams = when (lambdaFuncType.paramTypes.size) {
            0 -> emptyList()
            else -> {
                val generics = funcAcceptingLambda.paramTypes.zip(params)
                lambdaFuncType.paramTypes.mapIndexed { idx, type ->
                    val actualType = (type as? AstNode.Type.GenericType)?.let { getTypeFromTypePairs(generics, it.name) }
                    ?: type
                    AstNode.ParameterDeclaration(actualType,
                            AstNode.Command.Expression.Value.Identifier(
                                    "value${if (idx != 0) "${idx + 1}" else ""}",
                                    actualType
                            )
                    )
                }
            }
        }
        val lambdaBody = parseLambdaBody(lambdaParams)
        return AstNode.Command.Expression.LambdaExpression(
                lambdaParams,
                lambdaBody.type,
                lambdaBody.lambdaBody)
    }

    private fun parsePotentialFunctionCall(expression: AstExpression? = null): AstExpression =
            when (current.token) {
                is Token.SpecialChar.BlockStart -> {
                    val upcomingId = getUpcomingIdentifierNameForLambda()
                    if (upcomingId != null) {
                        val symbol = retrieveSymbol(upcomingId)
                        if (!symbol.isFunctions) identifierIsNotFunctionError(upcomingId)
                        val lambdaParameter = getLambdaParameter(symbol.functions, 0)
                        parsePotentialFunctionCall(lambdaParameter)
                    } else {
                        val lambda = parseLambdaBody(emptyList())
                        AstNode.Command.Expression.LambdaExpression(listOf(), lambda.type, lambda.lambdaBody)
                    }
                }
                is Token.Identifier -> {
                    val token = accept<Token.Identifier>()
                    val symbol = retrieveSymbol(token.value)
                    if (symbol.isFunctions) {
                        val funcDecls = symbol.functions
                        val secondaryArguments = mutableListOf<AstNode.Command.Expression>()
                        funcDecls.first().paramTypes.drop(1).forEachIndexed { index, _ ->
                            secondaryArguments.add(if (current.token != Token.SpecialChar.BlockStart)
                                parseExpressionAtomic()
                            else {
                                val params = secondaryArguments.map { it.type } + listOf(expression!!.type)
                                getLambdaParameter(funcDecls, index + 1, params)
                            })
                        }
                        val argTypes = listOf(expression!!.type) + secondaryArguments.map { it.type }
                        val declaration = funcDecls.getTypeDeclaration(argTypes)
                        if (declaration == null) unknownFunctionOverload(token, argTypes)
                        else AstNode.Command.Expression.FunctionCall(
                                AstNode.Command.Expression.Value.Identifier(token.value,
                                        getTypeOnFuncCall(declaration, argTypes)),
                                listOf(expression) + secondaryArguments,
                                declaration.paramTypes
                        )
                    } else if (symbol.isIdentifier) identifierIsNotFunctionError(token.value)
                    else undeclaredError(token)
                }
                else -> expression
            }.let { if (expression != it) parsePotentialFunctionCall(it) else expression!! }

    /* Doesn't work with generics */
    private fun isLambdaParameters() = peek().token is Token.Type ||
            (peek().token as? Token.Identifier)?.let { retrieveSymbol(it.value).undeclared } ?: false ||
            peek().token == Token.SpecialChar.ParenthesesEnd

    private fun parseExpressionAtomic(): AstExpression =
            when (current.token) {
                Token.SpecialChar.SquareBracketStart -> parseListLiteral(AstNode.Type.None)
                Token.SpecialChar.ParenthesesStart -> {
                    if (isLambdaParameters()) {
                        parseLambdaLiteral()
                    } else {
                        if (upcomingTuple()) parseTupleExpression() else {
                            accept<Token.SpecialChar.ParenthesesStart>()
                            parseExpression().apply { accept<Token.SpecialChar.ParenthesesEnd>() }
                        }
                    }
                }
                is Token.Literal.Text,
                is Token.Literal.Bool,
                is Token.Literal.Number -> acceptLiteral()
                is Token.SpecialChar.Colon -> {
                    if (peek().token is Token.Identifier) accept<Token.SpecialChar.Colon>()
                    else unexpectedTokenError(current.token)
                    val token = accept<Token.Identifier>()

                    retrieveSymbol(token.value).handle(
                        {
                            if (it.size == 1) AstIdentifier(token.value, it.first())
                            else overloadedPassedFunctionException()
                        },
                        { identifierIsNotFunctionError(token.value) },
                        { undeclaredError(token) }
                    )
                }
                is Token.Identifier -> {
                    val token = accept<Token.Identifier>()
                    retrieveSymbol(token.value).handle(
                        {
                            if (it.first().paramTypes.isEmpty()) {
                                AstNode.Command.Expression.FunctionCall(
                                    AstIdentifier(token.value, it.first().returnType),
                                    listOf(),
                                    listOf()
                                )
                            } else functionInvokedWithoutArgumentsError(token.value)
                        },
                        { AstIdentifier(token.value, it) },
                        { undeclaredError(token) }
                    )
                }
                else -> unexpectedTokenError(current.token)
            }

    private fun upcomingTuple(): Boolean {
        var depth = 0
        return findElement({
            when (it.token) {
                Token.SpecialChar.ParenthesesEnd -> if (depth == 0) return@findElement false else depth--
                Token.SpecialChar.ParenthesesStart,
                Token.SpecialChar.SquareBracketStart -> depth++
                Token.SpecialChar.SquareBracketEnd -> depth--
                Token.SpecialChar.ListSeparator -> if (depth == 0) return@findElement true
                else -> { }
            }
            null
        }, { it.token == Token.SpecialChar.EndOfLine }, 1)?.let { it } ?: lackingParenthesis()
    }

    private fun parseTupleExpression() =
            AstNode.Command.Expression.Value.Literal.Tuple(
                    mutableListOf<AstNode.Command.Expression>().apply {
                        accept<Token.SpecialChar.ParenthesesStart>()
                        while (current.token != Token.SpecialChar.ParenthesesEnd) {
                            add(parseExpression())
                            if (!tryAccept<Token.SpecialChar.ListSeparator>()) break
                        }
                        accept<Token.SpecialChar.ParenthesesEnd>()
                    }
            )

    private fun parseListLiteral(type: AstNode.Type): AstNode.Command.Expression.Value.Literal.List {
        var inputType = type
        return AstNode.Command.Expression.Value.Literal.List(
                mutableListOf<AstNode.Command.Expression>().apply {
                    accept<Token.SpecialChar.SquareBracketStart>()
                    while (current.token != Token.SpecialChar.SquareBracketEnd) {
                        add(parseExpression())
                        if (inputType == AstNode.Type.None)
                            inputType = last().type
                        if (last().type != inputType)
                            unexpectedTypeError(inputType::class.simpleName!!, last().type::class.simpleName!!)
                        if (!tryAccept<Token.SpecialChar.ListSeparator>()) break
                    }
                    accept<Token.SpecialChar.SquareBracketEnd>()
                }, inputType
        )
    }

    // endregion ExpressionParsing

    private fun AstNode.Type.containsGeneric(): Boolean = when (this) {
        is AstNode.Type.List -> this.elementType.containsGeneric()
        is AstNode.Type.Tuple -> this.elementTypes.any { it.containsGeneric() }
        is AstNode.Type.Func ->
            this.paramTypes.any { it.containsGeneric() } || this.returnType.containsGeneric()
        is AstNode.Type.GenericType -> true
        else -> false
    }
}
