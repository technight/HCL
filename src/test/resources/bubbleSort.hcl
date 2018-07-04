#should print [1, 2, 3, 4, 5, 6, 7, 8, 9]

var swapAtIndex = (list[T] lst, num firstIndex, num secondIndex): list[T]{

    var firstSub = lst splitAt 0 (firstIndex)
    var secondSub = lst splitAt (firstIndex + 1) (secondIndex - (firstIndex + 1))
    var lastSub = lst splitAt (secondIndex + 1) ((lst length) - (secondIndex + 1))
    return firstSub + [(lst at secondIndex)] + secondSub + [(lst at firstIndex)] + lastSub
}


var bubbleSort = (list[num] lst): list[num] {
    num i
    {
        num j
        {
            (lst at j) greaterThan (lst at (j  + 1)) then {
                lst = lst swapAtIndex j (j  + 1)
            }

            j = j + 1
        } while { j lessThan (lst length - 1) }
        i = i + 1
    } while { i lessThan  ((lst length) - 1) }

    return lst
}

var lst = [9, 8, 7, 6, 5, 4, 3, 2, 1]
var newList = lst bubbleSort
newList print
