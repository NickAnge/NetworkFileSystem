

public class NfsClient implements   nfsAPI{



    //TODO init communication information(structure for server information) with server and information of cache(structure)
    @Override
    public int myNfs_init(String ipaddr, int port, int cacheBlocks, int blockSize, int freshT) {

        return 0;
    }

    @Override
    public int myNfs_open(String fName, int flags) {
        return 0;
    }

    @Override
    public int myNfs_read(int fd, String buff, int n) {
        return 0;
    }

    @Override
    public int myNfs_write(int fd, String buff, int n) {
        return 0;
    }

    @Override
    public int myNfs_seek(int fd, int pos, int whence) {
        return 0;
    }

    @Override
    public int myNfs_close(int fd) {
        return 0;
    }
}
