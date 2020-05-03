
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Scanner;

public class Application {
    public  final String GREEN = "\033[0;32m";
    public static final String RED_BOLD = "\033[1;31m";    // RED
    public static final String CYAN_BOLD = "\033[1;36m";   // CYAN
    public static final String YELLOW_BOLD = "\033[1;33m"; // YELLOW
    public static final String WHITE_BOLD = "\033[1;37m";  // WHITE
    public static final String WHITE = "\033[0;37m";   // WHITE
    public static final String RESET = "\033[0m";  // Text Reset
    public static final String RED = "\033[0;31m";     // RED

    public static void main(String[] args) {
        NfsClient client = new NfsClient();
        EnumSet<Flag> flag = null;

        client.myNfs_init("192.168.2.2",Integer.parseInt(args[0]),0,0,0);

        Scanner in = new Scanner(System.in);
        int fd = 0;

        while(true){
            System.out.println(RED_BOLD + "MENU: ");
            System.out.println(RED_BOLD + "    1) Open:");
            System.out.println(RED_BOLD + "    2) Read:");
            System.out.println(RED_BOLD + "    3) Write:");
            System.out.println(RED_BOLD + "    3) seek:");
            System.out.println(RED_BOLD + "    3) close:");


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
//                    for(int)
                    ArrayList<Integer> listFlags = new ArrayList<>();
                    for(int i =0;i < flags2.length;i++){
                        listFlags.add(Integer.parseInt(flags2[i]));
                    }
//                    ArrayList<String> flags3 =
                    open = client.myNfs_open(fname,listFlags);

                    fd = open;
                    System.out.println("File descripotor at applciation: " + open);
                    break;
                case 2:

                    int read = 0;
                    String b = null;
                    read = client.myNfs_read(fd,b, 100);
            }
        }


    }
}
