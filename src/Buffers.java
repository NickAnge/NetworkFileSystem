import java.io.Serializable;
import java.net.InetAddress;
import java.util.EnumSet;

public class Buffers {

}

//FLags for open api
enum  Flag {
    O_CREAT, O_EXCL,O_TRUNC,O_RDWR,O_RDONLY,O_WRONLY;

    public static final EnumSet<Flag> ALL_OPTS = EnumSet.allOf(Flag.class);
}


//Informations about Server
class ServerInfo {
    InetAddress ServerIp;
    int port;

    public ServerInfo(InetAddress serverIp, int port) {
        ServerIp = serverIp;
        this.port = port;
    }

    public InetAddress getServerIp() {
        return ServerIp;
    }

    public void setServerIp(InetAddress serverIp) {
        ServerIp = serverIp;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }
}
//Cache Memory Informations
class CacheMemory {
    int cacheBlocks;
    double blockSize;
    long freshT;

    public CacheMemory(int cacheBlocks, double blockSize, long freshT) {
        this.cacheBlocks = cacheBlocks;
        this.blockSize = blockSize;
        this.freshT = freshT;
    }

    public int getCacheBlocks() {
        return cacheBlocks;
    }

    public void setCacheBlocks(int cacheBlocks) {
        this.cacheBlocks = cacheBlocks;
    }

    public double getBlockSize() {
        return blockSize;
    }

    public void setBlockSize(double blockSize) {
        this.blockSize = blockSize;
    }

    public long getFreshT() {
        return freshT;
    }

    public void setFreshT(long freshT) {
        this.freshT = freshT;
    }
}

class udpMessage implements Serializable {
    private String Type;


    public udpMessage(String type) {
        Type = type;
    }

    public String getType() {
        return Type;
    }

    public void setType(String type) {
        Type = type;
    }
}

class udpMessageRead extends udpMessage implements Serializable{
    private double size;
    private Msg readMsg;
    private int readClientInt;
    private fileDescriptor fd;


    public udpMessageRead(String type, int readClientInt, fileDescriptor fd,Msg readMsg, double size) {
        super(type);
        this.size = size;
        this.readClientInt = readClientInt;
        this.fd = fd;
        this.readMsg = readMsg;
    }

    public Msg getReadMsg() {
        return readMsg;
    }

    public void setReadMsg(Msg readMsg) {
        this.readMsg = readMsg;
    }

    public int getReadClientInt() {
        return readClientInt;
    }

    public void setReadClientInt(int readClientInt) {
        this.readClientInt = readClientInt;
    }

    public fileDescriptor getFd() {
        return fd;
    }

    public void setFd(fileDescriptor fd) {
        this.fd = fd;
    }

    public double getSize() {
        return size;
    }

    public void setSize(double size) {
        this.size = size;
    }
}
class udpMessageOpen extends udpMessage implements Serializable{
    private String fileName;
    private EnumSet<Flag> flags; // informations about open of this file
    private int  fd;
    private int openClientInt;

    public udpMessageOpen(String type, int clientId, int fd, String fileName, EnumSet<Flag> flags) {
        super(type);
        this.fileName = fileName;
        this.fd = fd;
        this.openClientInt = clientId;
        this.flags = flags;
    }

    public udpMessageOpen(String type, int fd, int openClientInt) {
        super(type);
        this.fd = fd;
        this.openClientInt = openClientInt;
    }

    public int getFd() {
        return fd;
    }

    public void setFd(int fd) {
        this.fd = fd;
    }

    public int getOpenClientInt() {
        return openClientInt;
    }

    public void setOpenClientInt(int openClientInt) {
        this.openClientInt = openClientInt;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public EnumSet<Flag> getFlags() {
        return flags;
    }

    public void setFlags(EnumSet<Flag> flags) {
        this.flags = flags;
    }
}
class udpMessageWrite extends udpMessage implements Serializable {
    private double size;
    private Msg writeMsg;
    private int writeClientInt;
    private fileDescriptor fd;


    public udpMessageWrite(String type, int clientId, fileDescriptor fd, double size, Msg writeMsg) {
        super(type);
        this.size = size;
        this.writeClientInt = clientId;
        this.fd = fd;
        this.writeMsg = writeMsg;
    }

    public double getSize() {
        return size;
    }

    public void setSize(double size) {
        this.size = size;
    }

    public Msg getWriteMsg() {
        return writeMsg;
    }

    public void setWriteMsg(Msg writeMsg) {
        this.writeMsg = writeMsg;
    }

    public int getWriteClientInt() {
        return writeClientInt;
    }

    public void setWriteClientInt(int writeClientInt) {
        this.writeClientInt = writeClientInt;
    }

    public fileDescriptor getFd() {
        return fd;
    }

    public void setFd(fileDescriptor fd) {
        this.fd = fd;
    }
}

class Msg implements  Serializable{
    String msg;

    public Msg(String msg) {
        this.msg = msg;
    }
}

//contains the information about each file descriptor
class fileDescriptor {
    private int fd;
    private int posFromStart;

    public fileDescriptor(int fd, int posFromStart) {
        this.fd = fd;
        this.posFromStart = posFromStart;
    }

    public int getFd() {
        return fd;
    }

    public void setFd(int fd) {
        this.fd = fd;
    }

    public int getPosFromStart() {
        return posFromStart;
    }

    public void setPosFromStart(int posFromStart) {
        this.posFromStart = posFromStart;
    }
}