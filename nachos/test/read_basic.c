#include "syscall.h"

int main()
{
    char expected[] = "hello\n";
    char data[10];

    int fd = creat("p2_read_output.txt");
    if (fd < 0)
        exit(1);

    if (write(fd, expected, 6) != 6)
        exit(2);

    if (close(fd) != 0)
        exit(3);

    fd = open("p2_read_output.txt");
    if (fd < 0)
        exit(4);

    if (read(fd, data, -1) != -1)
        exit(23);

    if (write(fd, data, -1) != -1)
        exit(24);

    if (read(fd, data, 0) != 0)
        exit(25);

    if (write(fd, data, 0) != 0)
        exit(26);

    int n = read(fd, data, 10);
    if (n != 6)
        exit(5);

    for (int i = 0; i < 6; i++) {
        if (data[i] != expected[i])
            exit(6);
    }

    if (read(fd, data, 10) != 0)
        exit(7);

    if (close(fd) != 0)
        exit(8);

    if (unlink("p2_read_output.txt") != 0)
        exit(9);
    
    if (open("p2_read_output.txt") >= 0)
        exit(10);
    
    if (read(-1, data, 1) != -1)
        exit(11);   
    
    if (write(16, data, 1) != -1)
        exit(12);

    if (close(fd) != -1)
        exit(13);

    exit(0);


}
