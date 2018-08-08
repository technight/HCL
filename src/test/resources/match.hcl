# This should return 5

RETURN_CODE = 1 match [ \
    (0, { 1 }), \
    (1, { 5 }), \
    (2, { 2 * 3 }) \
]
