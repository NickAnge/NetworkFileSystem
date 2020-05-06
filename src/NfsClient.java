import org.omg.PortableInterceptor.SYSTEM_EXCEPTION;

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
    private static final String max_capacity = "MAX_CAPACITY";
    public static final String NO_THIS_ID = "DONT_KNOW_THIS_ID";


    private static final int ERROR = -2;
    private int readInt,writeInt,openInt,openAck;
    private  ServerInfo server;
//    ServerInfo nikos;
    private DatagramSocket udpSocket;
    private CacheMemory cacheMemory;


    private HashMap<String, ArrayList<Integer> > idsMiddleware;

    private HashMap<Integer,udpMessageOpen> openMsgs;
    private HashMap<Integer,udpMessageRead> readMsgs;
    private HashMap<Integer,udpMessageWrite> writeMsgs;
    private HashMap<Integer,udpMessageMaxCapacityAnswer> serverAnswers;

    private Thread clientThread;
    private Thread sendThread;
    private ArrayList<udpMessage> requests;

    public String fileName;

    //structure for readMsgs coming from server;


    //blocking mutex
    public final Object readLock;
    public final Object lock;
    public final Object writeLock;


    //waitinf integers;
    int readWaiting;
    int openWaiting;
    int writeWaiting;

    //new first time
    private  HashMap<fileID,fileInformation> filesInMiddleware;
    //information middleware about file descriptors
    private HashMap<Integer,fileDescriptor>  middlewarefds;

    //if no iD buffers
    HashMap<Integer,ArrayList<udpMessageRead>> noIdreadMessages;
    HashMap<Integer,ArrayList<udpMessageWrite>> noIdwriteMessages;


    public NfsClient() {
        this.readInt = this.openInt = this.writeInt = this.openAck = -1; //  integers for duplicates
        this.idsMiddleware = new HashMap<>();
        ArrayList<Integer> openList = new ArrayList<>();
        this.idsMiddleware.put(open,openList);
        ArrayList<Integer> readList = new ArrayList<>();
        this.idsMiddleware.put(read,readList);
        ArrayList<Integer> writeList = new ArrayList<>();
        this.idsMiddleware.put(write,writeList);
        ArrayList<Integer> max_capacity_List = new ArrayList<>();
        this.idsMiddleware.put(max_capacity,max_capacity_List);
        ArrayList<Integer> noID = new ArrayList<>();
        this.idsMiddleware.put(NO_THIS_ID,noID);

//        ArrayList<udpMessageRead> listread = new ArrayList<>();
        noIdreadMessages = new HashMap<>();

        lock = new Object();
        openWaiting = 0;
        readLock = new Object();
        readWaiting = 0;
        writeLock = new Object();
        writeWaiting = 0;
        //init buffers for open/read/write that the  middleware takes
        this.openMsgs = new HashMap<>();
        this.readMsgs = new HashMap<>();
        this.writeMsgs = new HashMap<>();
        this.middlewarefds = new HashMap<>();
        this.requests = new ArrayList<>();
        this.serverAnswers = new HashMap<>();
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
                    if(file.getAttributes().getFlags().contains(O_RDONLY)){
                        return  ERROR;
                    }
                    else{
                        sendServer=-1;
                        break;
                    }
                }
                int check = checkMode(flags, file.getAttributes().getFlags());
                if (check < 0) {
                    return ERROR;
                }

                fileAttributes attributes = new fileAttributes(file.getAttributes().getSize());
                int readPerm = -1;
                int writePerm = -1;

                if (flags.contains(O_RDONLY)) {
                    readPerm = 1;
                }
                else if (flags.contains(O_WRONLY)){
                    writePerm = 1;
                }
                else {
                    writePerm = 1;
                    readPerm =1;
                }
                fileDescriptor newFd = new fileDescriptor(temp,openInt ,0,readPerm,writePerm);
