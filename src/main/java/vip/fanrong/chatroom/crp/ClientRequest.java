package vip.fanrong.chatroom.crp;

import vip.fanrong.chatroom.cmd.*;
import vip.fanrong.utils.JSONUtil;

/**
 * @author r0n9 <fanrong330@gmail.com>
 */
public class ClientRequest {

    public static enum ClientCMD {
        SIGN_UP(SignUp.class), //
        SIGN_IN(SignIn.class), //
        SIGN_OUT(SignOut.class), //
        QUIT(Quit.class), //
        HELP(Object.class), //
        BROADCAST_ALL_MSG(MessageBroadcastToAll.class), //
        BROADCAST_FRIENDS_MSG(MessageBroadcastToFriends.class), //
        PRIVATE_MSG(MessagePrivateToFriend.class), //
        GROUP_MSG(MessageToGroup.class), //
        GET_ONLINE_ALL(GetOnlineAll.class), //
        GET_ONLINE_FRIENDS(GetOnlineFriends.class), //
        GET_GROUPS(GetGroups.class), //
        HEART_BEAT(Object.class); //

        private Class<?> clazz;

        ClientCMD(Class<?> clazz) {
            this.clazz = clazz;
        }

        public Class<?> getClazz() {
            return clazz;
        }
    }

    private ClientCMD clientCMD;

    private String requestContent;

    public ClientRequest fromMessage(String msg) {
        return (ClientRequest) JSONUtil.fromJson(msg, ClientRequest.class);
    }

    public String toMessage() {
        return JSONUtil.toString(this, ClientRequest.class);
    }

    public ClientCMD getClientCMD() {
        return clientCMD;
    }

    public void setClientCMD(ClientCMD clientCMD) {
        this.clientCMD = clientCMD;
    }

    public String getRequestContent() {
        return requestContent;
    }

    public void setRequestContent(String requestContent) {
        this.requestContent = requestContent;
    }


}
