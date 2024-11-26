package cn.scs.impl;

// DataServer 代码
import cn.scs.client.MetaServerClient;
import cn.scs.common.Config;
import cn.scs.common.DataOpCode;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import sun.security.krb5.internal.HostAddress;
import sun.security.x509.IPAddressName;

import java.io.*;
import java.net.*;
import java.util.Date;
import java.util.List;

public class DataServer {
    private static Logger log = LogManager.getLogger(DataServer.class);
    String nodeName;
    boolean isRunning = false;
    int DATA_SERVRE_PORT = 9526;
    int END_STREAM = -1;
    String storage_path;
    MetaServerClient metaClient;
    ServerSocket serverSocket;
    HeartBeatThread heartBeat;

    public DataServer() {
        try {
            // 初始化节点名称
            this.nodeName = InetAddress.getLocalHost().getHostName();

            // 初始化存储路径
            storage_path = Config.STORAGE_PATH;
            if (storage_path == null || storage_path.isEmpty()) {
                throw new IllegalArgumentException("Storage path is not configured.");
            }
            File storageDir = new File(storage_path);
            if (!storageDir.exists()) {
                boolean created = storageDir.mkdirs();
                if (!created) {
                    throw new IOException("Failed to create storage directory: " + storage_path);
                }
            }

            // 初始化服务器套接字
            serverSocket = new ServerSocket(DATA_SERVRE_PORT);

            // 初始化心跳线程和元数据客户端
            metaClient = new MetaServerClient();
            heartBeat = new HeartBeatThread();

            System.out.println("DataServer initialized successfully.");
        } catch (IOException e) {
            log.error("Failed to initialize DataServer: " + e.getMessage(), e);
            throw new RuntimeException("DataServer initialization failed.", e);
        }
    }


