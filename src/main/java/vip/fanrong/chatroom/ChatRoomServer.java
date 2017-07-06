package vip.fanrong.chatroom;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import vip.fanrong.chatroom.cmd.*;
import vip.fanrong.chatroom.crp.ClientRequest;
import vip.fanrong.utils.JSONUtil;
import vip.fanrong.utils.TimeCacheMap;

/**
 * 网络聊天室
 * <p>
 * 功能1：客户端通过Java NIO连接到服务端，支持多客户端的连接。
 * 功能2：客户端初次连接时，可以键入HELP得到帮助
 * 功能3：支持注册账号，登陆账号，广播消息，登出或者退出客户端等功能。
 * 功能4：支持用户名密码验证登陆
 * 功能5：客户端长时间无活动将会自动下线，并且会广播通知其他所有用户。
 * <p>
 * TODO 客户端请求在线用户名单
 * TODO 增加私信功能
 * TODO 增加好友群聊功能
 *
 * @author r0n9 <fanrong330@gmail.com>
 */
public class ChatRoomServer {

    private static final Logger LOGGER = Logger.getLogger(ChatRoomServer.class);

    private Selector selector = null;

    // 用来记录客户端心跳
    private TimeCacheMap<String, SocketChannel> timeCacheMap;

    // 记录用户名密码
    private Map<String, String> userPswd;

    private void init() throws IOException {
        selector = Selector.open();
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress(Configuration.PORT)); // 服务端口
        serverChannel.configureBlocking(false); // 非阻塞的方式
        serverChannel.register(selector, SelectionKey.OP_ACCEPT); // 注册到选择器上，设置为监听状态

        System.out.println("Chatroom server is listening now on port: " + Configuration.PORT);
        LOGGER.info("Chatroom server is listening now on port: " + Configuration.PORT);

        userPswd = new HashMap<String, String>();

