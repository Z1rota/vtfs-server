package dev.vtfs.server.exception;

public class FsException extends RuntimeException {
    private final int errno;

    public FsException(int errno) {
        this.errno = errno;
    }

    public int getErrno() {
        return errno;
    }
}