    private void serve() {
        if (serverSocket == null || storage_path == null || heartBeat == null) {
            throw new IllegalStateException("DataServer is not properly initialized.");
        }
        System.out.println("┏======================================┓");
        System.out.println("|数据服务已经启动|Data Server is running ");
        System.out.println("┗======================================┛");
        isRunning = true;

        // 启动心跳线程
        new Thread(heartBeat).start();

        while (isRunning) {
            Socket clientSocket = null;
            try {
                // 接受客户端连接
                clientSocket = serverSocket.accept();
                System.out.println("Accepted connection from client: " + clientSocket.getRemoteSocketAddress());

                // 初始化输入输出流
                DataInputStream in = new DataInputStream(clientSocket.getInputStream());
                DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());

                // 读取客户端请求
                DataOpCode op = null;
                try {
                    op = DataOpCode.read(in);
                    System.out.println("Received operation: " + op);
                } catch (Exception e) {
                    log.error("Error reading operation code: " + e.getMessage(), e);
                    continue;
                }

                // 处理请求
                if (op != null) {
                    process(op, in, out);
                } else {
                    log.warn("Received null operation code. Skipping request.");
                }
            } catch (IOException e) {
                log.error("Error in server operation: " + e.getMessage(), e);
            } finally {
                // 确保关闭客户端连接
                if (clientSocket != null) {
                    try {
                        clientSocket.close();
                        System.out.println("Client socket closed.");
                    } catch (IOException e) {
                        log.error("Failed to close client socket: " + e.getMessage(), e);
                    }
                }
            }
        }
    }

    public class HeartBeatThread implements Runnable {
        @Override
        public void run() {
            while (isRunning) {
                Socket socket = null;
                DataOutputStream out = null;
                DataInputStream in = null;
                try {
                    // 建立与元数据服务器的连接
                    socket = new Socket(Config.META_SERVRE_HOST, Config.META_SERVRE_PORT);
                    out = new DataOutputStream(socket.getOutputStream());
                    in = new DataInputStream(socket.getInputStream());

                    // 发送心跳信息
                    out.writeUTF(nodeName); // 节点名称
                    out.flush();
                    System.out.println("HeartBeat sent from node: " + nodeName);

                    // 接收元数据服务器的响应
                    int responseCode = in.readInt();
                    String responseMsg = in.readUTF();
                    System.out.println("Received from MetaServer: " + responseCode + " - " + responseMsg);

                    // 等待下一个心跳周期
                    Thread.sleep(Config.HEARTBEAT_SECS * 1000);
                } catch (IOException e) {
                    log.error("HeartBeat failed: " + e.getMessage());
                    try {
                        // 如果心跳失败，等待重试间隔
                        System.out.println("Retrying heartbeat after " + Config.TIMEOUT_OF_HEARTBEATS + " seconds...");
                        Thread.sleep(Config.TIMEOUT_OF_HEARTBEATS * 1000);
                    } catch (InterruptedException ex) {
                        log.error("Retry sleep interrupted: " + ex.getMessage());
                    }
                } catch (InterruptedException e) {
                    System.out.println("HeartBeat thread interrupted: " + e.getMessage());
                    isRunning = false; // 如果线程被中断，停止运行
                } finally {
                    try {
                        if (out != null) out.close();
                        if (in != null) in.close();
                        if (socket != null) socket.close();
                    } catch (IOException e) {
                        log.error("Failed to close HeartBeat connection: " + e.getMessage());
                    }
                }
            }
        }
    }



    // 处理客户端请求并返回响应
    protected final void process(DataOpCode op, DataInputStream in, DataOutputStream out) throws IOException {
        switch(op) {
            case WRITE_FILE:
                writeFile(in,out);
                break;
            case READ_FILE:
                readFile(in,out);
                break;
            default:
                throw new IOException("Unknown op " + op + " in data stream");
        }
    }



    private void writeFile(DataInputStream in, DataOutputStream out) {
        try {
            String fileId = in.readUTF();
            String path = Config.STORAGE_PATH + File.separator + fileId;
            FileOutputStream fout = new FileOutputStream(path);

            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                fout.write(buffer, 0, bytesRead);
            }
            fout.close();

            out.writeInt(0);
            out.writeUTF("File written successfully: " + path);

            // 同步写入到副本节点
            notifyReplicas(fileId, buffer);
        } catch (IOException e) {
            log.error("Failed to write file: " + e.getMessage());
            try {
                out.writeInt(-1);
                out.writeUTF(e.getMessage());
            } catch (IOException ex) {
                log.error("Failed to send error response: " + ex.getMessage());
            }
        }
    }

    private void readFile(DataInputStream in, DataOutputStream out) {
        try {
            String fileId = in.readUTF(); // 文件 ID
            String path = Config.STORAGE_PATH + "/" + fileId; // 使用配置路径
            File file = new File(path);

            if (!file.exists()) {
                out.writeInt(-1);
                out.writeUTF("File not found: " + path);
                return;
            }

            FileInputStream fin = new FileInputStream(file);
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = fin.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            fin.close();

            out.writeInt(0); // 成功代码
            out.flush();
        } catch (IOException e) {
            log.error("Failed to read file: " + e.getMessage());
            try {
                out.writeInt(-1); // 失败代码
                out.writeUTF(e.getMessage());
                out.flush();
            } catch (IOException ex) {
                log.error("Failed to send error response: " + ex.getMessage());
            }
        }
    }


    private void notifyReplicas(String fileId, byte[] data) {
        List<String> replicaNodes = metaClient.getReplicas(fileId); // 获取副本节点
        for (String node : replicaNodes) {
            try {
                String[] parts = node.split(":");
                String host = parts[0];
                int port = Integer.parseInt(parts[1]);

                Socket socket = new Socket(host, port);
                DataOutputStream replicaOut = new DataOutputStream(socket.getOutputStream());

                replicaOut.writeUTF(fileId);
                replicaOut.write(data);
                replicaOut.flush();
                replicaOut.close();
                socket.close();

                System.out.println("Replica sent to " + node);
            } catch (IOException e) {
                log.error("Failed to send replica to " + node + ": " + e.getMessage());
            }
        }
    }



    private static void sendMessageToMetaServer(Socket socket, String message) throws IOException {
        PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
        writer.println(message);
    }

    public void shutdown() {
        isRunning = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            log.error("Failed to close server socket: " + e.getMessage());
        }
    }



    public static void main(String[] args) {
        DataServer dataServer = null;
        try {
            // 初始化 DataServer
            dataServer = new DataServer();

            // 启动心跳线程
            Thread heartBeatThread = new Thread(dataServer.heartBeat);
            heartBeatThread.start();
            System.out.println("HeartBeat thread started for node: " + dataServer.nodeName);

            // 启动数据服务器服务
            dataServer.serve();
        } catch (Exception e) {
            System.err.println("DataServer failed to start: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // 确保在程序结束时释放资源
            if (dataServer != null) {
                dataServer.shutdown(); // 自定义方法，清理资源
            }
            System.out.println("DataServer has been shut down.");
        }
    }
}

