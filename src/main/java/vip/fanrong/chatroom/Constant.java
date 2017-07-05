package vip.fanrong.chatroom;

import java.nio.charset.Charset;

/**
 * @author r0n9 <fanrong330@gmail.com>
 */
public class Constant {
    public static final Charset CHARSET = Charset.forName("UTF-8");


    public static final String USAGE_MESSAGE = "Usage: "
            + "\n  SIGN_UP <username> <password> "
            + "\n  SIGN_IN <username> <password> "
            + "\n  SIGN_OUT"
            + "\n  QUIT"
            + "\n  BROADCAST_ALL_MSG <message content>"
            + "\n";

    public static final String SERVER_BROADCAST_PREFIX = "*** BROADCAST MSG: ";
    public static final String SERVER_SYS_MEG_PREFIX = "*** SERVER SYSTEM MSG: ";

    public static final String USER_EXIST = SERVER_SYS_MEG_PREFIX + "user exists.";
    public static final String USER_SIGNIN = SERVER_SYS_MEG_PREFIX + "user sign in:";

}
