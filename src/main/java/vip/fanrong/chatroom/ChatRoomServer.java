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
 * ����������
 * <p>
 * ����1���ͻ���ͨ��Java NIO���ӵ�����ˣ�֧�ֶ�ͻ��˵����ӡ�
 * ����2���ͻ��˳�������ʱ�����Լ���HELP�õ�����
 * ����3��֧��ע���˺ţ���½�˺ţ��㲥��Ϣ���ǳ������˳��ͻ��˵ȹ��ܡ�
 * ����4��֧���û���������֤��½
 * ����5���ͻ��˳�ʱ���޻�����Զ����ߣ����һ�㲥֪ͨ���������û���
 * <p>
 * TODO �ͻ������������û�����
 * TODO ����˽�Ź���
 * TODO ���Ӻ���Ⱥ�Ĺ���
 *
 * @author r0n9 <fanrong330@gmail.com>
 */
public class ChatRoomServer {

    private static final Logger LOGGER = Logger.getLogger(ChatRoomServer.class);

    private Selector selector = null;

    // ������¼�ͻ�������
    private TimeCacheMap<String, SocketChannel> timeCacheMap;

    // ��¼�û�������
    private Map<String, String> userPswd;

    private void init() throws IOException {
        selector = Selector.open();
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress(Configuration.PORT)); // ����˿�
        serverChannel.configureBlocking(false); // �������ķ�ʽ
        serverChannel.register(selector, SelectionKey.OP_ACCEPT); // ע�ᵽѡ�����ϣ�����Ϊ����״̬

        System.out.println("Chatroom server is listening now on port: " + Configuration.PORT);
        LOGGER.info("Chatroom server is listening now on port: " + Configuration.PORT);

        userPswd = new HashMap<String, String>();

        // 5�����������Ŀͻ������ߣ����㲥��Ϣ
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

        // ÿ��һ���ӣ��������˴�ӡ��������
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

        // ����˲�����ѯ���п���ͨ�������������Կͻ��˵�����
        while (true) {
            int readyChannels = selector.select();
            if (readyChannels == 0)
                continue;
            Set<SelectionKey> selectedKeys = selector.selectedKeys(); //����ͨ�����������֪������ͨ���ļ���
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
            sc.configureBlocking(false); //������ģʽ
            sc.register(selector, SelectionKey.OP_READ); //ע��ѡ������������Ϊ��ȡģʽ���յ�һ����������Ȼ����һ��SocketChannel����ע�ᵽselector�ϣ�֮��������ӵ����ݣ��������SocketChannel����


            sk.interestOps(SelectionKey.OP_ACCEPT); //���˶�Ӧ��channel����Ϊ׼�����������ͻ�������

            System.out.println("Chatrooom server is listening from client :" + sc.getRemoteAddress());

            sc.write(Constant.CHARSET
                    .encode(Constant.SERVER_SYS_MEG_PREFIX
                            + "You have connected to server, please input your request: (Input 'help' for usage)"));
        }

        //�������Կͻ��˵����ݶ�ȡ����
        if (sk.isReadable()) {

            //���ظ�SelectionKey��Ӧ�� Channel��������������Ҫ��ȡ
            SocketChannel sc = (SocketChannel) sk.channel();

            ByteBuffer buff = ByteBuffer.allocate(1024);
            StringBuilder content = new StringBuilder();
            try {
                while (sc.read(buff) > 0) {
                    buff.flip();
                    content.append(Constant.CHARSET.decode(buff));
                }

                // �쳣�˳�
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
                //���˶�Ӧ��channel����Ϊ׼����һ�ν�������
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
                    case SIGN_UP:// ע��
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
     * �㲥��Ϣ�����е�SocketChannel��
     *
     * @param selector
     * @param except
     * @param content
     * @throws IOException
     */
    private void BroadCast(Selector selector, SocketChannel except, String content) throws IOException {
        for (SelectionKey key : selector.keys()) {
            Channel targetchannel = key.channel();
            // ���except��Ϊ�գ����ط������ʹ����ݵĿͻ���
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
