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
 *
 */
public class ChatRoomClient {

    private Selector selector = null;
    private SocketChannel sc = null;
    private String name = "";

    private Runnable heartBeat;

    private static final int TIMEOUT_SECOND = 60 * 5;

    private void init() throws IOException {
        selector = Selector.open();
        //����Զ��������IP�Ͷ˿�
        sc = SocketChannel.open(new InetSocketAddress("127.0.0.1", Configuration.PORT));
        sc.configureBlocking(false);
        sc.register(selector, SelectionKey.OP_READ);

        new Thread(new ClientSocketChannelReadThread()).start(); // �̣߳���ȡ�ӷ������˵�����

        Timer nameInputTimer = new Timer();
        nameInputTimer.schedule(new ClientExitTimerTask("Time over for input request. exit...", null),
                TIMEOUT_SECOND * 1000); // 5�����ڱ������������������˳�

        Timer msgInputTimer = null;

        ClientRequest clientRequest = new ClientRequest();

        //�����߳��� �Ӽ��̶�ȡ�������뵽��������
        Scanner scan = new Scanner(System.in);
        while (scan.hasNextLine()) {
            String line = scan.nextLine();
            String msg = null;

            if ("".equals(name) && !"".equals(line)) {
                nameInputTimer.cancel();
            }

            if (msgInputTimer != null && !"".equals(name) && !"".equals(line)) {
                msgInputTimer.cancel();
            }

            msgInputTimer = new Timer();
            msgInputTimer.schedule(new ClientExitTimerTask("Time over for input message. exit...",
                    name + Constant.USER_CONTENT_SPILIT + ClientRequest.ClientCMD.QUIT), TIMEOUT_SECOND * 1000); // ע�����5�����ڱ����л�������˳�


            if ("".equals(line))
                continue; //����������Ϣ

            if ("".equals(name) && line.startsWith(ClientRequest.ClientCMD.SIGN_UP.name())) {

                String[] arr = line.split(" ", -1);
                if (arr.length != 3) {
                    System.out.println(ClientRequest.ClientCMD.SIGN_UP.name() + " invalid parameters.");
                    continue;
                }

                name = arr[1];
                String pswd = arr[2];

                // �����ͻ��������̣߳�ÿ��10����������дһ������
                heartBeat = new HeartBeatThread(name);
                new Thread(heartBeat).start();

                SignUp signup = new SignUp();
                signup.setName(name);
                signup.setPassword(pswd);

                clientRequest.setClientCMD(ClientRequest.ClientCMD.SIGN_UP);
                clientRequest.setRequestContent(JSONUtil.toString(signup, SignUp.class));
                msg = JSONUtil.toString(clientRequest, ClientRequest.class);

            } else {

                if (ClientRequest.ClientCMD.QUIT.toString().equalsIgnoreCase(line)) {
                    Quit quit = new Quit();
                    quit.setUserName(name);

                    clientRequest.setClientCMD(ClientRequest.ClientCMD.QUIT);
                    clientRequest.setRequestContent(JSONUtil.toString(quit, Quit.class));
                    msg = JSONUtil.toString(clientRequest, ClientRequest.class);

                    sc.write(Constant.CHARSET.encode(msg));
                    System.out.println("Client exit...");
                    System.exit(0);
                }

                if (line.startsWith(ClientRequest.ClientCMD.BROADCAST_ALL_MSG.name())) {
                    String[] arr = line.split(" ", -1);

                    MessageBroadcastToAll mbta = new MessageBroadcastToAll();
                    mbta.setUserName(name);
                    mbta.setMessage(arr[1]);

                    clientRequest.setClientCMD(ClientRequest.ClientCMD.BROADCAST_ALL_MSG);
                    clientRequest.setRequestContent(JSONUtil.toString(mbta, MessageBroadcastToAll.class));
                    msg = JSONUtil.toString(clientRequest, ClientRequest.class);
                } else if (line.startsWith(ClientRequest.ClientCMD.SIGN_OUT.name())) {

                }
                // TODO
            }

            if (msg == null) {
                System.out.println("Invalid command!");
                continue;
            }

            sc.write(Constant.CHARSET.encode(msg));
        }

        scan.close();

    }

    private class ClientExitTimerTask extends TimerTask {

        String msg;
        String msgToSocketChannel;

        public ClientExitTimerTask(String msg, String msgToSocketChannel) {
            super();
            this.msg = msg;
            this.msgToSocketChannel = msgToSocketChannel;
        }

        @Override
        public void run() {
            System.out.println(msg);

            if (null != msgToSocketChannel) {
                try {
                    sc.write(Constant.CHARSET.encode(msgToSocketChannel));
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
                    Set<SelectionKey> selectedKeys = selector.selectedKeys(); //����ͨ�����������֪������ͨ���ļ���
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
                //ʹ�� NIO ��ȡ Channel�е����ݣ������ȫ�ֱ���sc��һ���ģ���Ϊֻע����һ��SocketChannel
                //sc����дҲ�ܶ�������Ƕ�
                SocketChannel sc = (SocketChannel) sk.channel();

                ByteBuffer buff = ByteBuffer.allocate(1024);
                String content = "";
                while (sc.read(buff) > 0) {
                    buff.flip();
                    content += Constant.CHARSET.decode(buff);
                }
                //��ϵͳ����֪ͨ�����Ѿ����ڣ�����Ҫ�����ǳ�
                if (Constant.USER_EXIST.equals(content)) {
                    name = "";
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
