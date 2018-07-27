public class Main {
    private static MultiServerSocket serverSocket;
    static int i = 0;
    public static void main(String[] args) {
        System.out.println("Hello World!");
        serverSocket = new MultiServerSocket(9006, new MultiServerSocket.SocketCallBack() {
            @Override
            public void initSocket(Boolean b) {
                System.out.println("initSocket"+b);
            }

            @Override
            public void msgCall(MultiServerSocket.Msg msg) {
                System.out.println("---"+msg.toString()+"------");

            }
        });

        serverSocket.sendFind();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                serverSocket.onDestroy();
            }
        }).start();


    }
}
