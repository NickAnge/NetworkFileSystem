import javax.xml.crypto.Data;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;

public class NfsClient implements   nfsAPI{
    private static final String open = "Open";
    private static final String read = "Read";
    private static final String write = "Write";
    private int readInt,writeInt,openInt;
    private  ServerInfo server;
//    ServerInfo nikos;
    private DatagramSocket udpSocket;
    private CacheMemory cacheMemory;
    private HashMap<String, ArrayList<Integer> > idsMiddleware;

    public NfsClient() {
        this.readInt = this.openInt = this.writeInt = -1; //  integers for duplicates
        this.idsMiddleware = new HashMap<>();
        ArrayList<Integer> openList = new ArrayList<>();
        this.idsMiddleware.put(open,openList);
        ArrayList<Integer> readList = new ArrayList<>();
        this.idsMiddleware.put(read,readList);
        ArrayList<Integer> writeList = new ArrayList<>();
        this.idsMiddleware.put(write,writeList);


    }

    //TODO init communication information(structure for server information) with server and information of cache(structure)
    @Override
    public int myNfs_init(String ipaddr, int port, int cacheBlocks, int blockSize, int freshT) {
        try {
            this.server = new ServerInfo(InetAddress.getByName(ipaddr),port);
            this.udpSocket = new DatagramSocket();
            this.cacheMemory = new CacheMemory(cacheBlocks, blockSize,freshT);
        } catch (UnknownHostException | SocketException e) {
            e.printStackTrace();
        }

        return 0;
    }

    @Override
    public int myNfs_open(String fName, EnumSet<Flag> flags) {

//        System.out.println("Flags"+ flags);
//        create_look("takhs",O_CREAT | );

        if (flags.contains(Flag.O_CREAT)) {
            System.out.println("CREATE");
        }


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


    public void create_look(String filename, int flags){

    }
    //This function will check every time if we have received the message
    public  int checkDuplicates(int fileId,String type){
        boolean check = this.idsMiddleware.get(type).contains(fileId);
        if(check){
            return 1; // already received
        }
        return 0;//failure.
    }
    public void receiveUdpMessages() {


    }
    //Thread of Middleware
    class client extends  Thread {
        public void run() {

            while(true){


            }

        }
    }
}
