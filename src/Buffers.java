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

class udpMessageOpen {
    private static final String Type = "Open";
    private String fileName;
    private int clientId;
    private int fd;

    public udpMessageOpen(String fileName, int clientId, int fd) {
        this.fileName = fileName;
        this.clientId = clientId;
        this.fd = fd;
    }

    public static String getType() {
        return Type;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public int getClientId() {
        return clientId;
    }

    public void setClientId(int clientId) {
        this.clientId = clientId;
    }

    public int getFd() {
        return fd;
    }

    public void setFd(int fd) {
        this.fd = fd;
    }
}

class udpMessageRead {
    private static final String Type = "Read";
    private int fd;
    private double size;
    private int clientId;

    public udpMessageRead(int fd, double size, int clientId) {
        this.fd = fd;
        this.size = size;
        this.clientId = clientId;
    }

    public static String getType() {
        return Type;
    }

    public int getFd() {
        return fd;
    }

    public void setFd(int fd) {
        this.fd = fd;
    }

    public double getSize() {
        return size;
    }

    public void setSize(double size) {
        this.size = size;
    }

    public int getClientId() {
        return clientId;
    }

    public void setClientId(int clientId) {
        this.clientId = clientId;
    }
}

class udpMessageWrite {
    private static final String Type = "Write";
    private int fd;
    private double size;
    private int clientId;

    public udpMessageWrite(int fd, double size, int clientId) {
        this.fd = fd;
        this.size = size;
        this.clientId = clientId;
    }

    public static String getType() {
        return Type;
    }

    public int getFd() {
        return fd;
    }

    public void setFd(int fd) {
        this.fd = fd;
    }

    public double getSize() {
        return size;
    }

    public void setSize(double size) {
        this.size = size;
    }

    public int getClientId() {
        return clientId;
    }

    public void setClientId(int clientId) {
        this.clientId = clientId;
    }
}