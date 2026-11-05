#include "syscall.h"

int main()
{
    int result = write(1, "hello\n", 6);

    if (result != 6)
        exit(1);

    exit(0);
}
