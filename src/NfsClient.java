import javax.swing.plaf.synth.SynthScrollBarUI;
import javax.xml.crypto.Data;
import java.io.*;
import java.net.*;
import java.sql.ClientInfoStatus;
import java.util.*;
import java.util.jar.Attributes;

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

    public String fileName;

    //structure for readMsgs coming from server;


    //blocking mutex
    public final Object readLock;
    public final Object lock;
    public final Object writeLock;


    //new first time
    private  HashMap<fileID,fileInformation> filesInMiddleware;
    //information middleware about file descriptors

    private HashMap<Integer,clientFileInformation>  middlewarefds;

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
        writeLock = new Object();
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


        //first time
        filesInMiddleware = new HashMap<>();
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
        int sendServer = -1;
        fileName = fName;

        Set<fileID> keys = filesInMiddleware.keySet();
        for(fileID temp: keys) {
            fileInformation file = filesInMiddleware.get(temp);

            if (file.getFname().equals(fName)) {

                sendServer = 1;
                if (flags.contains(O_EXCL)) {
                    //an yparxei
                    return E_EXIST;
                }
                if(flags.contains(O_TRUNC)){
                    if(file.getFlags().contains(O_RDONLY)){
                        return  ERROR;
                    }
                    else{
                        sendServer=-1;
                        break;
                    }
                }
                int check = checkMode(flags, file.getFlags());
                if (check < 0) {
                    return ERROR;
                }

                fileAttributes attributes = new fileAttributes(file.getAttributes().getSize());
                int readPerm = -1;
                int writePerm = -1;
                if (flags.contains(O_RDONLY) || flags.contains(O_RDWR)) {
                    readPerm = 1;
                }
                if (flags.contains(O_WRONLY) || flags.contains(O_RDWR)) {
                    writePerm = 1;
                }

                fileDescriptor newFd = new fileDescriptor(temp, 0, readPerm, writePerm);
                clientFileInformation newInfo = new clientFileInformation(newFd, attributes);
                middlewarefds.put(openInt, newInfo);
                return openInt;
            }
        }

        if(sendServer < 0){
            //send request
            fileAttributes attributes = new fileAttributes(0);
            fileID fd = new fileID(-1,-1);
            udpMessageOpen openPacket = new udpMessageOpen(open,openInt,fd,fName,flags,attributes);
            requests.add(openPacket);


            block(lock,open,openInt);
            System.out.println("OPENID"+ openInt);
            System.out.println(middlewarefds.get(openInt).getFd().getFd().getFd());
            if(middlewarefds.get(openInt).getFd().getFd().getFd() < 0 ){

                return middlewarefds.get(openInt).getFd().getFd().getFd();
            }
            else {
                return openInt;
            }
        }

        return openInt;

    }

    @Override
    public int myNfs_read(int fd, Msg buff, int n) {

//
//        System.out.println("requested fd" + fd);
//        System.out.println("clientID" + middlewarefds.get(fd).getClientID());
//        System.out.println(middlewarefds.get(fd).getFd());
//        System.out.println(middlewarefds.get(fd).getPosFromStart());
////        System.out.println(middlewarefds.get(fd).getFlags());
//        System.out.println(middlewarefds.get(fd).getReadPermission());
        if(!middlewarefds.containsKey(fd)){
            return ERROR;
        }
        if(middlewarefds.get(fd).getFd().getReadPermission() <  0){
            return  ERROR;
        }

        readInt++;
        Msg msg = new Msg(null);

        fileDescriptor filed = middlewarefds.get(fd).getFd();
        fileAttributes attributes = middlewarefds.get(fd).attributes;
        udpMessageRead readMsg = new udpMessageRead(read,readInt,filed,msg,n,attributes);
        requests.add(readMsg);


        while(true) {
            synchronized (readLock) {
                try {
                    readLock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            if(idsMiddleware.get(read).contains(readInt)){
                break;
            }
        }

        buff.setMsg(readMsgs.get(readInt).getReadMsg().msg);
        //remove from read messages when app takes them
        System.out.println("wanted file read"+ readMsgs.get(readInt).getReadMsg());

        readMsgs.remove(readInt);

        return 1; // success
    }

    @Override
    public int myNfs_write(int fd, String buff, int n) {

        if(!middlewarefds.containsKey(fd)){
            return ERROR;
        }
        if(middlewarefds.get(fd).getFd().getWritePermission() <  0){
            return  ERROR;
        }


        writeInt++;
        Msg msg = new Msg(buff);
        fileDescriptor filed = middlewarefds.get(fd).getFd();
        fileAttributes attributes = middlewarefds.get(fd).getAttributes();

        udpMessageWrite writeMsg = new udpMessageWrite("Write",writeInt,filed,msg.getMsg().length(),msg,attributes);

        requests.add(writeMsg);

        while(true) {
            synchronized (writeLock) {
                try {
                    writeLock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            if(idsMiddleware.get(write).contains(writeInt)){
                break;
            }
        }

        System.out.println("Write completed");
        return 1;
    }

    @Override
    public int myNfs_seek(int fd, int pos, int whence) {
        if(!middlewarefds.containsKey(fd)){
            return ERROR;
        }
        int temp = -1;
        long newStart = -1;

        if(whence == SEEK_SET){
            //SEEK_SET The offset is set to offset bytes.
            temp =1;
            newStart = pos;
            middlewarefds.get(fd).getFd().setPosFromStart(newStart);

        }
        else if(whence == SEEK_CUR){
            temp =1;
            //SEEK_CURR  The offset is set to its current location plus offset bytes.
            newStart = middlewarefds.get(fd).getFd().getPosFromStart() + pos;
            middlewarefds.get(fd).getFd().setPosFromStart(newStart);

        }
        else if (whence == SEEK_END){
            temp =1;
            //SEEK_END  The offset is set to the size of the file plus offset bytes.
            newStart = middlewarefds.get(fd).getAttributes().getSize() + pos;
            middlewarefds.get(fd).getFd().setPosFromStart(newStart);

        }
        return temp;
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

                            if(returnMsg.getFd().getFd() > 0){
                                if(returnMsg.getFlags().contains(O_RDONLY)) {
                                    System.out.println("takes read permission");
                                    readPermission = 1;
                                }else if(returnMsg.getFlags().contains(O_WRONLY)){
                                    System.out.println("Takes write permission");
                                    writePerminssion =1;
                                }
                                else {
                                    readPermission = 1;
                                    writePerminssion = 1 ;
                                }
                                fileDescriptor newfd = new fileDescriptor(returnMsg.getFd(),0,readPermission,writePerminssion);
                                fileAttributes attributes = new fileAttributes(returnMsg.getAttributes().getSize());

                                System.out.println("readPerm" + newfd.getReadPermission());
                                System.out.println("writePerm" + newfd.getWritePermission());

                                System.out.println("SIZE OF FILE" + attributes.getSize());

                                clientFileInformation newInfo = new clientFileInformation(newfd,attributes);

                                System.out.println("ID "+ newInfo.getFd().getFd().getFd());
                                System.out.println("openid" + newfd.getClientID());
                                middlewarefds.put(returnMsg.getOpenClientInt(),newInfo);
                                fileInformation finfo = new fileInformation(fileName,returnMsg.getAttributes(),returnMsg.getFlags());

                                filesInMiddleware.put(returnMsg.getFd(),finfo);


                            }
                            synchronized (lock){
                                lock.notify();
                            }
                        }
//
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
                            fileAttributes attributes = new fileAttributes(returnMsg.getAttributes().getSize());

                            clientFileInformation newInfo = new clientFileInformation(newfd,attributes);
                            middlewarefds.put(newfd.getClientID(),newInfo);


                            System.out.println("SIZE OF FILE" + attributes.getSize());

                            //hold read msgs here until app take them
                            System.out.println("Return Message" + returnMsg.getReadMsg().getMsg());
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

                            idsMiddleware.get(returnMsg.getType()).add(returnMsg.getWriteClientInt());

                            fileDescriptor newfd = new fileDescriptor(returnMsg.getFd().getFd(),returnMsg.getWriteClientInt(),returnMsg.getFd().getPosFromStart(),returnMsg.getFd().getReadPermission(),returnMsg.getFd().getWritePermission());

                            fileAttributes attributes = new fileAttributes(returnMsg.getAttributes().getSize());

                            clientFileInformation newInfo = new clientFileInformation(newfd,attributes);

                            middlewarefds.put(newfd.getClientID(),newInfo);
                            //refresh file descriptors information for new
//                            middlewarefds.put(returnMsg.getFd().getClientID(),fd);


                            synchronized (writeLock){
                                writeLock.notify();
                            }


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

    public void block(Object lock,String type,int id){

        while(true){
            synchronized (lock) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            if(idsMiddleware.get(type).contains(id)){
                break;
            }
        }
    }

    public int checkMode(ArrayList<Integer> wantedFlags, ArrayList<Integer> existedFlags){

        if(wantedFlags.contains(O_RDWR)) {
            if(existedFlags.contains(O_RDONLY) || existedFlags.contains(O_WRONLY)){
                return ERROR;
            }
        }
        else if(wantedFlags.contains(O_RDONLY)){
            if(existedFlags.contains(O_WRONLY)){
                return ERROR;
            }
        }
        else if(wantedFlags.contains(O_WRONLY)){
            if(existedFlags.contains(O_RDONLY)){
                return ERROR;
            }
        }

        return 1;
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
