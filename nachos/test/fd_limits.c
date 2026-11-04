#include "syscall.h"

int main()
{
    int fd;
    fd = creat("p2_fd_output.txt");
    if (fd < 0)
        exit(14);
    
    if (close(fd) != 0)
        exit(15);

    int fds[14];

    for (int i = 0; i < 14; i++)
    {
        fds[i] = open("p2_fd_output.txt");
        if (fds[i] < 0)
            exit(16);
    }

    int fd_extra = open("p2_fd_output.txt");
    if (fd_extra != -1)
        exit(17);
    
    if (close(fds[0]) != 0)
        exit(18);

    int replacement = open("p2_fd_output.txt");
    if (replacement < 0)
        exit(19);

    for (int i = 1; i < 14; i++)
    {
        if (close(fds[i]) != 0)
            exit(20);
    }
    
    if (close(replacement) != 0)
        exit(21);

    if (unlink("p2_fd_output.txt") != 0)
        exit(22);

    exit(0);
}
