package vip.fanrong.chatroom;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Scanner;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import vip.fanrong.chatroom.cmd.*;
import vip.fanrong.chatroom.crp.ClientRequest;
import vip.fanrong.utils.JSONUtil;

/**
 * @author r0n9 <fanrong330@gmail.com>
 */
public class ChatRoomClient {

    private Selector selector = null;
    private SocketChannel sc = null;
    private String name = "";

    private Runnable heartBeat;

    private static final int TIMEOUT_SECOND = 60 * 5;

    private void init() throws IOException {
        selector = Selector.open();
        //连接远程主机的IP和端口
        sc = SocketChannel.open(new InetSocketAddress("localhost", Configuration.PORT));
        sc.configureBlocking(false);
        sc.register(selector, SelectionKey.OP_READ);

        new Thread(new ClientSocketChannelReadThread()).start(); // 线程：读取从服务器端的数据

        Timer nameInputTimer = new Timer();
        nameInputTimer.schedule(new ClientExitTimerTask("Time over for input request. exit...", null),
                TIMEOUT_SECOND * 1000); // 5分钟内必须键入请求命令，否则退出

        Timer msgInputTimer = null;

        ClientRequest clientRequest = new ClientRequest();
        Quit quit = new Quit();

        //在主线程中 从键盘读取数据输入到服务器端
        Scanner scan = new Scanner(System.in);
        while (scan.hasNextLine()) {
            String line = scan.nextLine();
            String msg = null;

            if (msgInputTimer != null) {
                msgInputTimer.cancel();
            }


            quit.setUsername(name);

            clientRequest.setClientCMD(ClientRequest.ClientCMD.QUIT);
            clientRequest.setRequestContent(JSONUtil.toString(quit, Quit.class));


            msgInputTimer = new Timer();
            msgInputTimer.schedule(new ClientExitTimerTask("Time over for input message. exit...", clientRequest),
                    TIMEOUT_SECOND * 1000); // 5分钟内必须有活动，否则退出

            if ("".equals(line)) //不允许发空消息
                continue;

            if (ClientRequest.ClientCMD.HELP.toString().equalsIgnoreCase(line)) { // HELP
                System.out.println(Constant.USAGE_MESSAGE);
                continue;
            }

            if ("".equals(name) && line.startsWith(ClientRequest.ClientCMD.SIGN_IN.name())) { // 登陆
                String[] arr = line.split(" ", -1);
                if (arr.length != 3) {
                    System.out.println(ClientRequest.ClientCMD.SIGN_IN.name() + " invalid parameters.");
                    continue;
                }

                String name = arr[1];
                String pswd = arr[2];


                SignIn signin = new SignIn();
                signin.setUsername(name);
                signin.setPassword(pswd);

                clientRequest.setClientCMD(ClientRequest.ClientCMD.SIGN_IN);
                clientRequest.setRequestContent(JSONUtil.toString(signin, SignIn.class));
                msg = JSONUtil.toString(clientRequest, ClientRequest.class);

            } else {

                if (ClientRequest.ClientCMD.QUIT.toString().equalsIgnoreCase(line)) { // 退出客户端

                    quit.setUsername(name);
                    msg = JSONUtil.toString(clientRequest, ClientRequest.class);

                    sc.write(Constant.CHARSET.encode(msg));
                    System.out.println("Client exit...");
                    System.exit(0);

                } else if (line.startsWith(ClientRequest.ClientCMD.SIGN_UP.name())) { // 注册账号

                    String[] arr = line.split(" ", -1);
                    if (arr.length != 3) {
                        System.out.println(ClientRequest.ClientCMD.SIGN_UP.name() + " invalid parameters.");
                        continue;
                    }

                    String name = arr[1];
                    String pswd = arr[2];


                    SignUp signup = new SignUp();
                    signup.setUsername(name);
                    signup.setPassword(pswd);

                    clientRequest.setClientCMD(ClientRequest.ClientCMD.SIGN_UP);
                    clientRequest.setRequestContent(JSONUtil.toString(signup, SignUp.class));
                    msg = JSONUtil.toString(clientRequest, ClientRequest.class);

                } else if (line.startsWith(ClientRequest.ClientCMD.BROADCAST_ALL_MSG.name())) { // 广播消息

                    if ("".equals(name)) {
                        System.out.println("Please sign in first.");
                        continue;
                    }

                    MessageBroadcastToAll mbta = new MessageBroadcastToAll();
                    mbta.setUserName(name);
                    mbta.setMessage(line.substring(ClientRequest.ClientCMD.BROADCAST_ALL_MSG.name().length() + 1));

                    clientRequest.setClientCMD(ClientRequest.ClientCMD.BROADCAST_ALL_MSG);
                    clientRequest.setRequestContent(JSONUtil.toString(mbta, MessageBroadcastToAll.class));
                    msg = JSONUtil.toString(clientRequest, ClientRequest.class);

                } else if (line.startsWith(ClientRequest.ClientCMD.SIGN_IN.name())) { // 二次登录
                    System.out.println("You have signed in as: " + name);
                    continue;
                } else if (ClientRequest.ClientCMD.SIGN_OUT.toString().equalsIgnoreCase(line)) { // 登出
                    if ("".equals(name)) {
                        System.out.println("Please sign in first.");
                        continue;
                    }
                    SignOut signOut = new SignOut();
                    signOut.setUsername(name);

                    clientRequest.setClientCMD(ClientRequest.ClientCMD.SIGN_OUT);
                    clientRequest.setRequestContent(JSONUtil.toString(signOut, SignOut.class));
                    msg = JSONUtil.toString(clientRequest, ClientRequest.class);
                }
            }

            if (msg == null) {
                System.out.println("Invalid command!");
                System.out.println(Constant.USAGE_MESSAGE);
                continue;
            }

            sc.write(Constant.CHARSET.encode(msg));
        }

        scan.close();

    }

