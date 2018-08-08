# This should print [1, 1, 2, 3, 5, 8]

func fib = (num n) : num {
    (n lessThanEqual 2) then { 1 } else { ((n - 1) fib) + ((n - 2) fib) }
}

1 to 6 map { value fib } print
