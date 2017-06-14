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
 * �����ͻ���������
 * 
 * ����1���ͻ���ͨ��Java NIO���ӵ�����ˣ�֧�ֶ�ͻ��˵����ӡ�
 * ����2���ͻ��˳�������ʱ���������ʾ�����ǳƣ�����ǳ��Ѿ�����ʹ�ã���ʾ�������룬����ǳ�Ψһ�����¼�ɹ���֮������Ϣ����Ҫ���չ涨��ʽ�����ǳƷ�����Ϣ��
 * ����3���ͻ��˵�¼�󣬷����Ѿ����úõĻ�ӭ��Ϣ�������������ͻ��ˣ����㲥֪ͨ�����ͻ��˸ÿͻ������ߡ�
 * ����4���������յ��ѵ�¼�ͻ����������ݣ�ת����������¼�ͻ��ˡ�
 * ����5���ͻ����޻�������볬ʱ�����Զ����ߡ�
 * ����6��������յ��ѵ�¼�ͻ����˳����ʹ�����ߣ����㲥֪ͨ�����ͻ��˸ÿͻ������ߡ�
 * ����7�����������ܿͻ�����������ʱ�������ͻ����Զ����ߡ�
 * 
 * TODO �ͻ������������û�����
 * TODO ֧���û������¼
 * TODO �����ҹ㲥�ͺ���˽�Ź��ܣ�����һ��ServerSocketChannel
 * TODO �����ҹ㲥�ͺ���˽�Ź��ܣ�����ServerSocketChannel]
 * 
 * @author r0n9 <fanrong330@gmail.com>
 *
 */
public class ChatRoomServer {

    private static final Logger LOGGER = Logger.getLogger(ChatRoomServer.class);


    private Selector selector = null;

    // ������¼�ͻ�������
    private TimeCacheMap<String, SocketChannel> timeCacheMap;

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
                }); // 5�������������ÿͻ������ߣ����㲥��Ϣ

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
        }).start(); // ÿ��һ���ӣ��������˴�ӡ��������

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
            //������ģʽ
            sc.configureBlocking(false);
            //ע��ѡ������������Ϊ��ȡģʽ���յ�һ����������Ȼ����һ��SocketChannel����ע�ᵽselector�ϣ�֮��������ӵ����ݣ��������SocketChannel����
            sc.register(selector, SelectionKey.OP_READ);

            //���˶�Ӧ��channel����Ϊ׼�����������ͻ�������
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
                System.out.println(
                        "Server is listening from client " + sc.getRemoteAddress() + " data rev is: " + content);
                //���˶�Ӧ��channel����Ϊ׼����һ�ν�������
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
                    case SIGN_UP:// ע��
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
                dest.write(Constant.CHARSET.encode(content));
            }
        }
    }

    public static void main(String[] args) throws IOException {
        new ChatRoomServer().init();
    }
}
