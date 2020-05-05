
import java.io.*;
import java.net.*;
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
    public  static  int MAX_NUM_OF_FD = 5;

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

//                FileChannel newFd = new File
                serverSocket.receive(packet);

                System.out.println(packet.getLength());


                ByteArrayInputStream baos = new ByteArrayInputStream(buffer);
                ObjectInputStream oos = new ObjectInputStream(baos);
                udpMessage receiveMessage = (udpMessage) oos.readObject();

                if(receiveMessage == null){
                    continue;
                }


                String type = checkMessageType(receiveMessage);
                if(numOfFds + 1 > MAX_NUM_OF_FD){
                    //return no more fds can ...
                }

                if (type.equals("Open")) {
                    numOfFds ++;
                    System.out.println("New open request to Server");
                    udpMessageOpen openMsg = (udpMessageOpen) receiveMessage;
                    System.out.println("Request file name: " + openMsg.getFileName());

                    Path newPath  = Paths.get(directory +"/"+ openMsg.getFileName());
                    File newFile = new File(String.valueOf(newPath));

                    int nextId = -1;


                    nextId =  fillAccessMode(openMsg,filesInServer,newFile,ids);
                    fileID newfd =  new fileID(nextId,session);

                    ArrayList<Integer> flags = new ArrayList<>();

                    if(nextId > ids){
                        ids = nextId;
                        flags = openMsg.getFlags();
                    }
                    else if(nextId >= 0){
                        //take already exist files
                        flags =  takeFlags(newFile);
                    }
                    if(nextId >= 0){

                        // this Hashmap will match ids with names for faster searching

                        FileChannel fd = generateChannel(newFile,openMsg);
//                        FileChannel fd = FileChannel.open(newPath);

                        serversFdsInfo  newInfo = new serversFdsInfo(newFile,fd);
                        //add this file descriptor to temporary structure; limit to 5 file descriptors.
                        filesInServer.put(newfd,newInfo);

                        //size of file
                        openMsg.getAttributes().setSize(filesInServer.get(newfd).getFile().length());
                    }

                    System.out.println("id"+ newfd.getFd());
                    udpMessage serverAnswer = new udpMessageOpen("Open", newfd, openMsg.getOpenClientInt(),flags,openMsg.getAttributes());
                    sendUdpMessage(serverAnswer,packet.getAddress(),packet.getPort());
                }
                else if(type.equals("Read")){
//                    System.out.println("New read request to Server");
//                    udpMessageRead readMsg = (udpMessageRead) receiveMessage;
//
//                   if(!idsFIles.containsKey(readMsg.getFd().getFd())){
//                        File file = files.get(readMsg.getFd().getFd());
//                        idsFIles.put(readMsg.getFd().getFd(),file);
//                   }
//
//                   FileInputStream reading = new FileInputStream(idsFIles.get(readMsg.getFd().getFd()));
//
//                   reading.skip(readMsg.getFd().getPosFromStart());
//                   byte [] bytes = new byte[(int) readMsg.getSize()];
//                   int buff = reading.read(bytes,0,(int)readMsg.getSize());
//
//                   String s = new String(bytes, StandardCharsets.UTF_8);
//                   long len = (long) (readMsg.getFd().getPosFromStart() + readMsg.getSize());
//                   readMsg.getFd().setPosFromStart(len);
//                   Msg msg = new Msg(s);
//
//                   fileAttributes attributes = new fileAttributes(idsFIles.get(readMsg.getFd().getFd()).length());
//                   udpMessageRead serverAnswer = new udpMessageRead("Read",msg,readMsg.getReadClientInt(),readMsg.getFd(),attributes);
//
//
//                   sendUdpMessage(serverAnswer,packet.getAddress(),packet.getPort());


                }
                else if(type.equals("Write")){
//                    System.out.println("New write request to Server");
//
//                    udpMessageWrite writeΜsg = (udpMessageWrite) receiveMessage;
//
//                    if(!idsFIles.containsKey(writeΜsg.getFd().getFd())){
//                        File file = files.get(writeΜsg.getFd().getFd());
//                        idsFIles.put(writeΜsg.getFd().getFd(),file);
//                    }
////                    FileOutputStream out = new FileOutputStream(idsFIles.get(writeΜsg.getFd().getFd()));
//                    FileInputStream read = new FileInputStream(idsFIles.get(writeΜsg.getFd().getFd()));
//                    try {
//                        Thread.sleep(10000);
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
//                    byte [] byteBuffer = new byte[1024];
//                    long posStart = writeΜsg.getFd().getPosFromStart();
//
//                    int len = 0;
//
//                    long temp = posStart;
//                    System.out.println("PosSTART  :"+ posStart);
//
//                    String readMsg = readFunc(0,writeΜsg.getFd().getPosFromStart(),read);
//
//                    System.out.println(readMsg);
//                    readMsg = readMsg + writeΜsg.getWriteMsg().msg;
//                    //CAREFULL LONG TO INT
//
//                    System.out.println("readMsg"+ readMsg);
//
//                    writeΜsg.getFd().setPosFromStart(posStart + writeΜsg.getWriteMsg().getMsg().length());
//                    writeΜsg.getAttributes().setSize(idsFIles.get(writeΜsg.getFd().getFd()).length());
//
//
//                    System.out.println("start"+ writeΜsg.getFd().getPosFromStart());
//                    System.out.println("SIZE :"+ writeΜsg.getAttributes().getSize());
//                    readMsg = readMsg + readFunc(writeΜsg.getFd().getPosFromStart(),writeΜsg.getAttributes().getSize(),read);
//
//                    System.out.println(readMsg);
//
//                    byte[] b = readMsg.getBytes();
////                    out.write(b);
//
//                    long lenWrite = (long) (writeΜsg.getFd().getPosFromStart() + writeΜsg.getSize());
//                    writeΜsg.getFd().setPosFromStart(lenWrite);
////                    Msg msg = new Msg(s);
//
//                    fileAttributes attributes = new fileAttributes(idsFIles.get(writeΜsg.getFd().getFd()).length());
//                    udpMessageWrite serverAnswer = new udpMessageWrite("Write",writeΜsg.getWriteClientInt(),writeΜsg.getFd(),attributes);
//
//
//                    sendUdpMessage(serverAnswer,packet.getAddress(),packet.getPort());
//                    while(read.read())


//                    byte [] bytes = new byte[(int) readMsg.getSize()];


//                    out.write();
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
    public static long fileId(File newFile){

        Long inode = null;
        try {
            inode = (Long) Files.getAttribute(newFile.toPath(), "unix:ino");
        } catch (IOException e) {
            e.printStackTrace();
        }

//        long fileKey = (long) attr.fileKey();


        return inode;
    }
    public static File[] sortingFilesOfDirectory(File directory) {
        File[] files = directory.listFiles();
        assert files != null;
        Arrays.sort(files, new Comparator<File>() {
            public int compare(File f1, File f2) {
                long l1 = fileId(f1);
                long l2 = fileId(f2);
                return Long.valueOf(l1).compareTo(l2);
            }
        });

        return files;
    }
    public static  long getFileCreation(File f1){
        try {
            BasicFileAttributes attr = Files.readAttributes(f1.toPath(),
                    BasicFileAttributes.class);
            return attr.creationTime()
                    .toInstant().toEpochMilli();
        } catch (IOException e) {
            throw new RuntimeException(f1.getAbsolutePath(), e);
        }
    }
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


    public static  void  printList(File[] files){
        for(File file : files){
            System.out.println(file.getName());
            Path path = Paths.get(file.getAbsolutePath());
            try {
                BasicFileAttributes att = Files.readAttributes(path,BasicFileAttributes.class);
                System.out.println(att.fileKey());
            } catch (IOException e) {
                e.printStackTrace();
            }

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

        return 1;
    }


    public static int fillAccessMode(udpMessageOpen openMsg,HashMap<fileID,serversFdsInfo> fds,File newFile,int lastID){
        int nextId = -2;
//        int O_EXCL = -1;
//        int O_TRUNC = -1;

//        File newFile = new File(directory + "/" + openMsg.getFileName());

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
                    if (openMsg.getFlags().contains(O_RDONLY)) {
                        newFile.setReadOnly();
                    } else if (openMsg.getFlags().contains(O_WRONLY)) {
                        newFile.setReadable(false);
                        newFile.setWritable(true);
                    } else if (openMsg.getFlags().contains(O_RDWR)) {
                        newFile.setWritable(true);
                        newFile.setReadable(true);
                    }

                    nextId = lastID + 1;

                    return nextId;
                } else {
                    if (openMsg.getFlags().contains(O_RDONLY)) {
                        if (newFile.canRead()) {
                            nextId = returnIdFile(fds, openMsg);
                            return nextId;
                        } else {
                            return ERROR;
                        }
                    } else if (openMsg.getFlags().contains(O_WRONLY)) {
                        if (newFile.canWrite()) {
                            System.out.println("mphka");
                            nextId = returnIdFile(fds, openMsg);
                            return nextId;
                        }
                        else {
                            System.out.println("mphka error");
                            return ERROR;
                        }
                    } else if (openMsg.getFlags().contains(O_RDWR)) {
                        if (newFile.canRead() && newFile.canWrite()) {
                            nextId = returnIdFile(fds, openMsg);
                            return nextId;
                        }
                        else {
                            return  ERROR;
                        }
                    }
                    nextId = returnIdFile(fds, openMsg);
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
                if (openMsg.getFlags().contains(O_RDONLY)) {
                    if (newFile.canRead()) {
                        nextId = returnIdFile(fds, openMsg);
                        return nextId;
                    }
                    else {
                        return  ERROR;
                    }
                } else if (openMsg.getFlags().contains(O_WRONLY)) {
                    if (newFile.canWrite()) {
                        nextId = returnIdFile(fds, openMsg);
                        return nextId;
                    }
                } else if (openMsg.getFlags().contains(O_RDWR)) {
                    if (newFile.canRead() && newFile.canWrite()) {
                        nextId = returnIdFile(fds, openMsg);
                        return nextId;
                    }
                    else {
                        return ERROR;
                    }
                }
                else {
                    nextId = returnIdFile(fds, openMsg);
                    return nextId;
                }
            }
        }
        return  nextId;
    }

    public static FileChannel generateChannel(File newfile,udpMessageOpen open){
        Set<StandardOpenOption> ops = new TreeSet<>();

        if(open.getFlags().contains(O_WRONLY)){
            ops.add(StandardOpenOption.WRITE);
        }
        else if (open.getFlags().contains(O_RDWR)){
            ops.add(StandardOpenOption.READ);
            ops.add(StandardOpenOption.WRITE);
        }
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
}
