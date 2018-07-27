import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

public class MultiServerSocket {
    private final String CHARSET = "utf-8";
    private SocketCallBack socketCallBack;
    private ServerSocket serverSocket = null;
    private MessageDistributor messageDistributor;//事件分发事件 回调消息
    private boolean isAccept = false;
    private boolean isRunServer = true;
    private int port ;
    private HashMap<Long,Socket> clientSocket;
    private SendMessage sendMessage;//调socket发送消息
    private boolean isSuccessSend = false;
    private ProcessMsg processMsg;
    private int port2 = 9000;
    private String find = "find";

    public MultiServerSocket(int port, SocketCallBack socketCallBack) {
        clientSocket = new HashMap<>();
        this. port = port;
        messageDistributor = new MessageDistributor();
        messageDistributor.start();

        sendMessage = new SendMessage();
        sendMessage.start();

        this.socketCallBack = socketCallBack;
        initSocket(port);
    }

    /**
     * 初始化socket
     * @param port 端口
     */
    private void initSocket(int port){
        try {
            serverSocket = new ServerSocket(port);
            socketCallBack.initSocket(true);
            isRunServer = true;
            if(!isAccept) acceptClient();
        } catch (IOException e) {
            socketCallBack.initSocket(false);
            e.printStackTrace();
        }
    }

    /**
     * 发送消息
     * @param msg type Event.SEND long id=-1是所有都发送
     * 消息队列 返回Msg 是否发送成功
     */
    public void sendMsg(Msg msg){
        sendMessage.addMsg(msg);
    }


    /**
     * 获取连接中的socket
     */
    public synchronized void getSocketStatus(){
        if(clientSocket.size()>0){
            for (Map.Entry<Long, Socket> entry : clientSocket.entrySet()) {
                if(entry.getValue()!=null){
                    sendMessage.addMsg(new Msg(entry.getKey(),"-",Event.STATUS));
                }
            }
        }else{
            messageDistributor.addMsg(new Msg(-1,"当前没有设备连接！",Event.STATUS));
        }

    }
    /**
     *重启
     */
    public void reset( ){
        close(1);
    }

    /**
     *onDestroy
     */
    public void onDestroy(){
        close(0);
    }
    public void close(){
        close(2);
    }

