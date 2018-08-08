var generateBraceMap = (txt code): list[tuple[num, num]] {
    list[num] braceStack
    list[tuple[num, num]] braceMap
    var codeIdx = 0
    var codeLength = code length
    {
        var currentChar = code at codeIdx
        currentChar is "[" thenDo { braceStack = braceStack + [codeIdx] }
        currentChar is "]" thenDo {
            var newContent = (braceStack withoutLast, braceStack @ (braceStack length - 1))
            braceStack = newContent element0
            var gotoIdx = newContent element1
            braceMap = braceMap + [(codeIdx, gotoIdx)]
            braceMap = braceMap + [(gotoIdx, codeIdx)]
        }
        codeIdx = codeIdx + 1
    } while { codeIdx lessThan codeLength }
    return braceMap
}

var valueOfIdx = (list[tuple[num, num]] braceMap, num from): num {
    var res = -1
    braceMap forEach {
        var cond = value element0 is from
        cond thenDo { res = value element1 }
    }
    return res
}

"Enter brainfuck:" print

var code = input
var cells = [0]
var codePtr = 0
var cellPtr = 0
var braceMap = code generateBraceMap
var codeLen = code length
var result = ""

{
    var command = code at codePtr

    command equals ">" thenDo {
        cellPtr = cellPtr + 1
        cells length equals cellPtr thenDo { cells = cells + [0] }
    }
    command is "<" thenDo { cellPtr = cellPtr is 0 thenElse { 0 } { cellPtr - 1 } }
    command is "+" thenDo { cells = cells changeIdx cellPtr (cells @ cellPtr is 255 thenElse { 255 } { cells @ cellPtr + 1 }) }
    command is "-" thenDo { cells = cells changeIdx cellPtr (cells @ cellPtr is 0 thenElse { 0 } { cells @ cellPtr - 1 }) }
    command is "[" and (cells @ cellPtr is 0) thenDo { codePtr = braceMap valueOfIdx codePtr }
    command is "]" and (cells @ cellPtr isNot 0) thenDo { codePtr = braceMap valueOfIdx codePtr }
    command is "." thenDo { result = result + (cells @ cellPtr toChar) }

    codePtr = codePtr + 1
} while { codePtr lessThan codeLen }

result print
