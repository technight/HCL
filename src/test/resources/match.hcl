# This should return 5

var a = 1 match [
    (0, (num x): num { 11 }),
    (1, (num x): num { x + 2 }),
    (2, (num x): num { 2 * 3 })
]

var b = 5 match [
    ( (num x): bool { x lessThan 5 }, { 1 } ),
    ( (num x): bool { x is 5 }, { 2 } ),
    ( (num x): bool { x greaterThan 5 }, { 3 } )
]

RETURN_CODE = a + b
