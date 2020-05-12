
import java.util.*;

public class Application {
    public  final String GREEN = "\033[0;32m";
    public static final String RED_BOLD = "\033[1;31m";    // RED
    public static final String CYAN_BOLD = "\033[1;36m";   // CYAN
    public static final String YELLOW_BOLD = "\033[1;33m"; // YELLOW
    public static final String WHITE_BOLD = "\033[1;37m";  // WHITE
    public static final String WHITE = "\033[0;37m";   // WHITE
    public static final String RESET = "\033[0m";  // Text Reset
    public static final String RED = "\033[0;31m";     // RED
    public static final String GREEN_BOLD = "\033[1;32m";  // GREEN
    public static HashMap<Integer,String> appfds = new HashMap<>();
    public  static Scanner in = new Scanner(System.in);
    public static NfsClient client = new NfsClient();

    public static void main(String[] args) {
//        NfsClient client = new NfsClient();
//        EnumSet<Flag> flag = null;

        client.myNfs_init("192.168.2.2",4001,10,1000,100);

//        Scanner in = new Scanner(System.in);
//        HashMap<Integer,String> appfds = new HashMap<>();

        client.currentTimeInSeconds();
        while(true){
            System.out.println(RED_BOLD + "MENU: ");
            System.out.println(RED_BOLD + "    1) Open:");
            System.out.println(RED_BOLD + "    2) Read:");
            System.out.println(RED_BOLD + "    3) Write:");
            System.out.println(RED_BOLD + "    4) seek:");
            System.out.println(RED_BOLD + "    5) close:");


            int choice = in.nextInt();
            String fname;

            switch (choice) {
                case 1:
                    int open = 0;
                    System.out.print(CYAN_BOLD + "File name: ");
                    fname = in.next();
                    System.out.print(CYAN_BOLD + "Flags: O_CREAT(1), O_EXCL(2), O_TRUNC(3), O_RDWR(4), O_RDONLY(5), O_WRONLY(6): ");
                    String flags = in.next();
                    String[] flags2 = flags.split(",",7);
                    ArrayList<Integer> listFlags = new ArrayList<>();

                    for(int i =0;i < flags2.length;i++){
                        listFlags.add(Integer.parseInt(flags2[i]));
                    }
                    open = client.myNfs_open(fname,listFlags);
                    if(open < 0 ) {
                        if(open == -1){
                            System.err.println(RED + "The file already exists(ERROR: E_EXIST)");
                        }
                        else if(open == -2){
                            System.err.println(RED+ "Error returned");
                        }
                        continue;
                    }

                    appfds.put(open,fname);

                    System.out.println("File descripotor at applciation: " + open);

                    break;
                case 2:

                    Msg check = read();
                    if(check == null){
                        System.err.println(RED + "Read returned an error");
                        break;
                    }
                    int writeInt = write(check);

                    if(writeInt < 0 ){
                        System.err.println(RED + "Write returned an error");
                    }
                    break;
                case 3:
                    int fdWrite = -1;
                    while(true){
                        System.out.println(RED_BOLD + "Choose File descriptor");
                        printfds(appfds);
                        fdWrite = in.nextInt();
                        if(appfds.containsKey(fdWrite) || fdWrite == -1){
                            break;
                        }
                    }
                    if(fdWrite == -1){
                        break;
                    }
                    int write = 0;
                    in.nextLine();
                    System.out.print(RED_BOLD + "What do you want to write");
                    String s = in.nextLine();
                    Msg buff = new Msg(s.getBytes());
                    System.out.println(s);
                    write = client.myNfs_write(fdWrite,buff,s.length());

                    if(write < 0){
                        System.err.println(RED_BOLD  + "Error with write");

                    }
                    break;
                case 4:
                    int fdseek = -1;
                    while(true){
                        System.out.println(RED_BOLD + "Choose File descriptor");
                        printfds(appfds);
                        fdseek = in.nextInt();
                        if(appfds.containsKey(fdseek) || fdseek == -1){
                            break;
                        }
                    }

                    System.out.print(RED_BOLD + "How many bytes: ");
                    int seek = 0;
                    int pos  = in.nextInt();
                    System.out.print(CYAN_BOLD + "Flags: SEEK_SET(10), SEEK_CUR(11), SEEK_END(12): ");
                    int whence = in.nextInt();
                    seek = client.myNfs_seek(fdseek,pos,whence);
                    if(seek < 0){
                        System.err.println("Error with seek");
                    }
                    break;
                case 5:
                    int fdclose = -1;
                    while(true){
                        System.out.println(RED_BOLD + "Choose File descriptor");
                        printfds(appfds);
                        fdclose = in.nextInt();
                        if(appfds.containsKey(fdclose) || fdclose == -1){
                            break;
                        }
                    }
                    if(fdclose == -1){
                        break;
                    }
                    int close = client.myNfs_close(fdclose);
                    if(close < 0){
                        System.err.println("Error with close");
                    }
                    break;


            }

        }


    }

    public static void printfds(HashMap<Integer,String> appFds){
        Set<Integer> keys = appFds.keySet();
        for(Integer temp : keys){
            System.out.println( GREEN_BOLD+ "File Descriptor:" + temp + " (Filename: " + appFds.get(temp) + ")");
        }
    }

    public static int write(Msg buff){
        int fdWrite = -1;
        while(true){
            System.out.println(RED_BOLD + "Choose File descriptor");
            printfds(appfds);
            fdWrite = in.nextInt();
            if(appfds.containsKey(fdWrite) || fdWrite == -1){
                break;
            }
        }
        if(fdWrite == -1){
            return -1;
        }
        int write = 0;
        in.nextLine();
        if(buff == null){
            System.out.print(RED_BOLD + "What do you want to write");
            String s = in.nextLine();
            buff = new Msg(s.getBytes());
            System.out.println(s);
        }

        write = client.myNfs_write(fdWrite,buff,buff.msg.length);

        if(write < 0){
            System.err.println("Error with write");
        }
        return write;
    }

    public static Msg read(){
        int fd = -1;

        while(true){
            System.out.println(RED_BOLD + "Choose File descriptor");
            printfds(appfds);
            fd = in.nextInt();
            if(appfds.containsKey(fd) || fd == -1){
                break;
            }
        }
        if(fd == -1){
            return null;
        }
        System.out.print(RED_BOLD + "How many bytes: ");
        int read = 0;

        int bytes  = in.nextInt();

        byte [] bytesMsg = new byte[bytes];

        Msg msg = new Msg(bytesMsg);

        read = client.myNfs_read(fd,msg, bytes);

        if(read < 0 ){
            System.err.println(RED + "Read returned an error");
            return null;
        }

        System.out.println("Bytes"+ msg.getMsg().length);

        return msg;
    }


}