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
 * 网络多客户端聊天室
 * 
 * 功能1：客户端通过Java NIO连接到服务端，支持多客户端的连接。
 * 功能2：客户端初次连接时，服务端提示输入昵称，如果昵称已经有人使用，提示重新输入，如果昵称唯一，则登录成功，之后发送消息都需要按照规定格式带着昵称发送消息。
 * 功能3：客户端登录后，发送已经设置好的欢迎信息和在线人数给客户端，并广播通知其他客户端该客户端上线。
 * 功能4：服务器收到已登录客户端输入内容，转发至其他登录客户端。
 * 功能5：客户端无活动或者输入超时将会自动下线。
 * 功能6：服务端收到已登录客户端退出命令，使其下线，并广播通知其他客户端该客户端下线。
 * 功能7：服务器接受客户端心跳，超时无心跳客户端自动下线。
 * 
 * TODO 客户端请求在线用户名单
 * TODO 支持用户密码登录
 * TODO 聊天室广播和好友私信功能，共用一个ServerSocketChannel
 * TODO 聊天室广播和好友私信功能，两个ServerSocketChannel]
 * 
 * @author r0n9 <fanrong330@gmail.com>
 *
 */
public class ChatRoomServer {

    private static final Logger LOGGER = Logger.getLogger(ChatRoomServer.class);


    private Selector selector = null;

    // 用来记录客户端心跳
    private TimeCacheMap<String, SocketChannel> timeCacheMap;

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

        timeCacheMap = new TimeCacheMap<String, SocketChannel>(60 * 5,
                new TimeCacheMap.ExpiredCallback<String, SocketChannel>() {

                    @Override
                    public void expire(String key, SocketChannel socketChannel) {
                        LOGGER.info("No heatbeat from client, close it: " + key);
                        try {
                            if (null != socketChannel && socketChannel.isOpen())
                                socketChannel.close();
                            BroadCast(selector, socketChannel, ">>" + key + "<< has left. ");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }); // 5分钟无心跳，该客户端下线，并广播消息

        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(60 * 1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    LOGGER.info("Online members: " + timeCacheMap.keySet());
                }

            }
        }).start(); // 每隔一分钟，服务器端打印在线名单

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
            //非阻塞模式
            sc.configureBlocking(false);
            //注册选择器，并设置为读取模式，收到一个连接请求，然后起一个SocketChannel，并注册到selector上，之后这个连接的数据，就由这个SocketChannel处理
            sc.register(selector, SelectionKey.OP_READ);

            //将此对应的channel设置为准备接受其他客户端请求
            sk.interestOps(SelectionKey.OP_ACCEPT);
            System.out.println("Chatrooom server is listening from client :" + sc.getRemoteAddress());
            sc.write(Constant.CHARSET.encode("Please input your request. \nUsage: "
                    + "\n  SIGN_UP <username> <password> "
                    + "\n  SIGN_IN <username> <password> "
                    + "\n  SIGN_OUT"
                    + "\n  QUIT"
                    + "\n  BROADCAST_ALL_MSG <message content>"
                    + "\n  ..."));
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
                System.out.println(
                        "Server is listening from client " + sc.getRemoteAddress() + " data rev is: " + content);
                //将此对应的channel设置为准备下一次接受数据
                sk.interestOps(SelectionKey.OP_READ);
            } catch (IOException io) {
                sk.cancel();
                if (sk.channel() != null) {
                    sk.channel().close();
                }
            }


            if (content.length() > 0) {
                // TODO
                ClientRequest request = (ClientRequest) JSONUtil.fromJson(content.toString(), ClientRequest.class);

                ClientRequest.ClientCMD cmd = request.getClientCMD();
                String requestContent = request.getRequestContent();
                String name = null;
                String password = null;
                switch (cmd) {
                    case SIGN_UP:// 注册
                        SignUp signUpContent = (SignUp) JSONUtil.fromJson(requestContent, SignUp.class);
                        name = signUpContent.getName();
                        password = signUpContent.getPassword();

                        if (userPswd.containsKey(name)) {
                            sc.write(Constant.CHARSET.encode(Constant.USER_EXIST));
                        } else {
                            timeCacheMap.put(name, sc);
                            userPswd.put(name, password);

                            int num = timeCacheMap.size();
                            String message = "Welcome >>" + name + "<< to chat room! Online numbers: " + num;
                            BroadCast(selector, null, message);
                        }
                        break;
                    case SIGN_IN:
                        // TODO
                        break;

                    case BROADCAST_ALL_MSG:
                        MessageBroadcastToAll msgAllContent =
                                (MessageBroadcastToAll) JSONUtil.fromJson(requestContent, MessageBroadcastToAll.class);
                        name = msgAllContent.getUserName();
                        if (timeCacheMap.containsKey(name)) {
                            timeCacheMap.put(name, sc);
                            BroadCast(selector, sc, ">>" + name + "<< Say: " + msgAllContent.getMessage());
                        }
                        break;
                    case HEART_BEAT:
                        HeartBeat heartBeat = (HeartBeat) JSONUtil.fromJson(requestContent, HeartBeat.class);
                        timeCacheMap.put(heartBeat.getUserName(), sc);
                        break;
                    case QUIT:
                        Quit quit = (Quit) JSONUtil.fromJson(requestContent, Quit.class);
                        SocketChannel socketChannel = timeCacheMap.remove(quit.getUserName());
                        if (null != socketChannel && socketChannel.isOpen())
                            socketChannel.close();
                        BroadCast(selector, sc, ">>" + quit.getUserName() + "<< has left. ");
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
                dest.write(Constant.CHARSET.encode(content));
            }
        }
    }

    public static void main(String[] args) throws IOException {
        new ChatRoomServer().init();
    }
}
