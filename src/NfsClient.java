import org.omg.PortableInterceptor.SYSTEM_EXCEPTION;

import javax.swing.plaf.synth.SynthScrollBarUI;
import javax.xml.crypto.Data;
import java.io.*;
import java.lang.instrument.Instrumentation;
import java.net.*;
import java.nio.charset.StandardCharsets;
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
        fileAttributes attributes = file.getAttributes();


        udpMessageRead header = new udpMessageRead(read,n,readInt,filed,attributes);
        int sizeHeader = getObjectSize(header);
        
        System.out.println("sizeHeader" + sizeHeader);

        int remainPacket = MAX_UDP_PACKET_SIZE - sizeHeader;

        byte[] msg = remoteRead(fd,n,remainPacket,attributes);

        buff.setMsg(msg);
        //remove from read messages when app takes them
        System.out.println("wanted file read"+ new String(buff.getMsg(), StandardCharsets.UTF_8));

        return buff.getMsg().length; // success
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
        return outputStream.toByteArray();
    }

    public int remoteWrite(int fd,Msg buff,int n,int payload,fileAttributes attributes){
        int repetition = 1;
        byte [] sendMsg ;
        int returnBytes = 0;
        int start = 0;
        int end = 0;
        int writeSize = 0;

        while(repetition  > 0){
            System.out.println("before n"+ n);

            int check = n / payload;
            System.out.println("times" +check);


            if(check > 0){
                n = n -payload+ 22;
                end = end + payload -22;
                System.out.println("end "+ end);

                sendMsg = Arrays.copyOfRange(buff.getMsg(),start,end);

                start = start + payload-22;
                System.out.println("start"+ start);
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

            udpMessageWrite writeMsg = new udpMessageWrite(write,writeInt,newfileId,sendMsg,attributes);

            int req = clientLocks.get(fd).getRequests();
            req++;
            clientLocks.get(fd).setRequests(req);

            requests.add(writeMsg);
            block2(clientLocks.get(fd));

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
//                            fileDescriptor newfd = new fileDescriptor(returnMsg.getFd().getFd(),returnMsg.getFd().getClientID(),returnMsg.getFd().getPosFromStart());

                            //give attributes a timestamp
                            fileDescriptor newfd = middlewarefds.get(returnMsg.getFd().getClientID());
                            newfd.setPosFromStart(returnMsg.getFd().getPosFromStart());

//                            //refresh file information to client after server-Msg returned
                            fileAttributes attributes = new fileAttributes(returnMsg.getAttributes().getSize(),returnMsg.getAttributes().getFlags(),currentTimeInSeconds() + cacheMemory.getFreshT());
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
                            readMsgs.put(returnMsg.getReadClientInt(),returnMsg);

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
//
//                            fileDescriptor newfd = new fileDescriptor(returnMsg.getFd().getFd(),returnMsg.getWriteClientInt(),returnMsg.getFd().getPosFromStart(),returnMsg.getFd().getReadPermission(),returnMsg.getFd().getWritePermission());
                            fileDescriptor newfd = middlewarefds.get(returnMsg.getFd().getClientID());
                            newfd.setPosFromStart(returnMsg.getFd().getPosFromStart());
//                            fileAttributes attributes = new fileAttributes(returnMsg.getAttributes().getSize());
                            fileAttributes attributes = new fileAttributes(returnMsg.getAttributes().getSize(),returnMsg.getAttributes().getFlags(),currentTimeInSeconds() + cacheMemory.getFreshT());
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
}
