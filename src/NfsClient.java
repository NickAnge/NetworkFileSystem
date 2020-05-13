import org.omg.PortableInterceptor.SYSTEM_EXCEPTION;

import javax.swing.plaf.synth.SynthScrollBarUI;
import javax.xml.crypto.Data;
import java.io.*;
import java.lang.instrument.Instrumentation;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.sql.ClientInfoStatus;
import java.util.*;
import java.util.jar.Attributes;

public class NfsClient implements   nfsAPI{
    private static final String open = "Open";
    private static final String read = "Read";
    private static final String write = "Write";
    private static final String max_capacity = "MAX_CAPACITY";
    public static final String NO_THIS_ID_READ = "DONT_KNOW_THIS_ID_READ";
    public static final String NO_THIS_ID_WRITE = "DONT_KNOW_THIS_ID_WRITE";
    public  static final int MAX_UDP_PACKET_SIZE = 1024;

    private static final int ERROR = -2;
    public int readInt,writeInt,openInt,openAck;
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
    public HashMap<Integer,locker> clientLocks;

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
        ArrayList<Integer> noID_read = new ArrayList<>();
        this.idsMiddleware.put(NO_THIS_ID_READ,noID_read);

        ArrayList<Integer> noID_write = new ArrayList<>();
        this.idsMiddleware.put(NO_THIS_ID_WRITE,noID_write);
//        ArrayList<udpMessageRead> listread = new ArrayList<>();
        noIdreadMessages = new HashMap<>();
        noIdwriteMessages = new HashMap<>();

        lock = new Object();
        openWaiting = 0;
        readLock = new Object();
        readWaiting = 0;
        writeLock = new Object();
        writeWaiting = 0;

        clientLocks = new HashMap<>();

        //init buffers for open/read/write that the  middleware takes
        this.openMsgs = new HashMap<>();
        this.readMsgs = new HashMap<>();
        this.writeMsgs = new HashMap<>();
        this.middlewarefds = new HashMap<>();
        this.requests = new ArrayList<>();
        this.serverAnswers = new HashMap<>();

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
            this.cacheMemory = new CacheMemory(blockSize,freshT);

            for(int i =0; i < cacheBlocks; i++){
                Block addBlock = new Block(blockSize);
                System.out.println("prwth"+ addBlock.getBytearray()[1]);
                cacheMemory.getCache().add(addBlock);
            }

            this.cacheMemory.setFreshT(freshT);

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
                ArrayList<udpMessageWrite> writeList = new ArrayList<>();
                noIdwriteMessages.put(openInt,writeList);
                locker locker = new locker(new Object(),0);

                clientLocks.put(openInt,locker);

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

//            openWaiting= 1;
            int requests = 1;
            locker locker = new locker(new Object(),requests);
            clientLocks.put(openInt,locker);
            block2(clientLocks.get(openInt));
//            block(lock,open,openInt);
//            openWaiting = 0;
            System.out.println("OPENID"+ openInt);
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
                    ArrayList<udpMessageWrite> writeList = new ArrayList<>();
                    noIdwriteMessages.put(openInt,writeList);
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

        long time = currentTimeInSeconds();

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

//        readInt++;

        fileDescriptor filed = middlewarefds.get(fd);

        fileInformation file = findFile(filed.getFd());

        if(file == null){
            return ERROR;
        }

        int k = (int) (n + filed.getPosFromStart());
        long old = filed.getPosFromStart();
        System.out.println("old"+old);
        System.out.println("k" + k);
        Block returnBLock = bytesInCache(filed,n,time,filed.getFd(),n);

        System.out.println("VLOCKK"+returnBLock.getBytearray().length);
        System.out.println("data" + new String(returnBLock.getBytearray(),StandardCharsets.UTF_8) );

        if(returnBLock.getSizeofData() == n){
            byte[] array = biggerSmaller(returnBLock.getBytearray());
            buff.setMsg(array);
            System.out.println("wanted file read"+ new String(array, StandardCharsets.UTF_8));
            System.out.println("position"+filed.getPosFromStart());
            return array.length;
        }
        else {
            System.out.println("N + sizeofBLock" + n + returnBLock.getSizeofData());
            n = n - returnBLock.getSizeofData();
        }

        while (n > 0){
            fileAttributes attributes = file.getAttributes();
            System.out.println("modification time"+attributes.getModificationTime());
            int end = 0;
            if(returnBLock.sizeofData > 0){
                System.out.println("data" + new String(returnBLock.getBytearray(),StandardCharsets.UTF_8) );
                end = endRead(returnBLock.getBytearray(), (int) filed.getPosFromStart());
            }
            else {
                end = (int) (filed.getPosFromStart() + n);
            }
            System.out.println("enddd" + end);
            int startPosition = (int) filed.getPosFromStart();

            int readn = (int) (end - filed.getPosFromStart());

            int readBytes = checkBytes(readn);

            udpMessageRead header = new udpMessageRead(read,readBytes,readInt,filed,attributes);

            int sizeHeader = getObjectSize(header);
            System.out.println("sizeHeader" + sizeHeader);

            int remainPacket = MAX_UDP_PACKET_SIZE - sizeHeader;

            byte[] msg = remoteRead(fd,readBytes,remainPacket,attributes);

            filed = middlewarefds.get(fd);
            System.out.println("new fd"+middlewarefds.get(fd).getPosFromStart());

            n = n - msg.length;
            System.out.println("SIZEEEEEEEEEEEEEE" + file.getAttributes().getSize());
            System.out.println("POSSSSSSSSSSSSSSS" + filed);

            System.out.println("data in read"+new String(msg,StandardCharsets.UTF_8));

            int copyend =0 ;
            if(filed.getPosFromStart() > returnBLock.getBytearray().length){
                copyend = returnBLock.getBytearray().length;
            }
            else {
                copyend = (int) filed.getPosFromStart();
            }
            System.out.println("startPosition "+ startPosition);
            System.out.println("copyend"+copyend);

            returnBLock = inserArray(msg,startPosition, copyend,returnBLock);
            System.out.println("before in read"+new String(returnBLock.getBytearray(),StandardCharsets.UTF_8));

            int newfd = checkNewFd(returnBLock,filed);
            if(newfd!=-1){
                filed.setPosFromStart(newfd);
            }
            System.out.println("new fd"+filed.getPosFromStart());
            System.out.println("data in read"+new String(returnBLock.getBytearray(),StandardCharsets.UTF_8));

            System.out.println("Position of file descriptor"+filed.getPosFromStart());
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if(filed.getPosFromStart() == file.getAttributes().getSize()){
                break;
            }
        }

