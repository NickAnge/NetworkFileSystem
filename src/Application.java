import java.util.EnumSet;

public class Application {

    public static void main(String[] args) {
        NfsClient client = new NfsClient();
        EnumSet<Flag> flag = null;

        flag.add(Flag.O_CREAT);
        client.myNfs_open("takhs",flag);

    }
}
