import javax.xml.crypto.Data;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;

public class NfsServer {
    public  static final String RED = "\033[0;31m";
    public static DatagramSocket serverSocket;
    public static void main(String[] args) {

        if (args.length == 0) {
            System.err.println(RED + "Give an argument that will be the server root directory");
            return;
        }
        System.out.println("Starting root directort is :" + args[0]);
        File directory = new File(args[0]);


        //with this way all files will take

        File[] sortedFiles = sortingFilesOfDirectory(directory);

        ArrayList<File> files = new ArrayList<>(Arrays.asList(sortedFiles));

        for(int i =0 ; i  < files.size();i++){
            System.out.println(files.get(i));
        }
        printList(sortedFiles);
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
                System.out.println("werwrqweS");
                if (type.equals("Open")) {
                    System.out.println("New open request to Server");
                    udpMessageOpen openMsg = (udpMessageOpen) receiveMessage;
                    System.out.println("Request file name: " + openMsg.getFileName());
                    int nextId = -1;
                    if(openMsg.getFlags().contains(Flag.O_CREAT) && openMsg.getFlags().contains(Flag.O_EXCL)) {
                        System.out.println("CREATE AND EXCL");
                    }

                    if(openMsg.getFlags().contains(Flag.O_CREAT)){
                        File newFile = new File(directory+"/"+openMsg.getFileName());
                        if(newFile.createNewFile()){
                                if(openMsg.getFlags().contains(Flag.O_RDONLY)){
                                    newFile.setReadOnly();
                                }
                                else if(openMsg.getFlags().contains(Flag.O_WRONLY)){
                                    newFile.setWritable(true);
                                }
                                else if(openMsg.getFlags().contains(Flag.O_RDWR)) {
                                    newFile.setWritable(true);
                                    newFile.setReadable(true);
                                }

                                files.add(newFile);
                                nextId = files.size() - 1;
                        }
                        else {
                            nextId = returnIdFile(sortedFiles,openMsg.getFileName());
                        }

                    }

                    System.out.println(nextId);
                    udpMessage serverAnswer = new udpMessageOpen("Open", nextId, openMsg.getOpenClientInt());
                    sendUdpMessage(serverAnswer,packet.getAddress(),packet.getPort());
                    Thread.sleep(30000);

                }

            } catch (UnknownHostException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                    e.printStackTrace();
             } catch (InterruptedException e) {
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

    public  static int returnIdFile(File[] files, String fname){

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


}
