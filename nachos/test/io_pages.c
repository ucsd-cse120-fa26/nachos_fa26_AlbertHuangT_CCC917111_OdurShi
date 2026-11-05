#include "syscall.h"

int main()
{
    char source[2500];
    char result[2500];

    for (int i = 0; i < 2500; i++)
    {
        source[i] = 'A' + (i % 26);
    }
    int fd = creat("p2_fd_output.txt");
    if (fd < 0)
        exit(1);

    if (write(fd, source, 2500) != 2500)
        exit(2);

    if (close(fd) != 0)
        exit(3);

    fd = open("p2_fd_output.txt");
    if (fd < 0)
        exit(4);

    if (read(fd, result, 2500) != 2500)
        exit(5);
    
    for (int i = 0; i < 2500; i++)
    {
        if (result[i] != source[i])
            exit(6);
    }

    if (close(fd) != 0)
        exit(7);

    if (unlink("p2_fd_output.txt") != 0)
        exit(8);

    exit(0);
}