    private class ClientExitTimerTask extends TimerTask {

        String msg;
        ClientRequest msgToSocketChannel;

        public ClientExitTimerTask(String msg, ClientRequest msgToSocketChannel) {
            super();
            this.msg = msg;
            this.msgToSocketChannel = msgToSocketChannel;
        }

        @Override
        public void run() {
            System.out.println(msg);

            if (null != msgToSocketChannel) {
                try {
                    sc.write(Constant.CHARSET.encode(JSONUtil.toString(msgToSocketChannel, ClientRequest.class)));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            System.exit(0);
        }

    }

    private class HeartBeatThread implements Runnable {
        private String heartbeat;

        public HeartBeatThread(String name) {
            HeartBeat heartBeat = new HeartBeat();
            heartBeat.setUserName(name);

            ClientRequest clientRequest = new ClientRequest();
            clientRequest.setClientCMD(ClientRequest.ClientCMD.HEART_BEAT);
            clientRequest.setRequestContent(JSONUtil.toString(heartBeat, HeartBeat.class));
            this.heartbeat = JSONUtil.toString(clientRequest, ClientRequest.class);
        }

        @Override
        public void run() {
            while (true) {
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                try {
                    sc.write(Constant.CHARSET.encode(heartbeat));
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }

        }

    }

    private class ClientSocketChannelReadThread implements Runnable {

        @Override
        public void run() {
            try {
                while (true) {
                    int readyChannels = selector.select();
                    if (readyChannels == 0)
                        continue;
                    Set<SelectionKey> selectedKeys = selector.selectedKeys(); //可以通过这个方法，知道可用通道的集合
                    Iterator<SelectionKey> keyIterator = selectedKeys.iterator();
                    while (keyIterator.hasNext()) {
                        SelectionKey sk = keyIterator.next();
                        keyIterator.remove();
                        dealWithSelectionKey(sk);
                    }
                }
            } catch (IOException io) {

            }
        }

        private void dealWithSelectionKey(SelectionKey sk) throws IOException {
            if (sk.isReadable()) {
                //使用 NIO 读取 Channel中的数据，这个和全局变量sc是一样的，因为只注册了一个SocketChannel
                //sc既能写也能读，这边是读
                SocketChannel sc = (SocketChannel) sk.channel();

                ByteBuffer buff = ByteBuffer.allocate(1024);
                String content = "";
                while (sc.read(buff) > 0) {
                    buff.flip();
                    content += Constant.CHARSET.decode(buff);
                }
                //若系统发送通知名字已经存在，则需要换个昵称
                if (Constant.USER_EXIST.equals(content)) {
                    name = "";
                } else if (content.startsWith(Constant.USER_SIGNIN)) {
                    name = content.substring(Constant.USER_SIGNIN.length());

                    // 开启客户端心跳线程，每隔10秒往服务器写一次心跳
                    heartBeat = new HeartBeatThread(name);
                    new Thread(heartBeat).start();

                    System.out.println("*** Client: user signed in, heartbeat start: " + name);
                }

                System.out.println(content);
                sk.interestOps(SelectionKey.OP_READ);
            }
        }
    }


    public static void main(String[] args) throws IOException {
        new ChatRoomClient().init();
    }
}