//                clientFileInformation newInfo = new clientFileInformation(newFd);
                middlewarefds.put(openInt, newFd);
                ArrayList<udpMessageRead> readList = new ArrayList<>();
                noIdreadMessages.put(openInt,readList);
                return openInt;
            }
        }

        if(sendServer < 0){
            //send request
            fileAttributes attributes = new fileAttributes(0);
            fileID fd = new fileID(-1,-1);
            openAck++;
            System.out.println("OPEN_ACK"+openAck);
            fileDescriptor fd2 = new fileDescriptor(fd,openInt,0);
            udpMessage openPacket = new udpMessageOpen(open,fName,flags,openAck,attributes,fd2);
            requests.add(openPacket);

            openWaiting= 1;
            block(lock,open,openInt);
            openWaiting = 0;
            System.out.println("OPENID"+ openInt);
//            System.out.println(middlewarefds.get(openInt).getFd().getFd().getFd());
            System.out.println("open" + openMsgs.get(openInt).getFiled().getFd().getFd());

            if(openMsgs.containsKey(openInt)){
                System.out.println("ERRor");
                if(openMsgs.get(openInt).getFiled().getFd().getFd() < 0){
                    System.out.println("ERRor");

                    int ret = openMsgs.get(openInt).getFiled().getFd().getFd();
                    openMsgs.remove(openInt);
                    return  ret;
                }
                else {
                    openMsgs.remove(openInt);
                    ArrayList<udpMessageRead> readList = new ArrayList<>();
                    noIdreadMessages.put(openInt,readList);
                    return openInt;
                }

            }
            else if(serverAnswers.containsKey(openInt)){
                int ret = serverAnswers.get(openInt).getFd().getFd();
                serverAnswers.remove(openInt);
                return ret;
            }
        }

        return openInt;

    }

    @Override
    public int myNfs_read(int fd, Msg buff, int n) {

//
        System.out.println("requested fd" + fd);
//        System.out.println("PosfromStart : " + middlewarefds.get(fd).getFd().getPosFromStart());
//        System.out.println(middlewarefds.get(fd).getFd());
//        System.out.println(middlewarefds.get(fd).getPosFromStart());
////        System.out.println(middlewarefds.get(fd).getFlags());
//        System.out.println(middlewarefds.get(fd).getReadPermission());
        if(!middlewarefds.containsKey(fd)){
            return ERROR;
        }
        if(middlewarefds.get(fd).getReadPermission() <  0){
            return  ERROR;
        }

        readInt++;

        Msg msg = new Msg(null);

        fileDescriptor filed = middlewarefds.get(fd);


//        fileAttributes attributes = filesInMiddleware.get(filed.getFd()).getAttributes();
        fileInformation file = findFile(filed.getFd());

        if(file == null){
            return ERROR;
        }
        fileAttributes attributes = file.getAttributes();

        udpMessageRead readMsg = new udpMessageRead(read,readInt,filed,msg,n,attributes);

        requests.add(readMsg);

        readWaiting = 1;
        block(readLock,read,readInt);
        readWaiting = 0;

        buff.setMsg(readMsgs.get(readInt).getReadMsg().msg);
        //remove from read messages when app takes them
        System.out.println("wanted file read"+ readMsgs.get(readInt).getReadMsg());

        readMsgs.remove(readInt);

        return buff.getMsg().length(); // success
    }

    @Override
    public int myNfs_write(int fd, String buff, int n) {

//        if(!middlewarefds.containsKey(fd)){
//            return ERROR;
//        }
//        if(middlewarefds.get(fd).getFd().getWritePermission() <  0){
//            return  ERROR;
//        }
//
//
//        writeInt++;
//        Msg msg = new Msg(buff);
//        fileDescriptor filed = middlewarefds.get(fd).getFd();
//        fileAttributes attributes = middlewarefds.get(fd).getAttributes();
//
//        udpMessageWrite writeMsg = new udpMessageWrite("Write",writeInt,filed,msg.getMsg().length(),msg,attributes);
//
//        requests.add(writeMsg);
//
//        while(true) {
//            synchronized (writeLock) {
//                try {
//                    writeLock.wait();
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//            }
//            if(idsMiddleware.get(write).contains(writeInt)){
//                break;
//            }
//        }
//
//        System.out.println("Write completed");
        return 1;
    }

    @Override
    public int myNfs_seek(int fd, int pos, int whence) {
//        if(!middlewarefds.containsKey(fd)){
//            return ERROR;
//        }
//        int temp = -1;
//        long newStart = -1;
//
//        if(whence == SEEK_SET){
//            //SEEK_SET The offset is set to offset bytes.
//            temp =1;
//            newStart = pos;
//            middlewarefds.get(fd).getFd().setPosFromStart(newStart);
//
//        }
//        else if(whence == SEEK_CUR){
//            temp =1;
//            //SEEK_CURR  The offset is set to its current location plus offset bytes.
//            newStart = middlewarefds.get(fd).getFd().getPosFromStart() + pos;
//            middlewarefds.get(fd).getFd().setPosFromStart(newStart);
//
//        }
//        else if (whence == SEEK_END){
//            temp =1;
//            //SEEK_END  The offset is set to the size of the file plus offset bytes.
//            newStart = middlewarefds.get(fd).getAttributes().getSize() + pos;
//            middlewarefds.get(fd).getFd().setPosFromStart(newStart);
//
//        }
        return 1;
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

        int send =0;
        udpMessage msg2 = null;
        ObjectOutputStream oos = null;
        try {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        oos = new ObjectOutputStream(baos);
        oos.writeObject(msg);
        byte[] byteMsg = baos.toByteArray();
        DatagramPacket packet = new DatagramPacket(byteMsg, byteMsg.length,server.getServerIp(), server.port);
//        System.out.println("SEND PACKET");
        while(!idsMiddleware.get(msg.getType()).contains(clientId) && !idsMiddleware.get(max_capacity).contains(clientId) && !idsMiddleware.get(NO_THIS_ID).contains(clientId)){
            //resend packet until server answer
//            System.out.println("reseend");s
            send++;
            udpSocket.send(packet);
        }

        if(send == 0){
            while(!idsMiddleware.get(msg.getType()).contains(clientId)){
                //resend packet until server answer
//            System.out.println("reseend");s
                send++;
                udpSocket.send(packet);
            }
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
                        if(!idsMiddleware.get(returnMsg.getType()).contains(returnMsg.getOpenACK())){
                            System.out.println("Added open fd");
                            //added to buffer for duplicates
                            idsMiddleware.get(returnMsg.getType()).add(returnMsg.getOpenACK());
                            //informations that the the middleware holds and sends every time to server to read , like file descriptor

                            int readPermission = -1;
                            int writePerminssion = -1;
                            System.out.println("FDr"+returnMsg.getFiled().getFd().getFd());
                            System.out.println("openid"+returnMsg.getOpenClientInt());
                            System.out.println("openid"+returnMsg.getOpenACK());

                            if(returnMsg.getFiled().getFd().getFd() > 0) {
                                if (returnMsg.getFlags().contains(O_RDONLY)) {
                                    System.out.println("takes read permission");
                                    readPermission = 1;
                                } else if (returnMsg.getFlags().contains(O_WRONLY)) {
                                    System.out.println("Takes write permission");
                                    writePerminssion = 1;
                                } else {
                                    readPermission = 1;
                                    writePerminssion = 1;
                                }

                                fileDescriptor newfd = new fileDescriptor(returnMsg.getFiled().getFd(),returnMsg.getOpenClientInt(),returnMsg.getFiled().getPosFromStart(),readPermission,writePerminssion);

                                fileAttributes attributes = new fileAttributes(returnMsg.getAttributes().getSize(),returnMsg.getAttributes().getFlags());

                                System.out.println("readPerm" + newfd.getReadPermission());
                                System.out.println("writePerm" + newfd.getWritePermission());

                                System.out.println("SIZE OF FILE" + attributes.getSize());

//                                clientFileInformation newInfo = new clientFileInformation(newfd,attributes);


//                                middlewarefds.put(returnMsg.getOpenClientInt(),newInfo);
                                middlewarefds.put(returnMsg.getOpenClientInt(),newfd);

                                if(returnMsg.getFiled().getFd().getFd() >  0){
                                    System.out.println("ID "+ newfd.getFd().getFd());
                                    System.out.println("Session" + newfd.getFd().getSession());
                                    System.out.println("openid" + newfd.getClientID());

                                    fileInformation finfo = new fileInformation(fileName,attributes);
                                    filesInMiddleware.put(returnMsg.getFiled().getFd(),finfo);
                                }

                                if(noIdreadMessages.containsKey(returnMsg.getOpenClientInt())){
//                                idsMiddleware.get(NO_THIS_ID).remove(noIdreadMessages.get(returnMsg.getOpenClientInt()).get(0).getReadClientInt());
                                    for(int i =0 ; i < noIdreadMessages.get(returnMsg.getOpenClientInt()).size();i++){
                                        noIdreadMessages.get(returnMsg.getOpenClientInt()).get(i).setFd(newfd);
                                    }
                                    requests.addAll(noIdreadMessages.get(returnMsg.getOpenClientInt()));
                                    noIdreadMessages.get(returnMsg.getOpenClientInt()).clear();
                                }
                            }


                            openMsgs.put(returnMsg.getOpenClientInt(),returnMsg);


                            synchronized (lock){
                                lock.notify();
                            }
                        }
//
                    }
                    else if(receiveMessage.getType().equals(read)){
                        udpMessageRead returnMsg = (udpMessageRead) receiveMessage;
                        if(!idsMiddleware.get(returnMsg.getType()).contains(returnMsg.getReadClientInt())){
                            System.out.println("Added read Client ID ");
                            idsMiddleware.get(returnMsg.getType()).add(returnMsg.getReadClientInt());
//
//                            System.out.println("id" + returnMsg.getReadClientInt());
//                            System.out.println("READ"+returnMsg.getFd().getReadPermission());
//                            System.out.println("WRITE"+returnMsg.getFd().getWritePermission());

//                            fileDescriptor newfd = new fileDescriptor(returnMsg.getFd().getFd(),returnMsg.getFd().getClientID(),returnMsg.getFd().getPosFromStart(),returnMsg.getFd().getReadPermission(),returnMsg.getFd().getWritePermission());
                            fileDescriptor newfd = new fileDescriptor(returnMsg.getFd().getFd(),returnMsg.getFd().getClientID(),returnMsg.getFd().getPosFromStart());

//                            //refresh fd information to client after server-Msg returned
                            fileAttributes attributes = new fileAttributes(returnMsg.getAttributes().getSize(),returnMsg.getAttributes().getFlags());

//                            clientFileInformation newInfo = new clientFileInformation(newfd,attributes,middlewarefds.get(newfd.getClientID()).getReadPermission(),middlewarefds.get(newfd.getClientID()).getWritePermission());
                            System.out.println("id " + newfd.getClientID());
                            System.out.println("posStart" + newfd.getPosFromStart());
                            middlewarefds.put(newfd.getClientID(),newfd);

                            System.out.println("SIZE OF FILE" + attributes.getSize());
//
//                            //hold read msgs here until app take them
//                            System.out.println("Return Message" + returnMsg.getReadMsg().getMsg());
                            readMsgs.put(returnMsg.getReadClientInt(),returnMsg);
                            unblock(readLock);
                        }
                    }
                    else if (receiveMessage.getType().equals(write)) {
//                        udpMessageWrite returnMsg = (udpMessageWrite) receiveMessage;
//                        if(!idsMiddleware.get(returnMsg.getType()).contains(returnMsg.getWriteClientInt())){
//                            System.out.println("Added write client ID");
//
//                            idsMiddleware.get(returnMsg.getType()).add(returnMsg.getWriteClientInt());
//
//                            fileDescriptor newfd = new fileDescriptor(returnMsg.getFd().getFd(),returnMsg.getWriteClientInt(),returnMsg.getFd().getPosFromStart(),returnMsg.getFd().getReadPermission(),returnMsg.getFd().getWritePermission());
//
//                            fileAttributes attributes = new fileAttributes(returnMsg.getAttributes().getSize());
//
//                            clientFileInformation newInfo = new clientFileInformation(newfd,attributes);
//
//                            middlewarefds.put(newfd.getClientID(),newInfo);
//                            //refresh file descriptors information for new
////                            middlewarefds.put(returnMsg.getFd().getClientID(),fd);
//
//
//                            synchronized (writeLock){
//                                writeLock.notify();
//                            }
//
//
//                        }
                    }
                    else if(receiveMessage.getType().equals(max_capacity)){
//                        System.out.println();
                        udpMessageMaxCapacityAnswer answer =(udpMessageMaxCapacityAnswer)receiveMessage;
//                        System.out.println(answer.getType() );

                        if(!idsMiddleware.get(max_capacity).contains(answer.getOpenClientInt())){
                            idsMiddleware.get(max_capacity).add(answer.getOpenClientInt());
//                            System.out.println(answer.getOpenClientInt());
                            serverAnswers.put(answer.getOpenClientInt(),answer);
                        }

                        unblock(lock);
                    }
                    else if(receiveMessage.getType().equals(NO_THIS_ID)){
                        udpMessageDontKnowThisID noId = (udpMessageDontKnowThisID) receiveMessage;
                        if(noId.getxType().equals(read)){
                            if(!idsMiddleware.get(NO_THIS_ID).contains(noId.getTypeID())){
                                System.out.println("NO ID has come " + noId.getTypeID());
                                for(int i = 0;i < requests.size();i++){
                                    if(requests.get(i).getType().equals(read)){
                                        udpMessageRead readMs  = (udpMessageRead) requests.get(i);
                                        if(noId.getTypeID() == readMs.getReadClientInt()){
                                            noIdreadMessages.get(noId.getFd().getClientID()).add(readMs);
                                            break;
                                        }
                                    }
                                }

                                idsMiddleware.get(NO_THIS_ID).add(noId.getTypeID());
                                //new Open Request
                                fileAttributes attributes = new fileAttributes(0);
                                fileID fd = new fileID(-1,-1);
                                openAck++;
                                System.out.println("New OPEN_ACK"+openAck);

                                Set<fileID> keys = filesInMiddleware.keySet();
                                String fname = null;
                                for(fileID temp: keys){
                                    fileInformation file = filesInMiddleware.get(temp);
                                    if(temp.getFd() == noId.getFd().getFd().getFd() && temp.getSession() == noId.getFd().getFd().getSession()){
                                        fname = file.getFname();
                                        System.out.println("fname "+ fname);
                                    }
                                }
                                ArrayList<Integer> flags = new ArrayList<>();
                                if(middlewarefds.get(noId.getFd().getClientID()).getReadPermission() > 0 && middlewarefds.get(noId.getFd().getClientID()).getWritePermission() > 0){
                                    flags.add(O_RDWR);
                                }
                                else if(middlewarefds.get(noId.getFd().getClientID()).getReadPermission() > 0){
                                    flags.add(O_RDONLY);
                                }
                                else{
                                    flags.add(O_WRONLY);
                                }

                                udpMessage openPacket = new udpMessageOpen(open,fname,flags,openAck,attributes,noId.getFd());
                                requests.add(openPacket);
                            }
                        }

                    }
                }


//                if(openWaiting == 1){
//                    unblock(lock);
//                }
//                else if(readWaiting == 1){
//                    unblock(readLock);
//                }
//                else if(writeWaiting == 1){
//                    unblock(writeLock);
//                }
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


                    System.out.println(requests.size());
                    if(nextRequest.getType().equals(open)){
                        System.out.println("mphka wdw");
                        udpMessageOpen openRequest = (udpMessageOpen) nextRequest;
                        sendUdpMessage(openRequest,openRequest.getOpenACK());
                    }
                    else if(nextRequest.getType().equals(read)){
                        udpMessageRead readRequest = (udpMessageRead) nextRequest;
                        System.out.println("SEND WITH ID " + ((udpMessageRead) nextRequest).getReadClientInt());
                        sendUdpMessage(readRequest,readRequest.getReadClientInt());
                    }
                    else if(nextRequest.getType().equals(write)){
                        udpMessageWrite writeRequest = (udpMessageWrite) nextRequest;
                        sendUdpMessage(writeRequest,writeRequest.getWriteClientInt());
                    }
                    requests.remove(0);
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
    public void unblock(Object lock){
        synchronized (lock){
            lock.notify();
        }
    }
    public fileInformation findFile(fileID id){
        Set<fileID> keys = filesInMiddleware.keySet();

        for(fileID temp : keys){

            if(temp.getFd() == id.getFd() && temp.getSession() == id.getSession()){
                return  filesInMiddleware.get(temp);
            }
        }

        return null;
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
