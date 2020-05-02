import javax.xml.crypto.Data;
import java.io.*;
import java.net.*;
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
    private HashMap<Integer,udpMessage> openMsgs;
    private HashMap<Integer,udpMessage> readMsgs;
    private HashMap<Integer,udpMessage> writeMsgs;
    private Thread clientThread;


    //information middleware about file descriptors
    private HashMap<Integer,fileDescriptor>  middlewarefds;


    public NfsClient() {
        this.readInt = this.openInt = this.writeInt = -1; //  integers for duplicates
        this.idsMiddleware = new HashMap<>();
        ArrayList<Integer> openList = new ArrayList<>();
        this.idsMiddleware.put(open,openList);
        ArrayList<Integer> readList = new ArrayList<>();
        this.idsMiddleware.put(read,readList);
        ArrayList<Integer> writeList = new ArrayList<>();
        this.idsMiddleware.put(write,writeList);
        //init buffers for open/read/write that the  middleware takes
        this.openMsgs = new HashMap<>();
        this.readMsgs = new HashMap<>();
        this.writeMsgs = new HashMap<>();
        this.middlewarefds = new HashMap<>();
        clientThread = new Thread(new clientReceive());


    }

    @Override
    public int myNfs_init(String ipaddr, int port, int cacheBlocks, int blockSize, int freshT) {
        try {
            this.server = new ServerInfo(InetAddress.getByName(ipaddr),port);
            System.out.println(server.getServerIp());
            this.udpSocket = new DatagramSocket();
            this.cacheMemory = new CacheMemory(cacheBlocks, blockSize,freshT);
            clientThread.start();

        } catch (UnknownHostException | SocketException e) {
            e.printStackTrace();
        }

        return 0;
    }

    @Override
    public int myNfs_open(String fName, EnumSet<Flag> flags) {

        openInt++;
        udpMessageOpen openPacket = new udpMessageOpen(open,openInt,-1,fName,flags);
        sendUdpMessage(openPacket,openInt);

        return openInt;
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

    public void sendUdpMessage(udpMessage msg,int clientId){

        udpMessage msg2 = null;
        ObjectOutputStream oos = null;
        try {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        oos = new ObjectOutputStream(baos);
        oos.writeObject(msg);
        byte[] byteMsg = baos.toByteArray();
        DatagramPacket packet = new DatagramPacket(byteMsg, byteMsg.length,server.getServerIp(), server.port);

        while(!idsMiddleware.get(msg.getType()).contains(clientId)){
            //resend packet until server answer
            udpSocket.send(packet);

        }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
    public udpMessage receiveUdpMessages() {
        byte[] buffer = new byte[1024];
        try {
            System.out.println(server.getServerIp());
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, server.getServerIp(), server.getPort());
            udpSocket.setSoTimeout(1);
            udpSocket.receive(packet);
            System.out.println(packet.getLength());
            ByteArrayInputStream baos = new ByteArrayInputStream(buffer);
            ObjectInputStream oos = new ObjectInputStream(baos);
            udpMessage receiveMessage = (udpMessage) oos.readObject();
            System.out.println("ELAVA" + receiveMessage.getType());

            return receiveMessage;
        } catch (IOException | ClassNotFoundException e) {
//            e.printStackTrace();
            return null;
        }
    }
//    }

        //Thread of Middleware
    class clientReceive extends  Thread {
        public void run() {
            while(true){
                System.out.println("paw gia receive");
                udpMessage receiveMessage = receiveUdpMessages();

                if(receiveMessage != null){
                    if(receiveMessage.getType().equals(open)){
                        udpMessageOpen returnMsg = (udpMessageOpen) receiveMessage;
                        if(!idsMiddleware.get(returnMsg.getType()).contains(returnMsg.getOpenClientInt())){
                            System.out.println("Added open fd");
                            idsMiddleware.get(returnMsg.getType()).add(returnMsg.getOpenClientInt());
                            //informations that the the middleware holds and sends every time to server to read , like file descriptor

                            fileDescriptor newfd = new fileDescriptor(returnMsg.getFd(),0);
                            middlewarefds.put(returnMsg.getOpenClientInt(),newfd);

                        }
                        System.out.println("File descriptor from server : "+returnMsg.getFd());
                        System.out.println("CLient id " + returnMsg.getOpenClientInt() );
                    }
                    if(receiveMessage.getType().equals(read)){
                        udpMessageRead returnMsg = (udpMessageRead) receiveMessage;
                        if(!idsMiddleware.get(returnMsg.getType()).contains(returnMsg.getReadClientInt())){
                            System.out.println("Added read Client ID ");
                            idsMiddleware.get(returnMsg).add(returnMsg.getReadClientInt());
                        }
                    }
                    else if (receiveMessage.getType().equals(write)) {
                        udpMessageWrite returnMsg = (udpMessageWrite) receiveMessage;
                        if(!idsMiddleware.get(returnMsg.getType()).contains(returnMsg.getWriteClientInt())){
                            System.out.println("Added write client ID");

                            idsMiddleware.get(returnMsg).add(returnMsg.getWriteClientInt());

                        }
                    }
                }

            }

        }
    }
}
