import javax.xml.crypto.Data;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public class NfsServer {
    public  static final String RED = "\033[0;31m";
    public static DatagramSocket serverSocket;
    public static File directory;
    public static final int O_CREAT = 1; // if file does not exist create it
    public static final int O_EXCL = 2; // if file exists return error with EEXIT
    public static final int O_TRUNC = 3; // delete file and recreate it
    public static final int O_RDWR = 4; //READ and WRITE permission to the file
    public static final int O_RDONLY = 5; // READ only permission
    public static final int O_WRONLY = 6; // WRITE only permission
    public static final int E_EXIST = -1; //error if file exists with O_EXCl
    public  static final int ERROR = -2;
    public static void main(String[] args) {

        HashMap<Integer,File> idsFIles = new HashMap<>();

        if (args.length == 0) {
            System.err.println(RED + "Give an argument that will be the server root directory");
            return;
        }
        System.out.println("Starting root directort is :" + args[0]);
        directory = new File(args[0]);


        //with this way all files will take

        File[] sortedFiles = sortingFilesOfDirectory(directory);

        ArrayList<File> files = new ArrayList<>(Arrays.asList(sortedFiles));

        for(int i =0 ; i  < files.size();i++){
            System.out.println(files.get(i));
        }
        printList(sortedFiles);
        try {
            System.out.println(InetAddress.getLocalHost());
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
//        serverSocket = null;

        try {
            serverSocket = new DatagramSocket();
            System.out.println("Ip: "+ serverSocket.getLocalAddress() +" Port: "+serverSocket.getLocalPort());
        } catch (SocketException e) {
            e.printStackTrace();
        }


        DatagramPacket packet = null;
        while (true) {
            try {
                byte[] buffer = new byte[1024];
                System.out.println(serverSocket.getLocalAddress());
                packet = new DatagramPacket(buffer, buffer.length, serverSocket.getInetAddress(), serverSocket.getLocalPort());
//            serverSocket.setSoTimeout(1);

                System.out.println("Paw ston server gia receive");
                ;
                serverSocket.receive(packet);
                System.out.println(packet.getLength());
                ByteArrayInputStream baos = new ByteArrayInputStream(buffer);
                ObjectInputStream oos = new ObjectInputStream(baos);
                udpMessage receiveMessage = (udpMessage) oos.readObject();
//                udpMessage msg = receiveUdpMessages();


                if(receiveMessage == null){
                    continue;
                }

                String type = checkMessageType(receiveMessage);

                if (type.equals("Open")) {
                    System.out.println("New open request to Server");
                    udpMessageOpen openMsg = (udpMessageOpen) receiveMessage;
                    System.out.println("Request file name: " + openMsg.getFileName());

                    int nextId = -1;

                    //check for flags
                    nextId = fillAccessMode(openMsg,files);

                    if(nextId >= 0){
                        // this Hashmap will match ids with names for faster searching

                        idsFIles.put(nextId,files.get(nextId));
                    }
                    System.out.println(nextId);
                    udpMessage serverAnswer = new udpMessageOpen("Open", nextId, openMsg.getOpenClientInt(),openMsg.getFlags());
                    sendUdpMessage(serverAnswer,packet.getAddress(),packet.getPort());
//                    Thread.sleep(30000);
                }
                else if(type.equals("Read")){
                    System.out.println("New read request to Server");

                    udpMessageRead readMsg = (udpMessageRead) receiveMessage;

                   if(!idsFIles.containsKey(readMsg.getFd().getFd())){
                        File file = files.get(readMsg.getFd().getFd());
                        idsFIles.put(readMsg.getFd().getFd(),file);
                   }
                   FileInputStream reading = new FileInputStream(idsFIles.get(readMsg.getFd().getFd()));

                   reading.skip(readMsg.getFd().getPosFromStart());
                   byte [] bytes = new byte[(int) readMsg.getSize()];
                   int buff = reading.read(bytes,0,(int)readMsg.getSize());

                   String s = new String(bytes, StandardCharsets.UTF_8);
                   int len = readMsg.getFd().getPosFromStart() + (int)readMsg.getSize();
                   readMsg.getFd().setPosFromStart(len);
                   Msg msg = new Msg(s);
                   udpMessageRead serverAnswer = new udpMessageRead("Read",msg,readMsg.getReadClientInt(),readMsg.getFd());
                   sendUdpMessage(serverAnswer,packet.getAddress(),packet.getPort());


                }
                else if(type.equals("Write")){

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


    public static File[] sortingFilesOfDirectory(File directory) {
        File[] files = directory.listFiles();
        assert files != null;
        Arrays.sort(files, new Comparator<File>() {
            public int compare(File f1, File f2) {
                long l1 = getFileCreation(f1);
                long l2 = getFileCreation(f2);
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
        }
    }

    public  static int returnIdFile(ArrayList<File> files, String fname){

        int i =0;
        for(File file : files){
            if(!file.isDirectory()){
                if(file.getName().equals(fname)){
                    return i;
                }
            }
            i++;
        }
        return -1;
    }


    public static void checkOpenFlags(EnumSet<Flag> flags){


    }

    public static int fillAccessMode(udpMessageOpen openMsg,ArrayList<File> files){
        int nextId = -2;
//        int O_EXCL = -1;
//        int O_TRUNC = -1;

        if(openMsg.getFlags().contains(O_CREAT)) {
            File newFile = new File(directory + "/" + openMsg.getFileName());
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

                    files.add(newFile);
                    nextId = files.size() - 1;
                    return nextId;
                } else {
                    if (openMsg.getFlags().contains(O_RDONLY)) {
                        if (newFile.canRead()) {
                            nextId = returnIdFile(files, openMsg.getFileName());
                            return nextId;
                        } else {
                            return ERROR;
                        }
                    } else if (openMsg.getFlags().contains(O_WRONLY)) {
                        if (newFile.canWrite()) {
                            System.out.println("mphka");
                            nextId = returnIdFile(files, openMsg.getFileName());
                            return nextId;
                        }
                        else {
                            System.out.println("mphka error");
                            return ERROR;
                        }
                    } else if (openMsg.getFlags().contains(O_RDWR)) {
                        if (newFile.canRead() && newFile.canWrite()) {
                            nextId = returnIdFile(files, openMsg.getFileName());
                            return nextId;
                        }
                        else {
                            return  ERROR;
                        }
                    }
//                    return  nextId;
                    nextId = returnIdFile(files, openMsg.getFileName());
                    return nextId;
                }
            } catch(IOException e){
                e.printStackTrace();
            }
        }
        else {
            File newFile = new File(directory + "/" + openMsg.getFileName());
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
                        nextId = returnIdFile(files, openMsg.getFileName());
                        return nextId;
                    }
                } else if (openMsg.getFlags().contains(O_WRONLY)) {
                    if (newFile.canWrite()) {
                        nextId = returnIdFile(files, openMsg.getFileName());
                        return nextId;
                    }
                } else if (openMsg.getFlags().contains(O_RDWR)) {
                    if (newFile.canRead() && newFile.canWrite()) {
                        nextId = returnIdFile(files, openMsg.getFileName());
                        return nextId;
                    }
                }
            }
        }
        return  nextId;
    }
}
