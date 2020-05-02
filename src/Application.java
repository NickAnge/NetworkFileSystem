import java.util.EnumSet;

public class Application {

    public static void main(String[] args) {
        NfsClient client = new NfsClient();
        EnumSet<Flag> flag = null;
        client.myNfs_init("192.168.2.2",41534,0,0,0);

//        flag.add(Flag.O_CREAT);
        client.myNfs_open("kouple",EnumSet.of(Flag.O_CREAT,Flag.O_RDONLY));

    }
}
