import java.util.ArrayList;
import java.util.EnumSet;


public interface nfsAPI {
    public static final int O_CREAT = 1; // if file does not exist create it
    public static final int O_EXCL = 2; // if file exists return error with EEXIT
    public static final int O_TRUNC = 3; // delete file and recreate it
    public static final int O_RDWR = 4; //READ and WRITE permission to the file
    public static final int O_RDONLY = 5; // READ only permission
    public static final int O_WRONLY = 6; // WRITE only permission
    public static final int E_EXIST = -1; //error if file exists with O_EXCl

    public static final int SEEK_SET = 10; //relative to start position
    public static final int SEEK_CUR = 11; //relative to current position
    public static final int SEEK_END = 12; //relative to end




    int myNfs_init(String ipaddr,int port,int cacheBlocks,int blockSize,int freshT);
    int myNfs_open(String fName, ArrayList<Integer> flags);
    int myNfs_read(int fd, Msg buff,int n);
    int myNfs_write(int fd,String buff ,int n);
    int myNfs_seek(int fd ,int pos , int whence);
    int myNfs_close(int fd);

}
