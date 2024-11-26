package cn.scs.client;

import cn.scs.common.Config;
import cn.scs.common.FileInfo;
import cn.scs.common.MetaOpCode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import sun.rmi.runtime.Log;



public class MetaServerClient {

    private static final String META_SERVRE_HOST = Config.META_SERVRE_HOST; // 元数据服务器的主机名
    private static final int METADATA_SERVER_PORT = Config.META_SERVRE_PORT; // 元数据服务器的端口号
    private Connection connection;

    private static final Logger log = Logger.getLogger(MetaServerClient.class);

    public MetaServerClient() throws IOException {
        connection = new Connection(META_SERVRE_HOST, METADATA_SERVER_PORT);
    }

    public Connection getConnection(){
        return connection;
    }

    public static void main(String[] args) {
        MetaServerClient metaServerClient = null;
        try {
            metaServerClient = new MetaServerClient();

            // 发送请求并接收响应
            List<String> files = metaServerClient.listFiles("/");
            metaServerClient.createFile("/home");
            metaServerClient.createFile("/scs");
            metaServerClient.createFile("/home/1/a");
            System.out.println("files:"+files);
            files = metaServerClient.listFiles("/");
            System.out.println("files:"+files);
            metaServerClient.connection.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public List<String> listFiles(String cur_dir) {
        List<String> fl = new ArrayList<>();
        try {
            MetaOpCode.LIST_FILE.write(connection.getOut()); //
            connection.flush();
            connection.writeUTF(cur_dir); // 客户端发送路径
            connection.flush();



            int size =  connection.readInt();
            System.out.println("list size:" + size);
            for(int i = 0;i<size;i++){
                fl.add(connection.readUTF());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return fl;
    }

    public String heartBeat(String nodeName) {
        try {
            MetaOpCode.HEART_BEAT.write(connection.getOut()); //
            connection.flush();
            connection.writeUTF(nodeName); // 读取客户端发送路径
            connection.flush();
            int code = connection.readInt();
            String msg = connection.readUTF();
            return msg;

        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public FileInfo getFileInfo(String path) {
        try {
            MetaOpCode.GET_FILE_INFO.write(connection.getOut());
            connection.flush();
            connection.writeUTF(path);
            connection.flush();

            int code = connection.readInt();
            if (code == 0) {
                String fileName = connection.readUTF();
                boolean isDirectory = connection.readBoolean();
                String owner = connection.readUTF();
                long fileSize = connection.readLong();
                List<String> locations = new ArrayList<>();
                int locationCount = connection.readInt();
                for (int i = 0; i < locationCount; i++) {
                    locations.add(connection.readUTF());
                }
                FileInfo fileInfo = new FileInfo(fileName, path, isDirectory, fileSize, owner, System.currentTimeMillis());
                fileInfo.setLocations(locations);
                return fileInfo;
            } else {
                String msg = connection.readUTF();
                log.error("Failed to get file info for path: " + path + ", reason: " + msg);
            }
        } catch (IOException e) {
            log.error("IOException while getting file info for path: " + path, e);
        }
        return null;
    }

    public String createFile(String path) {
        try {
            MetaOpCode.CREATE_FILE.write(connection.getOut()); //
            connection.flush();
            connection.writeUTF(path); // 读取客户端发送路径
            connection.writeUTF(Config.USER); // 读取客户端发送用户
            connection.writeBoolean(false); // 是否为目录

            connection.flush();
            int code = connection.readInt();
            String nodeAndFileId = connection.readUTF();
            String nodeName = nodeAndFileId.split(":")[0];
            String localFileId = nodeAndFileId.split(":")[1];
            System.out.println(nodeName+":"+localFileId);
            return nodeAndFileId;

        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }



    public void close() throws IOException {
        this.connection.close();
    }

    // 假设还有其他辅助方法的实现...
    public List<String> getReplicas(String path) {
        try {
            connection.writeUTF("GET_REPLICAS");
            connection.writeUTF(path);
            connection.flush();

            int count = connection.readInt();
            List<String> replicas = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                replicas.add(connection.readUTF());
            }
            return replicas;
        } catch (IOException e) {
            log.error("Failed to get replicas: " + e.getMessage());
            return Collections.emptyList();
        }
    }


}

