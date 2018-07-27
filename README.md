# Socket_01
server socket android  java
  

支持 android  java 服务端多客户端处理 build-1 

初始化 
```java
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
        
```      
支持自定义解析数据 
```java
multiServerSocket.setProcessMsg(new MultiServerSocket.ProcessMsg() {
        @Override
        public MultiServerSocket.Data getData(String str) {
            MultiServerSocket.Data data = new MultiServerSocket.Data();
            if (str.startsWith("-")) {
                data.setHeartMsg("-");
            } else {
                try {
                    JSONObject obj = new JSONObject(str);

                    if(obj.has("map"))  data.setMap(obj.getInt("map"));
                    if(obj.has("name")){
                        data.setName(obj.getString("name"));
                    }else if(obj.has("orgName")){
                        data.setName(obj.getString("orgName"));
                    }

                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            return data;
        }
    });
```
