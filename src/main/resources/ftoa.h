#ifndef FTOA_H
#define FTOA_H

#include <cstdlib>
#include <cstdio>
#define MAX_STR_LEN 16

/*
 * This function returns a char array (string) representation of a floating point value
 * Parameters: d = double to be converted | precision = number of digits after decimal point
 */
List<char> ftoa(double d, int precision) {
    char buffer[MAX_STR_LEN];
    char *endOfString = buffer;
    memset(buffer, 0, MAX_STR_LEN);

    // Add digits before decimal point to string
    long characteristic = (long)d;
    sprintf(buffer, "%ld", characteristic);

    // Add digits after decimal point, if needed
    if (precision > 0) {

        while (*endOfString != '\0') endOfString++;
        *endOfString++ = '.';

        if (d < 0) {
            d *= -1;
            characteristic *= -1;
        }

        double mantissa = d - characteristic;
        char *endOfBuffer = buffer + MAX_STR_LEN;
        for (; precision > 0 && endOfString < endOfBuffer; precision--) {
            mantissa *= 10;
            characteristic = (long)mantissa;
            *endOfString++ = '0' + (char)characteristic; // Append digit to string

            mantissa -= characteristic;
        }
        while (*(endOfString-1) == '0'){
            endOfString = endOfString - 1;
        }
        if (*(endOfString-1) == '.'){
            endOfString = endOfString - 1;
        }

        *endOfString = '\0';

    }
    return ConstList<char>::string(buffer);
    //return ConstList<char>::create(buffer,endOfString-buffer);
}

#endif //FTOA_H