        // 5分钟无心跳的客户端下线，并广播消息
        timeCacheMap = new TimeCacheMap<String, SocketChannel>(60 * 5,
                new TimeCacheMap.ExpiredCallback<String, SocketChannel>() {

                    @Override
                    public void expire(String key, SocketChannel socketChannel) {
                        System.out.println("No heatbeat from user [" + key + "], offline.");
                        try {
                            if (null != socketChannel && socketChannel.isOpen())
                                socketChannel.close();
                            BroadCast(selector, socketChannel, "[" + key + "] is offline. ");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });

        // 每隔一分钟，服务器端打印在线名单
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(60 * 1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    System.out.println("Online members: " + timeCacheMap.keySet());
                }

            }
        }).start();

        // 服务端不断轮询所有可用通道，并处理来自客户端的请求
        while (true) {
            int readyChannels = selector.select();
            if (readyChannels == 0)
                continue;
            Set<SelectionKey> selectedKeys = selector.selectedKeys(); //可以通过这个方法，知道可用通道的集合
            Iterator<SelectionKey> keyIterator = selectedKeys.iterator();
            while (keyIterator.hasNext()) {
                SelectionKey sk = keyIterator.next();
                keyIterator.remove();
                dealWithSelectionKey(serverChannel, sk);
            }
        }
    }

    private void dealWithSelectionKey(ServerSocketChannel serverChannel, SelectionKey sk) throws IOException {


        if (sk.isAcceptable()) {
            SocketChannel sc = serverChannel.accept();
            sc.configureBlocking(false); //非阻塞模式
            sc.register(selector, SelectionKey.OP_READ); //注册选择器，并设置为读取模式，收到一个连接请求，然后起一个SocketChannel，并注册到selector上，之后这个连接的数据，就由这个SocketChannel处理


            sk.interestOps(SelectionKey.OP_ACCEPT); //将此对应的channel设置为准备接受其他客户端请求

            System.out.println("Chatrooom server is listening from client :" + sc.getRemoteAddress());

            sc.write(Constant.CHARSET
                    .encode(Constant.SERVER_SYS_MEG_PREFIX
                            + "You have connected to server, please input your request: (Input 'help' for usage)"));
        }

        //处理来自客户端的数据读取请求
        if (sk.isReadable()) {

            //返回该SelectionKey对应的 Channel，其中有数据需要读取
            SocketChannel sc = (SocketChannel) sk.channel();

            ByteBuffer buff = ByteBuffer.allocate(1024);
            StringBuilder content = new StringBuilder();
            try {
                while (sc.read(buff) > 0) {
                    buff.flip();
                    content.append(Constant.CHARSET.decode(buff));
                }

                // 异常退出
                if ("".equals(content.toString().trim())) {
                    String name = null;
                    for (String key : timeCacheMap.keySet()) {
                        if (timeCacheMap.get(key) == sc) {
                            name = key;
                            break;
                        }
                    }

                    if (name != null) {
                        timeCacheMap.remove(name);
                    }

                    sc.close();
                    sk.cancel();

                    BroadCast(selector, sc, ">>" + name + "<< has left. ");

                    return;
                }

                System.out.println(
                        "Server is listening from client " + sc.getRemoteAddress() + " data rev is: [" + content + "]");
                //将此对应的channel设置为准备下一次接受数据
                sk.interestOps(SelectionKey.OP_READ);
            } catch (IOException io) {
                sk.cancel();
                if (sk.channel() != null) {
                    sk.channel().close();
                }
                return;
            }


            if (content.length() > 0) {
                ClientRequest request = (ClientRequest) JSONUtil.fromJson(content.toString(), ClientRequest.class);

                ClientRequest.ClientCMD cmd = request.getClientCMD();
                String requestContent = request.getRequestContent();
                String name = null;
                String password = null;
                switch (cmd) {
                    case SIGN_UP:// 注册
                        SignUp signUpContent = (SignUp) JSONUtil.fromJson(requestContent, SignUp.class);
                        name = signUpContent != null ? signUpContent.getUsername() : null;
                        password = signUpContent.getPassword();

                        if (userPswd.containsKey(name)) {
                            sc.write(Constant.CHARSET.encode(Constant.USER_EXIST));
                        } else {
                            userPswd.put(name, password);
                            sc.write(
                                    Constant.CHARSET.encode(Constant.SERVER_SYS_MEG_PREFIX + "user sign up:" + name));
                        }
                        break;
                    case SIGN_IN:
                        SignIn signInContent = (SignIn) JSONUtil.fromJson(requestContent, SignIn.class);

                        name = signInContent != null ? signInContent.getUsername() : null;
                        password = signInContent.getPassword();

                        if (password != null && password.equals(userPswd.get(name))) {

                            if (timeCacheMap.containsKey(name)) {
                                sc.write(Constant.CHARSET
                                        .encode(Constant.SERVER_BROADCAST_PREFIX + "user is already online:" + name));

                                timeCacheMap.get(name).write(Constant.CHARSET
                                        .encode(Constant.SERVER_BROADCAST_PREFIX
                                                + "your accout is trying to sign in from other place: "
                                                + sc.getRemoteAddress()));
                            } else {
                                sc.write(Constant.CHARSET.encode(Constant.USER_SIGNIN + name));
                                timeCacheMap.put(name, sc);

                                int num = timeCacheMap.size();
                                String message = "Welcome [" + name + "] to chat room! Online numbers: " + num;
                                BroadCast(selector, sc, message);
                            }
                        } else if (password != null && !password.equals(userPswd.get(name))) {
                            sc.write(Constant.CHARSET
                                    .encode(Constant.SERVER_BROADCAST_PREFIX + "incorrect username or password:"
                                            + name));
                        }

                        break;
                    case SIGN_OUT:
                        SignOut signout = (SignOut) JSONUtil.fromJson(requestContent, SignOut.class);
                        SocketChannel signoutSC = timeCacheMap.remove(signout != null ? signout.getUsername() : null);
                        if (null != signoutSC && signoutSC.isOpen())
                            signoutSC.close();
                        BroadCast(selector, null, "[" + signout.getUsername() + "] has left. ");
                        break;
                    case BROADCAST_ALL_MSG:
                        MessageBroadcastToAll msgAllContent =
                                (MessageBroadcastToAll) JSONUtil.fromJson(requestContent, MessageBroadcastToAll.class);
                        name = msgAllContent != null ? msgAllContent.getUserName() : null;
                        if (timeCacheMap.containsKey(name)) {
                            timeCacheMap.put(name, sc);
                            BroadCast(selector, sc, "[" + name + "] Say: " + msgAllContent.getMessage());
                        }
                        break;
                    case HEART_BEAT:
                        HeartBeat heartBeat = (HeartBeat) JSONUtil.fromJson(requestContent, HeartBeat.class);
                        timeCacheMap.put(heartBeat != null ? heartBeat.getUserName() : null, sc);
                        break;
                    case QUIT:
                        Quit quit = (Quit) JSONUtil.fromJson(requestContent, Quit.class);

                        if ("".equals(quit != null ? quit.getUsername() : null)) {
                            sc.close();
                            break;
                        }

                        SocketChannel quitSC = timeCacheMap.remove(quit.getUsername());
                        if (null != quitSC && quitSC.isOpen())
                            quitSC.close();
                        BroadCast(selector, sc, "[" + quit.getUsername() + "] has left. ");
                        break;
                    default:
                        break;
                }
            }
        }
    }

    /**
     * 广播消息到所有的SocketChannel中
     *
     * @param selector
     * @param except
     * @param content
     * @throws IOException
     */
    private void BroadCast(Selector selector, SocketChannel except, String content) throws IOException {
        for (SelectionKey key : selector.keys()) {
            Channel targetchannel = key.channel();
            // 如果except不为空，不回发给发送此内容的客户端
            if (targetchannel instanceof SocketChannel && targetchannel != except) {
                SocketChannel dest = (SocketChannel) targetchannel;
                dest.write(Constant.CHARSET.encode(Constant.SERVER_BROADCAST_PREFIX + content));
            }
        }
    }

    private void PrivateMessage(SocketChannel sc, String content) throws IOException {
        sc.write(Constant.CHARSET.encode(content));
    }

    public static void main(String[] args) throws IOException {
        new ChatRoomServer().init();
    }
}
