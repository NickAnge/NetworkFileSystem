import javax.swing.plaf.synth.SynthScrollBarUI;
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
    private static final int ERROR = -2;
    private int readInt,writeInt,openInt;
    private  ServerInfo server;
//    ServerInfo nikos;
    private DatagramSocket udpSocket;
    private CacheMemory cacheMemory;
    private HashMap<String, ArrayList<Integer> > idsMiddleware;
    private HashMap<Integer,udpMessage> openMsgs;
    private HashMap<Integer,udpMessageRead> readMsgs;
    private HashMap<Integer,udpMessageWrite> writeMsgs;
    private Thread clientThread;
    private Thread sendThread;
    private ArrayList<udpMessage> requests;

    //information middleware about file descriptors
    private HashMap<Integer,fileDescriptor>  middlewarefds;


    //structure for readMsgs coming from server;
    //blocking mutex
    public final Object readLock;
    public final Object lock;

    public NfsClient() {
        this.readInt = this.openInt = this.writeInt = -1; //  integers for duplicates
        this.idsMiddleware = new HashMap<>();
        ArrayList<Integer> openList = new ArrayList<>();
        this.idsMiddleware.put(open,openList);
        ArrayList<Integer> readList = new ArrayList<>();
        this.idsMiddleware.put(read,readList);
        ArrayList<Integer> writeList = new ArrayList<>();
        this.idsMiddleware.put(write,writeList);


        lock = new Object();
        readLock = new Object();

        //init buffers for open/read/write that the  middleware takes
        this.openMsgs = new HashMap<>();
        this.readMsgs = new HashMap<>();
        this.writeMsgs = new HashMap<>();
        this.middlewarefds = new HashMap<>();
        this.requests = new ArrayList<>();


        // read Msgs that middleware takes
//        readServer = new HashMap<>();
        clientThread = new Thread(new clientReceive());
        sendThread = new Thread(new clientSend());
    }

    @Override
    public int myNfs_init(String ipaddr, int port, int cacheBlocks, int blockSize, int freshT) {
        try {
            this.server = new ServerInfo(InetAddress.getByName(ipaddr),port);
            System.out.println(server.getServerIp());
            this.udpSocket = new DatagramSocket();
            this.cacheMemory = new CacheMemory(cacheBlocks, blockSize,freshT);
            sendThread.start();
            clientThread.start();
        } catch (UnknownHostException | SocketException e) {
            e.printStackTrace();
        }

        return 0;
    }

    @Override
    public int myNfs_open(String fName, ArrayList<Integer> flags) {

        openInt++;
        for(int i =0;i < flags.size();i++){
            System.out.println(flags.get(i));
        }
        udpMessageOpen openPacket = new udpMessageOpen(open,openInt,-1,fName,flags);
        requests.add(openPacket);

        synchronized (lock) {
            try {
                lock.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if(middlewarefds.get(openInt).getFd() < 0 ){
            return middlewarefds.get(openInt).getFd();
        }
        else {
            return openInt;
        }
    }

    @Override
    public int myNfs_read(int fd, String buff, int n) {

//
//        System.out.println("requested fd" + fd);
//        System.out.println("clientID" + middlewarefds.get(fd).getClientID());
//        System.out.println(middlewarefds.get(fd).getFd());
//        System.out.println(middlewarefds.get(fd).getPosFromStart());
////        System.out.println(middlewarefds.get(fd).getFlags());
//        System.out.println(middlewarefds.get(fd).getReadPermission());
        if(middlewarefds.get(fd).getReadPermission() <  0){
            return  ERROR;
        }

        readInt++;
        Msg msg = new Msg(buff);
        fileDescriptor filed = middlewarefds.get(fd);
        udpMessageRead readMsg = new udpMessageRead(read,readInt,filed,msg,n);
        requests.add(readMsg);

        synchronized (readLock) {
            try {
                readLock.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        System.out.println("wanted file read"+readMsgs.get(readInt).getReadMsg().msg);



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

        //remove this file descriptor for the
        middlewarefds.remove(fd);
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
//        System.out.println("SEND PACKET");
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
//            System.out.println(server.getServerIp());
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, server.getServerIp(), server.getPort());

//            udpSocket.setSoTimeout(1);
            udpSocket.receive(packet);
//            System.out.println(packet.getLength());
            ByteArrayInputStream baos = new ByteArrayInputStream(buffer);
            ObjectInputStream oos = new ObjectInputStream(baos);
            udpMessage receiveMessage = (udpMessage) oos.readObject();
//            System.out.println("ELAVA" + receiveMessage.getType());

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
                udpMessage receiveMessage = receiveUdpMessages();

                if(receiveMessage != null){
                    if(receiveMessage.getType().equals(open)){
                        udpMessageOpen returnMsg = (udpMessageOpen) receiveMessage;
//
                        if(!idsMiddleware.get(returnMsg.getType()).contains(returnMsg.getOpenClientInt())){
                            System.out.println("Added open fd");
                            //added to buffer for duplicates
                            idsMiddleware.get(returnMsg.getType()).add(returnMsg.getOpenClientInt());
                            //informations that the the middleware holds and sends every time to server to read , like file descriptor
//                            System.out.println();
                            int readPermission = -1;
                            int writePerminssion = -1;
                            if(returnMsg.getFlags().contains(O_RDONLY) || returnMsg.getFlags().contains(O_RDWR)){
                                System.out.println("takes read permission");
                                readPermission = 1;
                            }
                            if(returnMsg.getFlags().contains(O_RDWR) || returnMsg.getFlags().contains(O_WRONLY)){
                                System.out.println("Takes write permission");
                                writePerminssion =1;
                            }
                            fileDescriptor newfd = new fileDescriptor(returnMsg.getFd(),returnMsg.getOpenClientInt(),0);

                            newfd.setReadPermission(readPermission);
                            newfd.setWritePermission(writePerminssion);

                            System.out.println("readPerm" + newfd.getReadPermission());
                            System.out.println("writePerm" + newfd.getWritePermission());

                            middlewarefds.put(returnMsg.getOpenClientInt(),newfd);

//                            removeRequest(requests,returnMsg.getOpenClientInt());
                            synchronized (lock){
                                lock.notify();
                            }
                        }
//                        System.out.println("File descriptor from server : "+returnMsg.getFd());
//                        System.out.println("CLient id " + returnMsg.getOpenClientInt() );
                    }
                    if(receiveMessage.getType().equals(read)){
                        udpMessageRead returnMsg = (udpMessageRead) receiveMessage;
                        if(!idsMiddleware.get(returnMsg.getType()).contains(returnMsg.getReadClientInt())){
                            System.out.println("Added read Client ID ");
                            idsMiddleware.get(returnMsg.getType()).add(returnMsg.getReadClientInt());

//                            System.out.println("id" + returnMsg.getReadClientInt());
//                            System.out.println("READ"+returnMsg.getFd().getReadPermission());
//                            System.out.println("WRITE"+returnMsg.getFd().getWritePermission());
                            fileDescriptor newfd = new fileDescriptor(returnMsg.getFd().getFd(),returnMsg.getFd().getClientID(),returnMsg.getFd().getPosFromStart(),returnMsg.getFd().getReadPermission(),returnMsg.getFd().getWritePermission());

                            //refresh fd information to client after server-Msg returned
                            middlewarefds.put(newfd.getClientID(),newfd);

                            //hold read msgs here until app take them
                            readMsgs.put(returnMsg.getReadClientInt(),returnMsg);

                            synchronized (readLock){
                                readLock.notify();
                            }

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

    class clientSend extends Thread {
        public void run() {
            while(true){
                System.out.print("");
                if(requests.size() > 0) {
                    System.out.println("fafasfaSIZE" + requests.size());

                    udpMessage nextRequest = requests.get(0);
                    requests.remove(0);
                    System.out.println(requests.size());
                    if(nextRequest.getType().equals(open)){
                        System.out.println("mphka wdw");
                        udpMessageOpen openRequest = (udpMessageOpen) nextRequest;
                        sendUdpMessage(openRequest,openRequest.getOpenClientInt());
                    }
                    else if(nextRequest.getType().equals(read)){
                        udpMessageRead readRequest = (udpMessageRead) nextRequest;
                        sendUdpMessage(readRequest,readRequest.getReadClientInt());
                    }
                    else if(nextRequest.getType().equals(write)){
                        udpMessageWrite writeRequest = (udpMessageWrite) nextRequest;
                        sendUdpMessage(writeRequest,writeRequest.getWriteClientInt());
                    }


                }
            }
        }
    }

    public void removeRequest(ArrayList<udpMessage> requests,int removeId){
        int j = 1;
        for(int i =0; i < requests.size();i++){
            if(requests.get(i).getType().equals(open)){
                udpMessageOpen open = (udpMessageOpen) requests.get(i);
                if(open.getOpenClientInt() == removeId){
                    j = i;
                    break;
                }
            }
            else if (requests.get(i).getType().equals(read)){
                udpMessageRead read = (udpMessageRead) requests.get(i);
                if(read.getReadClientInt() == removeId){
                    j = i;
                    break;
                }
            }
            else if(requests.get(i).getType().equals(write)){
                udpMessageWrite write = (udpMessageWrite) requests.get(i);
                if(write.getWriteClientInt() == removeId){
                    j = i;
                    break;
                }
            }
        }
        //delete that request
        requests.remove(j);
    }
}
