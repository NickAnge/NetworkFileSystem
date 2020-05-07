
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.util.*;

public class NfsServer {
    public  static final String RED = "\033[0;31m";
    public static DatagramSocket serverSocket;
    public static int session = 0;
    public static File directory;
    public static final int O_CREAT = 1; // if file does not exist create it
    public static final int O_EXCL = 2; // if file exists return error with EEXIT
    public static final int O_TRUNC = 3; // delete file and recreate it
    public static final int O_RDWR = 4; //READ and WRITE permission to the file
    public static final int O_RDONLY = 5; // READ only permission
    public static final int O_WRONLY = 6; // WRITE only permission
    public static final int E_EXIST = -1; //error if file exists with O_EXCl
    public  static final int ERROR = -2;
    public  static int MAX_NUM_OF_FD = 10;
    public static final String NO_THIS_ID_READ = "DONT_KNOW_THIS_ID_READ";
    public static final String NO_THIS_ID_WRITE = "DONT_KNOW_THIS_ID_WRITE";

    public static void main(String[] args) {

        HashMap<Integer,File> idsFIles = new HashMap<>();
        HashMap<Integer, serversFdsInfo> filesServer = new HashMap<>();
        Integer num = 1;


        //new Structures
        HashMap<fileID,serversFdsInfo> filesInServer = new HashMap<>();
        if (args.length == 0) {
            System.err.println(RED + "Give an argument that will be the server root directory");
            return;
        }
        System.out.println("Starting root directort is :" + args[0]);
        directory = new File(args[0]);

        File hiddenfolder = new File(directory+ "/hidden");

        try {
            if(hiddenfolder.createNewFile()){
                FileOutputStream out = new FileOutputStream(hiddenfolder);
                String ses = "0";
                out.write(ses.getBytes());
                out.close();
            }
            else {
                FileInputStream read = new FileInputStream(hiddenfolder);
                byte [] bytes = new byte[(int) hiddenfolder.length()];
                int buff = read.read(bytes);
                String s = new String(bytes, StandardCharsets.UTF_8);
                session = Integer.parseInt(s);
                session = session + 1;
                FileOutputStream out = new FileOutputStream(hiddenfolder);
                String ses = new String(String.valueOf(session));
                out.write(ses.getBytes());
                out.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        //with this way all files will take

        try {
            System.out.println(InetAddress.getLocalHost());
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
//        serverSocket = null;

        try {
            serverSocket = new DatagramSocket(4001);
            System.out.println("Ip: "+ serverSocket.getLocalAddress() +" Port: "+serverSocket.getLocalPort());
        } catch (SocketException e) {
            e.printStackTrace();
        }



        DatagramPacket packet = null;
        int ids = 0;
        int numOfFds = 0;
        while (true) {
            try {
                byte[] buffer = new byte[1024];
                System.out.println(serverSocket.getLocalAddress());
                packet = new DatagramPacket(buffer, buffer.length, serverSocket.getInetAddress(), serverSocket.getLocalPort());
                System.out.println("Paw ston server gia receive");

                serverSocket.receive(packet);

                System.out.println(packet.getLength());


                ByteArrayInputStream baos = new ByteArrayInputStream(buffer);
                ObjectInputStream oos = new ObjectInputStream(baos);
                udpMessage receiveMessage = (udpMessage) oos.readObject();

                if(receiveMessage == null){
                    continue;
                }

                String type = checkMessageType(receiveMessage);

                if (type.equals("Open")) {
                    System.out.println("New open request to Server id " + ids);
                    udpMessageOpen openMsg = (udpMessageOpen) receiveMessage;
                    System.out.println("Request file name: " + openMsg.getFileName());

                    //check if file exist , else check if it is possible to open new file
                    int exist = -1;

                    Set<fileID> Keys = filesInServer.keySet();
                    for(fileID temp : Keys){
                        serversFdsInfo file = filesInServer.get(temp);
                        if(file.getFile().getName().equals(openMsg.getFileName())){
                            exist = 1;
                        }
                    }

                    if(exist == -1){
                        int check = canServer(openMsg,ids,packet.getAddress(),packet.getPort());
                        if(check < 0){
                            continue;
                        }
                    }

                    Path newPath  = Paths.get(directory +"/"+ openMsg.getFileName());
                    File newFile = new File(String.valueOf(newPath));

                    int nextId = -1;

                    nextId =  fillAccessMode(openMsg,filesInServer,newFile,ids);

                    System.out.println("nextID" + nextId);

                    fileID newfd =  existedID(nextId,filesInServer);

//                    fileDescriptor fd = new fileDescriptor(newfd,openMsg.getOpenClientInt(),openMsg.getFiled().getPosFromStart());
                    openMsg.getFiled().setFd(newfd);
                    ArrayList<Integer>flags = takeFlags(newFile);
                    fileAttributes attributes = new fileAttributes(newFile.length(),flags);

                    if(nextId > ids) {
                        //NEW ADDITION OF A FILE DESCRIPTOR
                        ids = nextId;
                        FileChannel fd = generateChannel(newFile);
                        serversFdsInfo  newInfo = new serversFdsInfo(newFile,fd);
                        filesInServer.put(newfd,newInfo);

//                        openMsg.getAttributes().setSize(filesInServer.get(newfd).getFile().length());

                    }
                    else if(openMsg.getFlags().contains(O_TRUNC)){
                        Set<StandardOpenOption> ops = new TreeSet<>();
                        ops.add(StandardOpenOption.TRUNCATE_EXISTING);
                        FileChannel fdnew = FileChannel.open(Paths.get(newFile.getPath()),ops);

                        filesInServer.get(newfd).setFd(fdnew);
//                        openMsg.getAttributes().setSize(filesInServer.get(newfd).getFile().length());

                    }
                    else {
                        attributes.setSize(0);
//                        openMsg.getAttributes().setSize(0);
                    }

                    udpMessageOpen serverAnswer = new udpMessageOpen("Open",openMsg.getFlags(),openMsg.getOpenACK(),openMsg.getFiled().getClientID(),attributes,openMsg.getFiled());
//                    try {
//                        Thread.sleep(30000);
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
                    sendUdpMessage(serverAnswer,packet.getAddress(),packet.getPort());
                }
                else if(type.equals("Read")){
                    System.out.println("New read request to Server");
                    udpMessageRead readMsg = (udpMessageRead) receiveMessage;


                    fileID check = null;
                    check = existFileId(filesInServer,readMsg.getFd().getFd());


                    if(check == null){
                        //return that it must opened again//we dont know this fileId
                        System.out.println("MPHKA EDW read");
                        udpMessageDontKnowThisID answer = new udpMessageDontKnowThisID(NO_THIS_ID_READ,type,readMsg.getFd(),readMsg.getReadClientInt());
                        sendUdpMessage(answer,packet.getAddress(),packet.getPort());
                        continue;
                    }

                    FileChannel currentChannel = filesInServer.get(check).getFd();

                    currentChannel.position(readMsg.getFd().getPosFromStart());

//                    if(readMsg)
                    int size = 0;
                    if(readMsg.getSize() > 1024){
                        size = 1024;
                    }
                    else {
                        size = (int) readMsg.getSize();
                    }
                    ByteBuffer byteBuffer = ByteBuffer.allocate(size);

                    long read = currentChannel.read(new ByteBuffer[]{byteBuffer});

                    if (read <= 0) {
                        System.out.println("PROblem" + read);
                    }

                   String s = new String(byteBuffer.array(), StandardCharsets.UTF_8);
                   System.out.println(s);
                   readMsg.getAttributes().setSize(filesInServer.get(check).getFile().length());
                   Msg msg = new Msg(s);
                   readMsg.setReadMsg(msg);

                   readMsg.getFd().setPosFromStart(currentChannel.position());

                   fileAttributes attributes = new fileAttributes(readMsg.getAttributes().getSize());
                   udpMessageRead serverAnswer = new udpMessageRead("Read",msg,readMsg.getReadClientInt(),readMsg.getFd(),attributes);

                   sendUdpMessage(serverAnswer,packet.getAddress(),packet.getPort());
                }
                else if(type.equals("Write")){
                    System.out.println("New write request to Server");
//
                    udpMessageWrite writeΜsg = (udpMessageWrite) receiveMessage;
                    fileID check = null;

                    check = existFileId(filesInServer,writeΜsg.getFd().getFd());

                    if(check == null){
                        //return that it must opened again//we dont know this fileId
                        System.out.println("MPHKA EDW write");
                        udpMessageDontKnowThisID answer = new udpMessageDontKnowThisID(NO_THIS_ID_WRITE,type,writeΜsg.getFd(),writeΜsg.getWriteClientInt());
                        sendUdpMessage(answer,packet.getAddress(),packet.getPort());
                        continue;
                    }

                    FileChannel writeChannel = filesInServer.get(check).getFd();

                    writeChannel.position(writeΜsg.getFd().getPosFromStart());
//                    writeChannel.position(100);
                    int size = 0;
                    if(writeΜsg.getSize() > 1024){
                        size = 1024;
                    }
                    else {
                        size = (int) writeΜsg.getSize();
                    }
                    ByteBuffer byteBuffer = ByteBuffer.wrap(writeΜsg.getWriteMsg().getMsg().getBytes());


                    long read = writeChannel.write(new ByteBuffer[]{byteBuffer});

                    if (read <= 0) {
                        System.out.println("PROblem" + read);
                    }

                    writeΜsg.getAttributes().setSize(writeChannel.size());
                    writeΜsg.getFd().setPosFromStart(writeChannel.position());

                    udpMessageWrite retMsg = new udpMessageWrite("Write",writeΜsg.getWriteClientInt(),writeΜsg.getFd(),writeΜsg.getAttributes());
                    sendUdpMessage(retMsg,packet.getAddress(),packet.getPort());
//

                }

            } catch (UnknownHostException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }

    }

    public static fileID existedID(int id,HashMap<fileID,serversFdsInfo> fds){
        Set<fileID> keys = fds.keySet();

        for(fileID temp : keys){
           if(temp.getFd() == id){
               return temp;
           }
        }

        fileID newfd = new fileID(id,session);
        return  newfd;
    }
    public static String readFunc(long start,long end,FileInputStream read){
        byte[] byteBuffer = new byte[1024];
        String ret = "";
        long temp =  end-start;
        int readbytes = 0;
        try {
            read.skip(start);
        } catch (IOException e) {
            e.printStackTrace();
        }
        while(temp > 0){

            System.out.println(temp);
            long temp2 = temp - 1024;
            if(temp2 <= 0){
                readbytes = readbytes + (int)temp;
                temp =  0;
//                start = start + (int)temp;
            }
            else {
                temp = temp - 1024;
                readbytes = readbytes + 1024;
//                start = start + 1024;
            }
            try {
                read.read(byteBuffer,0,readbytes);
            } catch (IOException e) {
                e.printStackTrace();
            }

            ret = ret + new String(byteBuffer,StandardCharsets.UTF_8);
        }
        return ret;
    }
//    public static long fileId(File newFile){
//
//        Long inode = null;
//        try {
//            inode = (Long) Files.getAttribute(newFile.toPath(), "unix:ino");
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//
////        long fileKey = (long) attr.fileKey();
//
//
//        return inode;
//    }
//    public static File[] sortingFilesOfDirectory(File directory) {
//        File[] files = directory.listFiles();
//        assert files != null;
//        Arrays.sort(files, new Comparator<File>() {
//            public int compare(File f1, File f2) {
//                long l1 = fileId(f1);
//                long l2 = fileId(f2);
//                return Long.valueOf(l1).compareTo(l2);
//            }
//        });
//
//        return files;
//    }
//    public static  long getFileCreation(File f1){
//        try {
//            BasicFileAttributes attr = Files.readAttributes(f1.toPath(),
//                    BasicFileAttributes.class);
//            return attr.creationTime()
//                    .toInstant().toEpochMilli();
//        } catch (IOException e) {
//            throw new RuntimeException(f1.getAbsolutePath(), e);
//        }
//    }
    public static String checkMessageType(udpMessage msg){

        if(msg.getType().equals("Open")){
            return "Open";
        }
        else if(msg.getType().equals("Read")){
            return "Read";
        }
        else if(msg.getType().equals("Write")){
            return "Write";
        }
        else return null;

    }
    public static udpMessage receiveUdpMessages() {
        byte[] buffer = new byte[1024];
        try {
            System.out.println(serverSocket.getLocalAddress());
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, serverSocket.getLocalAddress(), serverSocket.getLocalPort());
//            serverSocket.setSoTimeout(1);
            serverSocket.receive(packet);
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

    public static void sendUdpMessage(udpMessage msg,InetAddress ip, int port){
        udpMessage msg2 = null;
        ObjectOutputStream oos = null;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            oos = new ObjectOutputStream(baos);
            oos.writeObject(msg);
            byte[] byteMsg = baos.toByteArray();

            DatagramPacket packet = new DatagramPacket(byteMsg, byteMsg.length,ip, port);
            serverSocket.send(packet);
            System.out.println(packet.getLength());
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public static ArrayList<Integer> takeFlags(File newFile){
        ArrayList<Integer> flags = new ArrayList<>();

        if(newFile.canWrite()&& newFile.canRead()){
            flags.add(O_RDWR);
        }
        else if(newFile.canRead()){
            flags.add(O_RDONLY);
        }
        else if(newFile.canWrite()){
            flags.add(O_WRONLY);
        }
        return flags;
    }

    public  static int returnIdFile(HashMap<fileID,serversFdsInfo> fds, udpMessageOpen open) {
        Set<fileID> keys = fds.keySet();

        for(fileID temp : keys){
            serversFdsInfo file = fds.get(temp);

            if(file.getFile().getName().equals(open.getFileName())){
                return temp.getFd();
            }
        }

        return -1;
    }


    public static int fillAccessMode(udpMessageOpen openMsg,HashMap<fileID,serversFdsInfo> fds,File newFile,int lastID){
        int nextId = -2;

        if(openMsg.getFlags().contains(O_CREAT)) {
            try {
                if (openMsg.getFlags().contains(O_EXCL)) {
                    //
                    if (newFile.exists()) {
                        //return error cause file exists
                        nextId = E_EXIST;
                        return nextId;
                    }
                }
                if (openMsg.getFlags().contains(O_TRUNC)) {
//                    O_TRUNC = 1;
                    if (newFile.exists()) {
                        if (newFile.canWrite()) {
                            //delete contents and set fd to zero
                            PrintWriter pw = new PrintWriter(newFile);
                            pw.close();
                        }
                    }
                }

                if (newFile.createNewFile()) {
                    nextId = lastID + 1;
                    return nextId;
                } else {
                    if (openMsg.getFlags().contains(O_RDONLY)) {
                        if (newFile.canRead()) {
                            nextId = returnIdFile(fds, openMsg);
                            if(nextId == -1){
                                nextId = lastID + 1;
                                return nextId;
                            }
                            return nextId;
                        } else {
                            return ERROR;
                        }
                    } else if (openMsg.getFlags().contains(O_WRONLY)) {
                        if (newFile.canWrite()) {
                            System.out.println("mphka");
                            nextId = returnIdFile(fds, openMsg);
                            if(nextId ==-1){
                                nextId = lastID + 1;
                                return nextId;
                            }
                            return nextId;
                        }
                        else {
                            System.out.println("mphka error");
                            return ERROR;
                        }
                    } else if (openMsg.getFlags().contains(O_RDWR)) {
                        if (newFile.canRead() && newFile.canWrite()) {
                            nextId = returnIdFile(fds, openMsg);
                            if(nextId ==-1){
                                nextId = lastID + 1;
                                return nextId;
                            }
                            return nextId;
                        }
                        else {
                            return  ERROR;
                        }
                    }
                    //if exists in already opened files
                    nextId = returnIdFile(fds, openMsg);

                    if(nextId ==-1){
                        nextId = lastID + 1;
                        return nextId;
                    }

                    return nextId;
                }
            } catch(IOException e){
                e.printStackTrace();
            }
        }
        else {
            if(newFile.exists()) {
                if (openMsg.getFlags().contains(O_TRUNC)) {
//                O_TRUNC = 1;
                    if (newFile.exists()) {
                        if (newFile.canWrite()) {
                            //delete contents and set fd to zero
                            PrintWriter pw = null;
                            try {
                                pw = new PrintWriter(newFile);
                            } catch (FileNotFoundException e) {
                                e.printStackTrace();
                            }
                            pw.close();
                        }
                    }
                }

//                nextId = returnIdFile(fds, openMsg);
//
//                if(nextId == -1){
//                    nextId = lastID + 1;
//                    return  nextId;
//                }
//                else {
//                    return nextId;
//                }
                if (openMsg.getFlags().contains(O_RDONLY)) {
                    if (newFile.canRead()) {
                        nextId = returnIdFile(fds, openMsg);
                        if(nextId == -1){
                            nextId = lastID + 1;
                            return  nextId;
                        }
                        return nextId;
                    }
                    else {
                        return  ERROR;
                    }
                } else if (openMsg.getFlags().contains(O_WRONLY)) {
                    if (newFile.canWrite()) {
                        nextId = returnIdFile(fds, openMsg);
                        if(nextId == -1){
                            nextId = lastID + 1;
                            return  nextId;
                        }
                        return nextId;
                    }
                } else if (openMsg.getFlags().contains(O_RDWR)) {
                    if (newFile.canRead() && newFile.canWrite()) {
                        nextId = returnIdFile(fds, openMsg);
                        if(nextId == -1){
                            nextId = lastID + 1;
                            return  nextId;
                        }
                        return nextId;
                    }
                    else {
                        return ERROR;
                    }
                }
                else {
                    nextId = returnIdFile(fds, openMsg);
                    if(nextId == -1){
                        nextId = lastID + 1;
                        return  nextId;
                    }
                    return nextId;
                }
            }
        }
        return  nextId;
    }

    public static FileChannel generateChannel(File newfile){

        Set<StandardOpenOption> ops = new TreeSet<>();


        ops.add(StandardOpenOption.READ);
        ops.add(StandardOpenOption.WRITE);

        if(ops.isEmpty()){
            try {
                FileChannel fd = FileChannel.open(Paths.get(newfile.getPath()));
                return fd;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        else {
            try {
                FileChannel fd = FileChannel.open(Paths.get(newfile.getPath()),ops);
                return fd;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public static int canServer(udpMessageOpen openMsg,int ids,InetAddress ip ,int port){

        if(ids + 1 > MAX_NUM_OF_FD){
            //return no more fds can ...
            openMsg.getAttributes().setSize(0);
            fileID newfd = new fileID(-1,-1);
            udpMessageMaxCapacityAnswer answer = new udpMessageMaxCapacityAnswer("MAX_CAPACITY", newfd,openMsg.getOpenClientInt(),openMsg.getAttributes());
            sendUdpMessage(answer,ip,port);
            return  -1;
        }
        return 1;
    }

    public static fileID existFileId(HashMap<fileID,serversFdsInfo> fd,fileID currentfd){
        Set<fileID> keys = fd.keySet();
        for(fileID temp : keys){
            serversFdsInfo file = fd.get(temp);

            if(temp.getFd() == currentfd.getFd() && temp.getSession() == currentfd.getSession()){
                return temp;
            }
        }

        return null;
    }

}
