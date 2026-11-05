#include "syscall.h"

int main()
{
    int fd = creat("p2_create_output.txt");

    if (fd < 0)
        exit(1);

    int written = write(fd, "hello\n", 6);

    if (written != 6)
        exit(2);

    if (close(fd) != 0)
        exit(3);

    fd = open("p2_create_output.txt");
    if (fd < 0)
        exit(4);

    if (close(fd) != 0)
        exit(5);
        
    exit(0);
}

