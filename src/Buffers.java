import java.io.File;
import java.io.Serializable;
import java.lang.instrument.Instrumentation;
import java.net.InetAddress;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class Buffers {

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
    List<Block> cache;
//    int cacheBlocks;
    int blockSize;
    int freshT;

    public CacheMemory(int blockSize, int freshT) {
        this.cache = new ArrayList<>();
        this.blockSize = blockSize;
        this.freshT = freshT;
    }

    public CacheMemory(int freshT) {
        this.cache = new ArrayList<>();
        this.freshT = freshT;
    }

    public List<Block> getCache() {
        return cache;
    }

    public void setCache(List<Block> cache) {
        this.cache = cache;
    }

    public int getBlockSize() {
        return blockSize;
    }

    public void setBlockSize(int blockSize) {
        this.blockSize = blockSize;
    }

    public int getFreshT() {
        return freshT;
    }

    public void setFreshT(int freshT) {
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
    private double size; // how much we want to read
//    private String readMsg; // the msg
    private int readClientInt; // the id for duplicates in client
    private byte[] readMsg;
    private fileDescriptor fd; //information for this fd
//    private  fileID idfd;
    private fileAttributes attributes; //size

    public udpMessageRead(String type, double size, int readClientInt, fileDescriptor fd, fileAttributes attributes) {
        super(type);
        this.size = size;
        this.readClientInt = readClientInt;
        this.fd = fd;
        this.attributes = attributes;
    }

    public udpMessageRead(String type, double size, int readClientInt, fileDescriptor fd) {
        super(type);
        this.size = size;
        this.readClientInt = readClientInt;
        this.fd = fd;
    }

    public udpMessageRead(String type, byte[] readMsg, int readClientInt, fileDescriptor fd, fileAttributes attributes) {
        super(type);
        this.readMsg = readMsg;
        this.readClientInt = readClientInt;
        this.fd = fd;
        this.attributes = attributes;
    }

    public fileAttributes getAttributes() {
        return attributes;
    }

    public void setAttributes(fileAttributes attributes) {
        this.attributes = attributes;
    }

    public byte[] getReadMsg() {
        return readMsg;
    }

    public void setReadMsg(byte[] readMsg) {
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
    private ArrayList<Integer> flags;
//    private fileID  fd; // maybe change it
    private int openACK;
    private int openClientInt;
    private fileAttributes attributes;
    private fileDescriptor filed;

    public udpMessageOpen(String type, String fileName, ArrayList<Integer> flags, int openACK, fileAttributes attributes, fileDescriptor filed) {
        super(type);
        this.fileName = fileName;
        this.flags = flags;
        this.openACK = openACK;
        this.attributes = attributes;
        this.filed = filed;
    }
    public udpMessageOpen(String type, ArrayList<Integer> flags, int openACK, int openClientInt, fileAttributes attributes, fileDescriptor filed) {
        super(type);
        this.flags = flags;
        this.openACK = openACK;
        this.openClientInt = openClientInt;
        this.attributes = attributes;
        this.filed = filed;
    }

    public fileDescriptor getFiled() {
        return filed;
    }

    public void setFiled(fileDescriptor filed) {
        this.filed = filed;
    }

    public int getOpenACK() {
        return openACK;
    }

    public void setOpenACK(int openACK) {
        this.openACK = openACK;
    }

    public fileAttributes getAttributes() {
        return attributes;
    }

    public void setAttributes(fileAttributes attributes) {
        this.attributes = attributes;
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

    public ArrayList<Integer> getFlags() {
        return flags;
    }

    public void setFlags(ArrayList<Integer> flags) {
        this.flags = flags;
    }
}
class udpMessageWrite extends udpMessage implements Serializable {
    private byte[] writeMsg;
    private int writeClientInt;
    private fileDescriptor fd;
    private fileAttributes attributes;

    public udpMessageWrite(String type, int writeClientInt,fileDescriptor fd, fileAttributes attributes) {
        super(type);
        this.writeClientInt = writeClientInt;
        this.fd = fd;
        this.attributes = attributes;
    }

    public udpMessageWrite(String type, int clientId, fileDescriptor fd, byte[] writeMsg, fileAttributes attributes) {
        super(type);
        this.writeClientInt = clientId;
        this.fd = fd;
        this.writeMsg = writeMsg;
        this.attributes = attributes;
    }
    public fileAttributes getAttributes() {
        return attributes;
    }

    public void setAttributes(fileAttributes attributes) {
        this.attributes = attributes;
    }


    public byte[] getWriteMsg() {
        return writeMsg;
    }

    public void setWriteMsg(byte[] writeMsg) {
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


class clientFileInformation implements Serializable {
    fileDescriptor fd;
    fileAttributes attributes;
    private int readPermission;
    private int writePermission;

    public clientFileInformation(fileDescriptor fd, fileAttributes attributes) {
        this.fd = fd;
        this.attributes = attributes;
    }

    public clientFileInformation(fileDescriptor fd, fileAttributes attributes, int readPermission, int writePermission) {
        this.fd = fd;
        this.attributes = attributes;
        this.readPermission = readPermission;
        this.writePermission = writePermission;
    }

    public int getReadPermission() {
        return readPermission;
    }

    public void setReadPermission(int readPermission) {
        this.readPermission = readPermission;
    }

    public int getWritePermission() {
        return writePermission;
    }

    public void setWritePermission(int writePermission) {
        this.writePermission = writePermission;
    }

    public fileDescriptor getFd() {
        return fd;
    }

    public void setFd(fileDescriptor fd) {
        this.fd = fd;
    }

    public fileAttributes getAttributes() {
        return attributes;
    }

    public void setAttributes(fileAttributes attributes) {
        this.attributes = attributes;
    }
}
class Msg implements  Serializable{
    byte[] msg;

    public Msg(byte[] msg) {
        this.msg = msg;
    }

    public byte[] getMsg() {
        return msg;
    }

    public void setMsg(byte[] msg) {
        this.msg = msg;
    }
}
//contains the information about each file descriptor
class fileDescriptor implements  Serializable {
    private fileID fd; //serverID
    private int clientID; //clientID
    private long posFromStart; //posFromStart
    private  int readPermission;
    private int writePermission;

    public fileDescriptor(fileID fd, int clientID,long posFromStart) {
        this.clientID = clientID;
        this.fd = fd;
        this.posFromStart = posFromStart;
    }

    public fileDescriptor(fileID fd, long posFromStart, int readPermission, int writePermission) {
        this.fd = fd;
        this.posFromStart = posFromStart;
        this.readPermission = readPermission;
        this.writePermission = writePermission;
    }

    public int getReadPermission() {
        return readPermission;
    }

    public void setReadPermission(int readPermission) {
        this.readPermission = readPermission;
    }

    public int getWritePermission() {
        return writePermission;
    }

    public void setWritePermission(int writePermission) {
        this.writePermission = writePermission;
    }

    public fileDescriptor(fileID fd, int clientID, long posFromStart, int readPermission, int writePermission) {
        this.fd = fd;
        this.clientID = clientID;
        this.posFromStart = posFromStart;
        this.readPermission = readPermission;
        this.writePermission = writePermission;
    }

    public int getClientID() {
        return clientID;
    }

    public void setClientID(int clientID) {
        this.clientID = clientID;
    }

    public fileID getFd() {
        return fd;
    }

    public void setFd(fileID fd) {
        this.fd = fd;
    }

    public long getPosFromStart() {
        return posFromStart;
    }

    public void setPosFromStart(long posFromStart) {
        this.posFromStart = posFromStart;
    }
}

//attrivutes for the file(size)
class fileAttributes implements Serializable {
    long size;
    ArrayList<Integer>flags;
    long timestampAttributes;
    long modificationTime;

    public fileAttributes(long size,long modificationTime) {
        this.size = size;
        this.modificationTime = modificationTime;
    }

    public fileAttributes(long size, ArrayList<Integer> flags) {
        this.size = size;
        this.flags = flags;
    }

    public fileAttributes(long size, ArrayList<Integer> flags, long timestampAttributes) {
        this.size = size;
        this.flags = flags;
        this.timestampAttributes = timestampAttributes;
    }

    public long getModificationTime() {
        return modificationTime;
    }

    public void setModificationTime(long modificationTime) {
        this.modificationTime = modificationTime;
    }

    public long getTimestampAttributes() {
        return timestampAttributes;
    }

    public void setTimestampAttributes(long timestampAttributes) {
        this.timestampAttributes = timestampAttributes;
    }

    public ArrayList<Integer> getFlags() {
        return flags;
    }

    public void setFlags(ArrayList<Integer> flags) {
        this.flags = flags;
    }

    public fileAttributes(long size) {
        this.size = size;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }
}


////SERVERRRRR BUFFERS////
class serversFdsInfo {
    File file;
    FileChannel fd;

    public serversFdsInfo(File file, FileChannel fd) {
        this.file = file;
        this.fd = fd;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public FileChannel getFd() {
        return fd;
    }

    public File getFile() {
        return file;
    }

    public void setFd(FileChannel fd) {
        this.fd = fd;
    }
}
class fileInformation {

    private String fname;
    private fileAttributes attributes;

//    private fileID idsToServer;

    public fileInformation(String fname, fileAttributes attributes) {
        this.fname = fname;
        this.attributes = attributes;
    }

    public String getFname() {
        return fname;
    }

    public void setFname(String fname) {
        this.fname = fname;
    }

    public fileAttributes getAttributes() {
        return attributes;
    }

    public void setAttributes(fileAttributes attributes) {
        this.attributes = attributes;
    }


}
class udpMessageMaxCapacityAnswer extends udpMessage implements Serializable {
//    private String fileName;
//    private ArrayList<Integer> flags;
    private fileID  fd; // maybe change it
    private int openClientInt;
    private fileAttributes attributes;

    public udpMessageMaxCapacityAnswer(String type) {
        super(type);
    }

    public udpMessageMaxCapacityAnswer(String type, fileID fd, int openClientInt, fileAttributes attributes) {
        super(type);
        this.fd = fd;
        this.openClientInt = openClientInt;
        this.attributes = attributes;
    }

    public int getOpenClientInt() {
        return openClientInt;
    }

    public void setOpenClientInt(int openClientInt) {
        this.openClientInt = openClientInt;
    }

    public fileAttributes getAttributes() {
        return attributes;
    }

    public void setAttributes(fileAttributes attributes) {
        this.attributes = attributes;
    }

    public fileID getFd() {
        return fd;
    }

    public void setFd(fileID fd) {
        this.fd = fd;
    }
}
class udpMessageDontKnowThisID extends udpMessage{
    private String xType;
    private fileDescriptor fd;
    private int typeID;

    public udpMessageDontKnowThisID(String type, String xType, fileDescriptor fd,int typeID) {
        super(type);
        this.xType = xType;
        this.fd = fd;
        this.typeID = typeID;
    }

    public int getTypeID() {
        return typeID;
    }

    public void setTypeID(int typeID) {
        this.typeID = typeID;
    }

    public String getxType() {
        return xType;
    }

    public void setxType(String xType) {
        this.xType = xType;
    }

    public fileDescriptor getFd() {
        return fd;
    }

    public void setFd(fileDescriptor fd) {
        this.fd = fd;
    }
}
class fileID implements Serializable{
    private int fd;
    private int session;

    public fileID(int fd, int session) {
        this.fd = fd;
        this.session = session;
    }

    public int getFd() {
        return fd;
    }

    public void setFd(int fd) {
        this.fd = fd;
    }

    public int getSession() {
        return session;
    }

    public void setSession(int session) {
        this.session = session;
    }
}
class locker {
    Object lock;
    int requests;

    public locker(Object lock, int requests) {
        this.lock = lock;
        this.requests = requests;
    }

    public Object getLock() {
        return lock;
    }

    public void setLock(Object lock) {
        this.lock = lock;
    }

    public int getRequests() {
        return requests;
    }

    public void setRequests(int requests) {
        this.requests = requests;
    }
}

class InstrumentationAgent {
    private static volatile Instrumentation globalInstrumentation;

    public static void premain(final String agentArgs, final Instrumentation inst) {
        globalInstrumentation = inst;
    }

    public static long getObjectSize(final Object object) {
        if (globalInstrumentation == null) {
            throw new IllegalStateException("Agent not initialized.");
        }
        return globalInstrumentation.getObjectSize(object);
    }
}

class Block implements Serializable{
    byte[] bytearray;
    int hasInfo;
    long blockTimeStamp;
    long modificationStamp;
    fileID fileInfo;
    long start;
    long end;

    public Block(int size) {
        this.bytearray = new byte[size];
        this.hasInfo = 0;
        this.blockTimeStamp = 0;
        this.modificationStamp = 0;
        this.start = 0;
        this.end = 0;
    }

    public long getStart() {
        return start;
    }

    public void setStart(long start) {
        this.start = start;
    }

    public long getEnd() {
        return end;
    }

    public void setEnd(long end) {
        this.end = end;
    }

    public fileID getFileInfo() {
        return fileInfo;
    }

    public void setFileInfo(fileID fileInfo) {
        this.fileInfo = fileInfo;
    }

    public long getModificationStamp() {
        return modificationStamp;
    }

    public void setModificationStamp(long modificationStamp) {
        this.modificationStamp = modificationStamp;
    }

    public long getBlockTimeStamp() {
        return blockTimeStamp;
    }

    public void setBlockTimeStamp(long blockTimeStamp) {
        this.blockTimeStamp = blockTimeStamp;
    }

    public int getHasInfo() {
        return hasInfo;
    }

    public void setHasInfo(int hasInfo) {
        this.hasInfo = hasInfo;
    }

    public byte[] getBytearray() {
        return bytearray;
    }

    public void setBytearray(byte[] bytearray) {
        this.bytearray = bytearray;
    }
}