        byte[] msg = null;

        if(k > filed.getPosFromStart()){
            msg = Arrays.copyOfRange(returnBLock.getBytearray(), (int) old, (int) filed.getPosFromStart());

        }
        else {
            System.out.println("old"+old);
            System.out.println("k"+k);

            for(int i =0;i<returnBLock.getBytearray().length;i++){
                if(returnBLock.getBytearray()[i] != 0){
                    System.out.println(i);
                    break;
                }
            }
            msg = Arrays.copyOfRange(returnBLock.getBytearray(), (int)old, (int) k);
        }

        if(k > filed.getPosFromStart()){
            System.out.println();
            filed.setPosFromStart(filed.getPosFromStart());
        }
        else{
            System.out.println("k");
            filed.setPosFromStart(k);
        }
        System.out.println("REAL POSITION OF FD"+filed.getPosFromStart());
        buff.setMsg(msg);

        System.out.println("position"+filed.getPosFromStart());

        System.out.println("wanted file read"+ new String(msg, StandardCharsets.UTF_8));

        return returnBLock.getBytearray().length;
//        int readBytes = checkBytes(n);

//        System.out.println("nnnn"+ readBytes);
//        udpMessageRead header = new udpMessageRead(read,readBytes,readInt,filed,attributes);
//        int sizeHeader = getObjectSize(header);
//        System.out.println("sizeHeader" + sizeHeader);
//
//        int remainPacket = MAX_UDP_PACKET_SIZE - sizeHeader;
//        byte[] msg = remoteRead(fd,readBytes,remainPacket,attributes);
//
//        buff.setMsg(msg);
//
//        //remove from read messages when app takes them
//        System.out.println("wanted file read"+ new String(buff.getMsg(), StandardCharsets.UTF_8));
//
//        return buff.getMsg().length; // success
    }

    @Override
    public int myNfs_write(int fd, Msg buff, int n) {

        System.out.println("requested fd" + fd);
        if(!middlewarefds.containsKey(fd)){
            return ERROR;
        }

        if(middlewarefds.get(fd).getWritePermission() <  0){
            return  ERROR;
        }

        fileDescriptor filed = middlewarefds.get(fd);
        fileInformation file = findFile(filed.getFd());

        if(file == null){
            return ERROR;
        }
//        writeInt++;
        fileAttributes attributes = file.getAttributes();

        udpMessageWrite header = new udpMessageWrite("Write",writeInt,filed,attributes);

        int sizeHeader = getObjectSize(header);
        System.out.println("sizeHeader" +sizeHeader);
        int remainPacket = MAX_UDP_PACKET_SIZE - sizeHeader;
        System.out.println("remainPacket" +remainPacket);

        int ret = remoteWrite(fd,buff,n,remainPacket,attributes);
        System.out.println("REAL POSITION OF FD"+filed.getPosFromStart());

        return ret;
    }

    @Override
    public int myNfs_seek(int fd, int pos, int whence) {
        if(!middlewarefds.containsKey(fd)){
            return ERROR;
        }
        else if(pos < 0){
            return ERROR;
        }


        fileInformation file = findFile(middlewarefds.get(fd).getFd());
        if(currentTimeInSeconds() > file.getAttributes().getTimestampAttributes()){
            openInt++;
            System.out.println("Attributes" + file.getAttributes().getFlags());
            udpMessageOpen newOpen = new udpMessageOpen(open,file.getFname(),file.getAttributes().getFlags(),openInt,file.getAttributes(),middlewarefds.get(fd));
            requests.add(newOpen);
            int req = clientLocks.get(fd).getRequests();
            req++;
            clientLocks.get(fd).setRequests(req);
            block2(clientLocks.get(fd));
        }
        int temp = -1;
        long newStart = -1;
//
        if(whence == SEEK_SET){
            //SEEK_SET The offset is set to offset bytes.
            temp =1;
            newStart = pos;
            middlewarefds.get(fd).setPosFromStart(newStart);
            System.out.println("NEW POSITION: "+middlewarefds.get(fd).getPosFromStart() );

        }
        else if(whence == SEEK_CUR){
            temp =1;
            //SEEK_CURR  The offset is set to its current location plus offset bytes.
            newStart = middlewarefds.get(fd).getPosFromStart() + pos;
            middlewarefds.get(fd).setPosFromStart(newStart);
            System.out.println("NEW POSITION: "+middlewarefds.get(fd).getPosFromStart() );

        }
        else if (whence == SEEK_END) {
            temp = 1;
            //SEEK_END  The offset is set to the size of the file plus offset bytes.
//            fileInformation file = findFile(middlewarefds.get(fd).getFd());

            System.out.println("SIZE: "+ file.getAttributes().getSize());
            System.out.println("OLD POSITION: "+ middlewarefds.get(fd).getPosFromStart());
            newStart = file.getAttributes().getSize() + pos;
            middlewarefds.get(fd).setPosFromStart(newStart);
            System.out.println("NEW POSITION: "+middlewarefds.get(fd).getPosFromStart() );
        }
//        }
        return 1;
    }
    @Override
    public int myNfs_close(int fd) {

        //remove this file descriptor for the

        if(!middlewarefds.containsKey(fd)){
            return ERROR;
        }
        noIdwriteMessages.remove(fd);
        noIdreadMessages.remove(fd);
        middlewarefds.remove(fd);

        return 0;
    }

    public static int  getObjectSize(udpMessage e){
        ObjectOutputStream oos = null;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            oos = new ObjectOutputStream(baos);
            oos.writeObject(e);
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
        byte[] byteMsg = baos.toByteArray();


        return byteMsg.length;
    }

    public void sendUdpMessage(udpMessage msg,int clientId){

        int send =0;
        udpMessage msg2 = null;
        ObjectOutputStream oos = null;
        try {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        oos = new ObjectOutputStream(baos);

        oos.writeObject(msg);
        byte[] byteMsg = new byte[1024];
        byteMsg = baos.toByteArray();


        DatagramPacket packet = new DatagramPacket(byteMsg, byteMsg.length,server.getServerIp(), server.port);
        System.out.println("packet - length"+ packet.getLength());
        if(msg.getType().equals(read)){
            while(!idsMiddleware.get(msg.getType()).contains(clientId) && !idsMiddleware.get(max_capacity).contains(clientId) && !idsMiddleware.get(NO_THIS_ID_READ).contains(clientId)){
                //resend packet until server answer
                send++;
                udpSocket.send(packet);
            }
        }
        else {
            while(!idsMiddleware.get(msg.getType()).contains(clientId) && !idsMiddleware.get(max_capacity).contains(clientId) && !idsMiddleware.get(NO_THIS_ID_WRITE).contains(clientId)){
                send++;
                udpSocket.send(packet);
            }
        }
        if(send == 0){
            while(!idsMiddleware.get(msg.getType()).contains(clientId)){
                send++;
                udpSocket.send(packet);
            }
        }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public byte[] remoteRead(int fd,int n,int payload,fileAttributes attributes){
        int repetition = 1;
        String returnMsg = "";
//        byte[] retMsg = new byte[n];
        int realBytes = 0;
        int start = 0;

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );


        while(repetition  > 0){
            System.out.println("before :"+ n);
            int check = n / payload;
            System.out.println("times" +check);

            if(check > 0){
                n = n - payload + 22;
                realBytes = payload - 22;
                System.out.println("n"+ n);
                System.out.println("payload"+ realBytes);
            }
            else {
                realBytes = n;
                System.out.println("n"+ n);
                System.out.println("payload"+ realBytes);
                repetition = -1;
            }
            readInt++;

            System.out.println("id "+ readInt);
            fileDescriptor newfileId = middlewarefds.get(fd);

            fileInformation file = findFile(newfileId.getFd());

            System.out.println("SIZE"+ file.getAttributes().getSize());
            System.out.println("POS"+ newfileId.getPosFromStart());

            if(file.getAttributes().getSize() == newfileId.getPosFromStart()){
                System.out.println("ne"+newfileId);
                break;
            }
            udpMessageRead readMsg = new udpMessageRead(read,realBytes,readInt,newfileId,attributes);
            int req = clientLocks.get(fd).getRequests();
            req++;

            clientLocks.get(fd).setRequests(req);

            requests.add(readMsg);
            block2(clientLocks.get(fd));

            try {
                System.out.println("size of byte array"+ readMsgs.get(readInt).getReadMsg().length);
                outputStream.write(readMsgs.get(readInt).getReadMsg());
            } catch (IOException e) {
                e.printStackTrace();
            }
            readMsgs.remove(readInt);
        }
        System.out.println("posssss"+ middlewarefds.get(fd).getPosFromStart());
        return outputStream.toByteArray();
    }

    public int remoteWrite(int fd,Msg buff,int n,int payload,fileAttributes attributes){
        int repetition = 1;
        byte [] sendMsg ;
        int returnBytes = 0;
        int start = 0;
        int end = 0;
        int writeSize = 0;
        int cache = 0;
        while(repetition  > 0){
            System.out.println("before n"+ n);

            int check = n / payload;
            System.out.println("times" +check);


            if(check > 0){
                n = n -payload+ 22;
                end = end + payload -22;
                System.out.println("end "+ end);
                cache = start;
                System.out.println("start"+ start);

                sendMsg = Arrays.copyOfRange(buff.getMsg(),start,end);
                start = start + payload-22;
//                System.out.println("start"+ start);
                System.out.println("payload"+ sendMsg.length);
            }
            else {
                end = buff.getMsg().length;
                sendMsg = Arrays.copyOfRange(buff.getMsg(),start,end);
                System.out.println("n"+ n);
                System.out.println("payload"+ sendMsg.length);
                repetition = -1;
            }
            writeInt++;

            fileDescriptor newfileId = middlewarefds.get(fd);
            int old = (int) newfileId.getPosFromStart();

            udpMessageWrite writeMsg = new udpMessageWrite(write,writeInt,newfileId,sendMsg,attributes);

            int req = clientLocks.get(fd).getRequests();
            req++;
            clientLocks.get(fd).setRequests(req);


            requests.add(writeMsg);

            block2(clientLocks.get(fd));
            ///for cache
            udpMessageWrite msg = writeMsgs.get(fd);
            long time = currentTimeInSeconds();

            if(msg.getFd().getPosFromStart() != old){
                insertCache(sendMsg,cache,end,time,msg.getAttributes().getModificationTime(),msg.getFd().getFd());
            }

            returnBytes = returnBytes + sendMsg.length;
        }


        return returnBytes;
    }


    public udpMessage receiveUdpMessages() {
        byte[] buffer = new byte[MAX_UDP_PACKET_SIZE];

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

                                //give attributes a timestamp
                                fileAttributes attributes = new fileAttributes(returnMsg.getAttributes().getSize(),returnMsg.getAttributes().getFlags(),currentTimeInSeconds() + cacheMemory.getFreshT());

                                System.out.println("readPerm" + newfd.getReadPermission());
                                System.out.println("writePerm" + newfd.getWritePermission());

                                System.out.println("SIZE OF FILE" + attributes.getSize());
                                System.out.println("Current time "+ attributes.getTimestampAttributes());

                                middlewarefds.put(returnMsg.getOpenClientInt(),newfd);

                                if(returnMsg.getFiled().getFd().getFd() >  0){
                                    fileInformation file = findFile(returnMsg.getFiled().getFd());
                                    if(file == null){
                                        fileInformation finfo = new fileInformation(fileName,attributes);
                                        filesInMiddleware.put(returnMsg.getFiled().getFd(),finfo);
                                    }
                                    else {
                                        file.setAttributes(attributes);
                                    }
                                    System.out.println("ID "+ newfd.getFd().getFd());
                                    System.out.println("Session" + newfd.getFd().getSession());
                                    System.out.println("openid" + newfd.getClientID());


                                }


                                if(noIdreadMessages.containsKey(returnMsg.getOpenClientInt())){
                                    for(int i =0 ; i < noIdreadMessages.get(returnMsg.getOpenClientInt()).size();i++){
                                        noIdreadMessages.get(returnMsg.getOpenClientInt()).get(i).setFd(newfd);
                                    }
                                    requests.addAll(noIdreadMessages.get(returnMsg.getOpenClientInt()));
                                    noIdreadMessages.get(returnMsg.getOpenClientInt()).clear();
                                }
                                if(noIdwriteMessages.containsKey(returnMsg.getOpenClientInt())){
                                    System.out.println("DINW TA PALIA WRITE");
                                    for(int i =0 ; i < noIdwriteMessages.get(returnMsg.getOpenClientInt()).size();i++){
                                        noIdwriteMessages.get(returnMsg.getOpenClientInt()).get(i).setFd(newfd);
                                    }
                                    requests.addAll(noIdwriteMessages.get(returnMsg.getOpenClientInt()));
                                    noIdwriteMessages.get(returnMsg.getOpenClientInt()).clear();
                                }
                            }

                            openMsgs.put(returnMsg.getOpenClientInt(),returnMsg);
                            int req = clientLocks.get(returnMsg.getOpenClientInt()).getRequests();
                            req--;
                            clientLocks.get(returnMsg.getOpenClientInt()).setRequests(req);
                            unblock(clientLocks.get(returnMsg.getOpenClientInt()));
                        }
                    }
                    else if(receiveMessage.getType().equals(read) || receiveMessage.getType().equals("No-change")){
                        udpMessageRead returnMsg = (udpMessageRead) receiveMessage;
                        String type = receiveMessage.getType();
                        if(receiveMessage.getType().equals("No-change")){
                            receiveMessage.setType(read);
                        }
                        if(!idsMiddleware.get(returnMsg.getType()).contains(returnMsg.getReadClientInt())){
                            System.out.println("Added read Client ID ");
                            idsMiddleware.get(returnMsg.getType()).add(returnMsg.getReadClientInt());
//
//                            System.out.println("id" + returnMsg.getReadClientInt());
//                            System.out.println("READ"+returnMsg.getFd().getReadPermission());
//                            System.out.println("WRITE"+returnMsg.getFd().getWritePermission());

                            //give attributes a timestamp
                            fileDescriptor newfd = middlewarefds.get(returnMsg.getFd().getClientID());
                            System.out.println("mesa sto read"+newfd);

                            newfd.setPosFromStart(returnMsg.getFd().getPosFromStart());
                            System.out.println("MESA STHN READDDDDDD"+newfd.getPosFromStart());
                            long time = currentTimeInSeconds() + cacheMemory.freshT;
//                            //refresh file information to client after server-Msg returned
                            fileAttributes attributes = new fileAttributes(returnMsg.getAttributes().getSize(),returnMsg.getAttributes().getFlags(),time);
                            fileInformation file = findFile(returnMsg.getFd().getFd());
//                            middlewarefds.put(newfd.getClientID(),newfd);

                            if(file == null){
                                fileInformation finfo = new fileInformation(fileName,attributes);
                                filesInMiddleware.put(returnMsg.getFd().getFd(),finfo);
                            }
                            else {
                                file.setAttributes(attributes);
                            }
                            System.out.println("id " + newfd.getClientID());
                            System.out.println("posStart" + newfd.getPosFromStart());
                            System.out.println("SIZE OF FILE" + attributes.getSize());
                            System.out.println("Current time "+ attributes.getTimestampAttributes());
                            System.out.println("Modification time" + returnMsg.getAttributes().getModificationTime());
                            System.out.println("INSERT CACHE");


                            if(type.equals("No-change")){
                                byte[] msg = refreshTimeBlocks(returnMsg.getFd(),returnMsg.getFd().getPosFromStart(),(long)returnMsg.getSize(),time,returnMsg.getFd().getFd());
                                returnMsg.setReadMsg(msg);

                                readMsgs.put(returnMsg.getReadClientInt(),returnMsg);
                                int req = clientLocks.get(newfd.getClientID()).getRequests();
                                req--;
                                clientLocks.get(newfd.getClientID()).setRequests(req);
                                unblock(clientLocks.get(newfd.getClientID()));
                                continue;
                            }

                            if(cacheMemory.getCache().size()> 0 && returnMsg.getReadMsg().length > 0){
                                int insertCache = insertCache(returnMsg.getReadMsg(),newfd.getPosFromStart() - returnMsg.getReadMsg().length,newfd.getPosFromStart(),time,returnMsg.getAttributes().getModificationTime(),newfd.getFd());
                            }

                            readMsgs.put(returnMsg.getReadClientInt(),returnMsg);

                            /// cache
                            int req = clientLocks.get(newfd.getClientID()).getRequests();
                            req--;
                            clientLocks.get(newfd.getClientID()).setRequests(req);
                            unblock(clientLocks.get(newfd.getClientID()));
                        }
                    }
                    else if (receiveMessage.getType().equals(write)) {
                        udpMessageWrite returnMsg = (udpMessageWrite) receiveMessage;
                        if(!idsMiddleware.get(returnMsg.getType()).contains(returnMsg.getWriteClientInt())){
                            System.out.println("Added write client ID");
//
                            idsMiddleware.get(returnMsg.getType()).add(returnMsg.getWriteClientInt());
                            long time = currentTimeInSeconds() + cacheMemory.freshT;
//                            fileDescriptor newfd = new fileDescriptor(returnMsg.getFd().getFd(),returnMsg.getWriteClientInt(),returnMsg.getFd().getPosFromStart(),returnMsg.getFd().getReadPermission(),returnMsg.getFd().getWritePermission());
                            fileDescriptor newfd = middlewarefds.get(returnMsg.getFd().getClientID());
                            newfd.setPosFromStart(returnMsg.getFd().getPosFromStart());
//                            fileAttributes attributes = new fileAttributes(returnMsg.getAttributes().getSize());
                            fileAttributes attributes = new fileAttributes(returnMsg.getAttributes().getSize(),returnMsg.getAttributes().getFlags(),time);
                            fileInformation file = findFile(returnMsg.getFd().getFd());

                            if(file == null){
                                fileInformation finfo = new fileInformation(fileName,attributes);
                                filesInMiddleware.put(returnMsg.getFd().getFd(),finfo);
                            }
                            else {
                                file.setAttributes(attributes);
                            }

                            System.out.println("id " + newfd.getClientID());
                            System.out.println("posStart" + newfd.getPosFromStart());
                            System.out.println("SIZE OF FILE" + attributes.getSize());
                            System.out.println("Current time "+ attributes.getTimestampAttributes());

                            writeMsgs.put(returnMsg.getFd().getClientID(),returnMsg);
                            int req = clientLocks.get(newfd.getClientID()).getRequests();
                            req--;
                            clientLocks.get(newfd.getClientID()).setRequests(req);
                            unblock(clientLocks.get(newfd.getClientID()));
                        }
                    }
                    else if(receiveMessage.getType().equals(max_capacity)){
                        udpMessageMaxCapacityAnswer answer =(udpMessageMaxCapacityAnswer)receiveMessage;
                        if(!idsMiddleware.get(max_capacity).contains(answer.getOpenClientInt())){
                            idsMiddleware.get(max_capacity).add(answer.getOpenClientInt());
                            serverAnswers.put(answer.getOpenClientInt(),answer);
                        }
//
//                        int req = clientLocks.get(answer.getFd().getClientID()).getRequests();
//                        req--;
//                        clientLocks.get(newfd.getClientID()).setRequests(req);
//                        unblock(clientLocks.get(newfd.getClientID()));
                    }
                    else if(receiveMessage.getType().equals(NO_THIS_ID_READ)){
                        udpMessageDontKnowThisID noId = (udpMessageDontKnowThisID) receiveMessage;

                        if(noId.getxType().equals(read)){
                            if(!idsMiddleware.get(NO_THIS_ID_READ).contains(noId.getTypeID())){
                                System.out.println("NO ID  READ has come " + noId.getTypeID());
                                for(int i = 0;i < requests.size();i++){
                                    if(requests.get(i).getType().equals(read)){
                                        udpMessageRead readMs  = (udpMessageRead) requests.get(i);
                                        if(noId.getTypeID() == readMs.getReadClientInt()){
                                            noIdreadMessages.get(noId.getFd().getClientID()).add(readMs);
                                            break;
                                        }
                                    }
                                }

                                idsMiddleware.get(NO_THIS_ID_READ).add(noId.getTypeID());
                                //new Open Request
                                fileAttributes attributes = new fileAttributes(0);
                                fileID fd = new fileID(-1,-1);
                                openAck++;
                                System.out.println("New OPEN_ACK"+openAck);

                                fileInformation file = findFile(noId.getFd().getFd());

                                String fname = file.getFname();
//
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

                                int req = clientLocks.get(noId.getFd().getClientID()).getRequests();
                                req++;
                                clientLocks.get(noId.getFd().getClientID()).setRequests(req);

                                udpMessage openPacket = new udpMessageOpen(open,fname,flags,openAck,attributes,noId.getFd());
                                requests.add(openPacket);
                            }
                        }
                    }
                    else if(receiveMessage.getType().equals(NO_THIS_ID_WRITE)){
                        udpMessageDontKnowThisID noId = (udpMessageDontKnowThisID) receiveMessage;

                        if(!idsMiddleware.get(NO_THIS_ID_WRITE).contains(noId.getTypeID())){
                            for(int i = 0;i < requests.size();i++){
                                if(requests.get(i).getType().equals(write)){
                                    udpMessageWrite readMs  = (udpMessageWrite) requests.get(i);
                                    if(noId.getTypeID() == readMs.getWriteClientInt()){
                                        noIdwriteMessages.get(noId.getFd().getClientID()).add(readMs);
                                        break;
                                    }
                                }
                            }
                            idsMiddleware.get(NO_THIS_ID_WRITE).add(noId.getTypeID());
                            fileAttributes attributes = new fileAttributes(0);
                            fileID fd = new fileID(-1,-1);
                            openAck++;
                            System.out.println("New OPEN_ACK"+openAck);

                            fileInformation file = findFile(noId.getFd().getFd());

                            String fname = file.getFname();
//
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
                            int req = clientLocks.get(noId.getFd().getClientID()).getRequests();
                            req++;
                            clientLocks.get(noId.getFd().getClientID()).setRequests(req);
                            udpMessage openPacket = new udpMessageOpen(open,fname,flags,openAck,attributes,noId.getFd());
                            requests.add(openPacket);
                        }
                    }
                }
                for(int i = 0; i < clientLocks.size(); i++){
                    unblock(clientLocks.get(i));
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


    public void block2(locker locker){
        while (true){
            synchronized (locker.getLock()){
                try {
                    locker.getLock().wait();
                }catch (InterruptedException e){
                    e.printStackTrace();
                }
                if(locker.getRequests() <= 0){
                    break;
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
    public void unblock(locker lock){
        synchronized (lock.getLock()){
            lock.getLock().notify();
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


    public long currentTimeInSeconds(){
        long time = System.currentTimeMillis()/1000;
        System.out.println(System.currentTimeMillis()/1000);
        return time;
    }
    public  int checkBytes(int n) {
        int max_size_of = cacheMemory.getBlockSize()*cacheMemory.getCache().size();

        if(max_size_of > n){
            int k = n/cacheMemory.getBlockSize();
            if( (k*cacheMemory.getBlockSize()) == n){
                return n;
            }
            else {
                return (k+1)*cacheMemory.getBlockSize();
            }

        }
        return  n;
    }

    public byte[] refreshTimeBlocks(fileDescriptor posFd ,long start,long end,long newTimestamp,fileID file){
        int sizeOfData  = (int) (end - start);
        Block returnBlock = new Block((int) (end - start));
        int startData = 0;
        int endData = 0;
        int startByteArray = 0;
        int endByteArray= 0;
        for(int i=0;i < cacheMemory.getCache().size();i++){
            if(cacheMemory.getCache().get(i).getFileInfo().equals(file)){
                long startBlock = cacheMemory.getCache().get(i).getStart();
                long endBlock = cacheMemory.getCache().get(i).getEnd();
                if(start >= startBlock && start <= endBlock){
                    returnBlock.setHasInfo(1);
                    cacheMemory.getCache().get(i).setBlockTimeStamp(newTimestamp);
                    int addition = (int) (endBlock- start);
                    start = start + addition + 1;

                    startData = (int) (posFd.getPosFromStart() - startBlock);
                    endData = (int) (endBlock - startBlock) + 1;

                    byte[] refresh = Arrays.copyOfRange(cacheMemory.getCache().get(i).getBytearray(),startData,endData);
                    endByteArray = endData + endByteArray;
                    returnBlock = inserArray(returnBlock.getBytearray(),startByteArray,endByteArray,returnBlock);
                    startByteArray = startByteArray  + endData;
                    posFd.setPosFromStart(start);
                    int size = returnBlock.getSizeofData();
                    returnBlock.setSizeofData(size + addition+ 1);
                    i = -1;
                }
            }
        }

        return returnBlock.getBytearray();
    }
    public int insertCache(byte[] data,long start,long end,long timestamp,long modificationStamp,fileID fileID){
        int j = -1;
        int sizeOfdata = data.length;
        int inserted = -1;
        System.out.println("before start"+start);
        System.out.println("before end"+end);
        int startData = 0;
        int newEnd = 0;

        for(int i =0; i < cacheMemory.getCache().size();i++){
            System.out.println("BLOCK +" +i);
            System.out.println("data lenget" + sizeOfdata);
            System.out.println("info"+cacheMemory.getCache().get(i).getHasInfo());
//            if(cacheMemory.)
            if(cacheMemory.getCache().get(i).getHasInfo() == 0){

                System.out.println("Add new Block");
                System.out.println("fileInfo" + fileID);
                System.out.println("start"+ start);
                System.out.println("end " + (cacheMemory.getBlockSize() - 1 + start));
                System.out.println("timestamp" + timestamp);
                System.out.println("modificationTIme" + modificationStamp);

                inserted = 1;
                cacheMemory.getCache().get(i).setHasInfo(1);

                if(cacheMemory.blockSize > sizeOfdata){
                    newEnd = (int) startData + sizeOfdata;
                    end = start + sizeOfdata - 1;
                }
                else{
                    newEnd = (int) (cacheMemory.getBlockSize() + startData);
                    end = start+cacheMemory.getBlockSize() - 1;
                }
                System.out.println("NEw end"+ newEnd);
                System.out.println("NEW START" + startData);

                byte[] refresh = Arrays.copyOfRange(data, (int) startData, newEnd);

                cacheMemory.getCache().get(i).setBytearray(refresh);
                cacheMemory.getCache().get(i).setFileInfo(fileID);
                cacheMemory.getCache().get(i).setStart(start);
                cacheMemory.getCache().get(i).setEnd(end );
                cacheMemory.getCache().get(i).setHasInfo(1);
                refreshTimestamps(cacheMemory.getCache().get(i),timestamp,modificationStamp);
                System.out.println("data lenget" + data.length);
                sizeOfdata = sizeOfdata - cacheMemory.getBlockSize();
                //gia to data array
                startData = startData + cacheMemory.getBlockSize();
                //gia to byte start
                start = start + cacheMemory.getBlockSize();
                if(sizeOfdata <=0){
                    break;
                }
                continue;
            }
            if(cacheMemory.getCache().get(i).getFileInfo().equals(fileID)) {
                long startBlock = cacheMemory.getCache().get(i).getStart();
                long endBlock = cacheMemory.getCache().get(i).getEnd();
                System.out.println("start second if :"+ start);
                System.out.println("END"+ cacheMemory.getCache().get(i).getEnd());
                System.out.println("start " +cacheMemory.getCache().get(i).getStart());
                if (startBlock <= start && endBlock >= start) {
                    inserted = 1;
                    if(cacheMemory.blockSize > sizeOfdata){
                        newEnd = (int)startData + sizeOfdata;
                        end = start + sizeOfdata - 1;
                    }
                    else{
                        end = start+cacheMemory.getBlockSize() -1;
                        newEnd = (int) (cacheMemory.getBlockSize()+ startData);
                    }
                    System.out.println("NEw end"+ newEnd);
                    System.out.println("STARt" + startData);

                    byte[] refresh = Arrays.copyOfRange(data, startData, newEnd);
                    cacheMemory.getCache().get(i).setHasInfo(1);
                    cacheMemory.getCache().get(i).setStart(start);
                    cacheMemory.getCache().get(i).setEnd(end);
                    cacheMemory.getCache().get(i).setBytearray(refresh);
                    refreshTimestamps(cacheMemory.getCache().get(i), timestamp, modificationStamp);

                    sizeOfdata = sizeOfdata - cacheMemory.getBlockSize();
                    //gia to byte start
                    start = start + cacheMemory.getBlockSize();
                    //gia to array data start
                    startData = startData + cacheMemory.getBlockSize();
                    System.out.println("Add new Block");
                    System.out.println("fileInfo" + fileID);
                    System.out.println("start"+ cacheMemory.getCache().get(i).getStart());
                    System.out.println("end " + cacheMemory.getCache().get(i).getEnd());
                    System.out.println("timestamp" +cacheMemory.getCache().get(i).getBlockTimeStamp());
                    System.out.println("modificationTIme" +cacheMemory.getCache().get(i).getModificationStamp());

                    if (sizeOfdata <= 0) {
                        break;
                    }
                    i=-1;
                }
            }
        }

        if(sizeOfdata > 0 && inserted < 0){
            int manyBlocks = (int) (sizeOfdata)/cacheMemory.getBlockSize();
            if (manyBlocks > cacheMemory.getCache().size()){
                manyBlocks = cacheMemory.getCache().size();
            }

            System.out.println("HOw many blocks "+ manyBlocks);
            ArrayList<Integer> listOfBlocks = new ArrayList<>();
            int l = -1;

            long min = cacheMemory.getCache().get(0).getBlockTimeStamp();

            while(true){
                if(manyBlocks == 0){
                    break;
                }
                for(int i =0;i < cacheMemory.getCache().size() ;i++) {
                    if (cacheMemory.getCache().get(i).getBlockTimeStamp() <= min) {
                        min = cacheMemory.getCache().get(i).getBlockTimeStamp();
                        l = i;
                    }
                }
                listOfBlocks.add(l);
                System.out.println("kappa "+ listOfBlocks.size());
                if(listOfBlocks.size() == manyBlocks || listOfBlocks.size() == 0){
                    break;
                }
            }


            System.out.println("list "+ listOfBlocks.size());
            for(int k = 0; k < listOfBlocks.size(); k++){
                if(cacheMemory.blockSize > sizeOfdata){
                    newEnd = (int)startData + sizeOfdata;
                    end = start + sizeOfdata-1;

                }
                else{
                    end = start + cacheMemory.getBlockSize() -1;
                    newEnd = (int) (cacheMemory.getBlockSize()+ startData);
                }
                System.out.println("NEw end"+ newEnd);
                System.out.println("STARt" + startData + k);
                System.out.println("end"+ end);
                System.out.println("start"+start);

                byte[] refresh = Arrays.copyOfRange(data, (int) startData, newEnd);
                cacheMemory.getCache().get(listOfBlocks.get(k)).setBytearray(refresh);
                cacheMemory.getCache().get(listOfBlocks.get(k)).setHasInfo(1);
                cacheMemory.getCache().get(listOfBlocks.get(k)).setFileInfo(fileID);
                cacheMemory.getCache().get(listOfBlocks.get(k)).setStart(start);
                cacheMemory.getCache().get(listOfBlocks.get(k)).setEnd(end);
                refreshTimestamps(cacheMemory.getCache().get(listOfBlocks.get(k)),timestamp,modificationStamp);
                startData = startData + cacheMemory.getBlockSize();
                start = start + cacheMemory.getBlockSize();
                System.out.println("data in cache"+new String(cacheMemory.getCache().get(listOfBlocks.get(k)).getBytearray(),StandardCharsets.UTF_8));
            }
        }
        return 1;
    }


    public Block bytesInCache(fileDescriptor fd,int n,long currentTime,fileID file,int size){

        int sizeOfBytes = n;
        long positionFD1 = fd.getPosFromStart();

        Block returnBlock = new Block((int) (positionFD1+size),0);

        System.out.println("BLOCKKKKKK" + returnBlock.getBytearray().length);
        int startData = 0;
        int endData =  0;
        int fdPos=0;
        int end = (int) (fd.getPosFromStart() + n);

        int stardfd = (int) fd.getPosFromStart();

        System.out.println("end"+ end);
//        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for(int i =0; i< cacheMemory.getCache().size();i++){
            System.out.println("BLOCK "+ i);
            if(file.equals(cacheMemory.getCache().get(i).getFileInfo())){
                if((currentTime > cacheMemory.getCache().get(i).getBlockTimeStamp() )|| (filesInMiddleware.get(file).getAttributes().getModificationTime() != cacheMemory.getCache().get(i).getModificationStamp())){
                    continue;
                }
                long startBlock = cacheMemory.getCache().get(i).getStart();
                long endBlock = cacheMemory.getCache().get(i).getEnd();
                long positionFD = fd.getPosFromStart();
//                long end = (fd.getPosFromStart() + )
                System.out.println("FD POSITION" +fd.getPosFromStart());
//                System.out.println("start second if :"+ start);
                System.out.println("END"+ endBlock);
                System.out.println("start " +startBlock);
                System.out.println("sizeofBytes" + sizeOfBytes);
                System.out.println("Bytearray in block" + new String(cacheMemory.getCache().get(i).getBytearray(),StandardCharsets.UTF_8));
                System.out.println("Bytearray length"+ cacheMemory.getCache().get(i).getBytearray().length);

                if((startBlock <= positionFD && positionFD <=endBlock)||( endBlock >= end && startBlock <= end)){
                    returnBlock.setHasInfo(1);

                    int old = (int) positionFD;

//                    int func = (int) (positionFD / cacheMemory.getBlockSize());

                    int returnStart = 0;
                    if(positionFD >=startBlock){
                        startData = (int) (positionFD - startBlock);
                        returnStart = (int) positionFD;
                    }
                    else {
                        startData=0;
                        returnStart = (int) startBlock;
                    }
                    int check = (int) (sizeOfBytes + positionFD);

                    if(check > endBlock){
                        endData = cacheMemory.getCache().get(i).getBytearray().length;
                        sizeOfBytes = sizeOfBytes - (cacheMemory.getCache().get(i).getBytearray().length - startData);
                    }
                    else if(check == endBlock){
                        endData = cacheMemory.getCache().get(i).getBytearray().length;
                        sizeOfBytes = sizeOfBytes - (cacheMemory.getCache().get(i).getBytearray().length - startData);
                    }
                    else {
                        endData = startData + sizeOfBytes;
                        sizeOfBytes = 0;
                    }

                    System.out.println("endData" + endData);
                    System.out.println("stardData "+ startData);

                    System.out.println("new fd" + fd.getPosFromStart());

                    byte[] refresh = Arrays.copyOfRange(cacheMemory.getCache().get(i).bytearray,startData,endData);

                    System.out.println("startdata"+old);
                    System.out.println("end"+old+refresh.length);
                    System.out.println("startBloc"+ startBlock);
                    System.out.println("endBlock"+(startBlock+refresh.length));

                    inserArray(refresh, returnStart,(returnStart+refresh.length),returnBlock);

                    System.out.println("inside " + new String(returnBlock.getBytearray(),StandardCharsets.UTF_8));
                    long oldFD = fd.getPosFromStart();
                    if(startBlock <= oldFD){
                        fd.setPosFromStart(oldFD + refresh.length);
                    }
                    int back = returnBlock.getSizeofData();
                    back = back + refresh.length;
                    returnBlock.setSizeofData(back);

                    System.out.println("Bytearray :"+ new String(refresh,StandardCharsets.UTF_8));
                    if (sizeOfBytes == 0){
                        return returnBlock;
                    }
                }
            }
        }
        return returnBlock;
    }

//
    public  byte[] biggerSmaller(byte[] array){
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        for(int i =0;i < array.length;i++){
            if(array[i] != 0){
                outputStream.write(array[i]);
            }
        }
        return outputStream.toByteArray();
    }
    public Block inserArray(byte[] array,int start,int end,Block block){

        System.out.println("block asdadadasdadas" +block.getBytearray().length);
        for(int i = start,j=0;i<end;i++,j++){
            block.getBytearray()[i] = array[j];
        }
        return block;
    }

    public int endRead(byte[] array,int start){

        for(int i =start;i <array.length;i++){
            if(array[i]!= 0){
                return i;
            }
        }

        return array.length;
    }

    public int checkNewFd(Block block,fileDescriptor oldfd){
        for(int i = (int) oldfd.getPosFromStart(); i < block.getBytearray().length; i++){
            if(block.getBytearray()[i] == 0){
                return i;
            }
        }
        return -1;
    }
    public void printfCache(){
        for(int i = 0;i < cacheMemory.getCache().size();i++){
            System.out.println("BLOCK "+i);
            System.out.println("bytearray " + new String(cacheMemory.getCache().get(i).getBytearray(),StandardCharsets.UTF_8));
            System.out.println("Start :" +cacheMemory.getCache().get(i).getStart());
            System.out.println("END :" +cacheMemory.getCache().get(i).getEnd());
            System.out.println("Time :" +cacheMemory.getCache().get(i).getBlockTimeStamp());
            System.out.println("MOdifTime :" +cacheMemory.getCache().get(i).getModificationStamp());
        }
    }
    public void refreshTimestamps(Block block,long timestamp,long modificationtimestamp){
        block.setModificationStamp(modificationtimestamp);
        block.setBlockTimeStamp(timestamp);
    }
}