    /**
     * 关闭
     * @param type
     */
    private void close(int type){
        try {
            isRunServer = false;
            if(serverSocket!=null) serverSocket.close();
            for(Map.Entry<Long,Socket> entry :clientSocket.entrySet()){
                if(entry.getKey()!=null){
                    try {
                        entry.getValue().close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            clientSocket.clear();


            if(type == 0 ){
                messageDistributor.close();
                messageDistributor.interrupt();
                sendMessage.close();
                sendMessage.interrupt();
            }else if(type == 1){
                initSocket(port);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
//        System.out.println("=========");
    }


    /**
     * 开始接受客户端 请求
     */
    private void acceptClient(){
        isAccept = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (isRunServer){
                    try {
                        Socket s = serverSocket.accept();
                        s.setKeepAlive(true);
                        long id = System.currentTimeMillis();
                        clientSocket.put(id,s);
                        new ProcessClient(s,id);
//                        messageDistributor.addMsg(new Msg(id,"有设备连接成功",2));
                    } catch (IOException e) {
                        e.printStackTrace();
                        isRunServer = false;
                    }
                }
                isAccept = false;
                System.out.println("结束acceptClient");
            }
        }).start();
    }

    /**
     * 处理 客户端信息
     */
    class ProcessClient extends Thread{
        private Socket s;
        private String ip = "";
        private InputStream is = null;
        private boolean isConn = true;
        private long id = -1;
        private ProcessClient(Socket s,long id){
            this.s = s;
            this.id = id;
            try {
                is = s.getInputStream();
                ip = s.getInetAddress().toString().replace("/","");
            } catch (IOException e) {
                isConn = false;
                e.printStackTrace();
            }
            start();

        }
        @Override
        public void run() {
            if(isConn){
           //     messageDistributor.addMsg(new Msg(id,"客户端："+ip,Event.CONN,ip));
                byte[] bt = new byte[1024];
                int len;
                try {
                    while ((len = is.read(bt)) != -1) {
                        String str = "";
                        try {
                            str = new String(bt, 0, len, CHARSET);
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                        }

                        if(str.length() > 0){
                            System.out.println("str:"+str);
                            if(processMsg!= null){
                                Data data =  processMsg.getData(str);
                                String heartMsg = data.getHeartMsg();
                                if(heartMsg!=null)  sendMessage.addMsg(new Msg(id,heartMsg,Event.SEND,ip));
                                int map = data.getMap();
                                String name = data.getName();
                                if(map >0 && name!= null)messageDistributor.addMsg(new Msg(id,name,Event.CONN,ip,map));
                            }

                        }
                    }
                    System.out.println("closeSocket");
                    closeSocket();
                } catch (IOException e) {
                    closeSocket();
                    e.printStackTrace();
                    clientSocket.remove(id);
                }
            }else{
                closeSocket();
                clientSocket.remove(id);
            }

        }

        private void closeSocket(){
            messageDistributor.addMsg(new Msg(id,ip,Event.DISCONN));

            try {
                s.close();
                if(is != null ){
                    s.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


    }


    /**
     * 事件分发器 生产者消费者关系
     */
    private class MessageDistributor extends Thread{

        private boolean isRun = true;

        private final int MAX_SIZE = 100;

        private LinkedList<Msg> list = new LinkedList<>();

        private void addMsg(Msg msg) {
            synchronized (list) {
                while (list.size() >= MAX_SIZE ) {
                    try {
                        // 由于条件不满足， 阻塞
                        list.wait();
                    } catch (InterruptedException e) {
                        isRun = false;
//                        e.printStackTrace();
                    }
                }
                list.add(msg);
                list.notifyAll();
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
//                isRun = false;
                e.printStackTrace();
            }


        }


        private void distributor() {
            while(isRun){
                synchronized (list) {
                    while (list.size() <= 0 && isRun) {
                        try {
                            // 由于条件不满足， 阻塞
                            list.wait();
                        } catch (InterruptedException e) {
                            isRun =false;
                            //    e.printStackTrace();
                        }
                    }
                    if(list.size()>0){
                        socketCallBack.msgCall(list.getFirst());
                        list.remove();
                        list.notifyAll();
                    }

                }

            }

        }

        @Override
        public void run() {
            distributor();

        }
        public void close(){
            isRun = false;
        }
    }


    /**
     * 发送 消息
     */
    private class SendMessage extends Thread{
        private boolean isRun = true;


        private LinkedList<Msg> list = new LinkedList<>();
        private void addMsg(Msg msg) {
            synchronized (list) {
                list.add(msg);
                list.notifyAll();
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                isRun = false;
//                e.printStackTrace();
            }

        }


        private void distributor() {
            while (isRun) {
                synchronized (list) {
                    while (list.size() <= 0  && isRun ) {
                        try {
                            // 由于条件不满足， 阻塞
                            list.wait();
                        } catch (InterruptedException e) {
//                            e.printStackTrace();
                            isRun = false;
                        }
                    }
                    if(list.size()>0){
                        Msg msg = list.getFirst();
                        long id = msg.id;
                        if (id == -1) {
                            for (Map.Entry<Long, Socket> entry : clientSocket.entrySet()) {
                                if (entry.getKey() != null) {
                                    try {
                                        Socket socket = entry.getValue();
                                        OutputStream os = socket.getOutputStream();
                                        os.write(msg.msg.getBytes());
                                        os.flush();
                                        msg.setId(entry.getKey());
                                        if(isSuccessSend)  messageDistributor.addMsg(msg);
                                    } catch (IOException e) {
                                        msg.setType(Event.SENDFAIL);
                                        messageDistributor.addMsg(msg);
                                        clientSocket.remove(entry.getKey());
                                        e.printStackTrace();
                                    }

                                }else{
                                    clientSocket.remove(entry.getKey());
                                }
                            }
                        } else {
                            Socket socket = clientSocket.get(msg.id);
                            if(socket!=null){
                                try {
                                    OutputStream os = socket.getOutputStream();
                                    os.write(msg.msg.getBytes());
                                    os.flush();
                                    msg.setId(msg.id);
                                    if(isSuccessSend)  messageDistributor.addMsg(msg);
                                } catch (IOException e) {
                                    msg.setType(Event.SENDFAIL);
                                    messageDistributor.addMsg(msg);
                                    clientSocket.remove(msg.id);
                                    e.printStackTrace();
                                }
                            }else{
                                clientSocket.remove(msg.id);
                            }


                        }

                        list.remove();
                        list.notifyAll();
                    }

                }

            }

        }
        @Override
        public void run() {
            distributor();
        }
        public void close(){
            isRun = false;
        }
    }




    /**
     * 发送广播
     */
    public synchronized void sendFind(){
        //自动启动发送广播链接
        try {
            DatagramSocket ds =  new DatagramSocket();
            sendMsgDatagram(ds, "255.255.255.255");
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }
    /**
     * 发送广播
     * @param sender
     * @param ip
     */
    private void sendMsgDatagram(DatagramSocket sender, String ip) {
        byte[] b = new byte[1024];
        DatagramPacket packet;
        try {
            b = (find).getBytes();
            InetAddress broadAddress = InetAddress.getByName(ip);
            packet = new DatagramPacket(b, b.length, broadAddress,
                    port2);
            sender.send(packet);
            sender.close();
        } catch (Exception e) {
            e.printStackTrace();
            sender.close();
        }
    }


    public void setProcessMsg(ProcessMsg processMsg) {
        this.processMsg = processMsg;
    }

    public int getPort2() {
        return port2;
    }
    /**
     * 需要在发送广播前 调用
     * @param   port2
     */
    public void setPort2(int port2) {
        this.port2 = port2;
    }

    public String getFind() {
        return find;
    }

    /**
     * 需要在发送广播前 调用
     * @param find
     */
    public void setFind(String find) {
        this.find = find;
    }

    public static class  Msg{
        private long id;
        private String msg;
        private String ip;
        private Event type = Event.DEFAULT;
        private int map;
        public Msg(long id,String msg,Event type,String ip,int map) {
            this.type = type;
            this.msg = msg;
            this.id = id;
            this.ip = ip;
            this.map = map;
        }
        public Msg(long id,String msg,Event type,String ip) {
            this.type = type;
            this.msg = msg;
            this.id = id;
            this.ip = ip;
        }
        public Msg(long id,String msg,Event type) {
            this.type = type;
            this.msg = msg;
            this.id = id;

        }
        public Msg(long id,String msg) {
            this.msg = msg;
            this.id = id;

        }

        public int getMap() {
            return map;
        }

        public void setMap(int map) {
            this.map = map;
        }

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public String getMsg() {
            return msg;
        }

        public void setMsg(String msg) {
            this.msg = msg;
        }

        public Event getType() {
            return type;
        }

        public void setType(Event type) {
            this.type = type;
        }

        public String getIp() {
            return ip;
        }

        public void setIp(String ip) {
            this.ip = ip;
        }

        @Override
        public String toString() {
            return "id: "+ id + " msg: " + msg + " type: "+type +" ip: "+ ip;
        }
    }

    public enum Event{
        CONN,DISCONN,DEFAULT,SEND,SENDFAIL,STATUS,MAP
    }
    public static class  Data{
        private int map;
        private String name;
        private String heartMsg;

        public int getMap() {
            return map;
        }

        public void setMap(int map) {
            this.map = map;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getHeartMsg() {
            return heartMsg;
        }

        public void setHeartMsg(String heartMsg) {
            this.heartMsg = heartMsg;
        }
    }
    public interface SocketCallBack {

        void initSocket(Boolean b);

        void msgCall(MultiServerSocket.Msg msg);

    }
    public interface ProcessMsg{
        MultiServerSocket.Data getData(String msg);
    }

}
