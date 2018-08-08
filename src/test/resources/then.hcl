# should return 11 and print 2
var x = 1
var y = 2

y > x then { y } else { x } print

y - 1 is x then { RETURN_CODE = 4 }
x + 1 is y then { RETURN_CODE = RETURN_CODE + 1 } else { RETURN_CODE = 0 }
x > y else { RETURN_CODE = RETURN_CODE * 2 } else { RETURN_CODE = RETURN_CODE + 1 }
