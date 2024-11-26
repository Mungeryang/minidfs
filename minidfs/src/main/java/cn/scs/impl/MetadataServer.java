package cn.scs.impl;


import cn.scs.common.Config;
import cn.scs.common.FileInfo;
import cn.scs.common.MetaOpCode;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.List;

public class MetadataServer {
    private static Logger log = LogManager.getLogger(MetadataServer.class);
    private ServerSocket serverSocket;
    private Map<String, String> fileToStorageNode; // 文件路径到存储节点名称的映射
    private LinkedHashMap<String,StorageNode> storageNodes = new LinkedHashMap();
    private LinkedHashMap<String,Long> storageNodesUpTime = new LinkedHashMap();
    private Map<String, String> fileOwners;
    private Map<String, FileInfo> fileSystem; // 文件元数据，文件路径到文件信息的映射

    private boolean isRunning;

    public MetadataServer() {
        try {
            fileSystem = new HashMap<>();
            fileToStorageNode = new HashMap<>();
            storageNodes.put("1", new StorageNode("datanode1", "192.168.88.101", 9526));
            storageNodes.put("2", new StorageNode("datanode2", "192.168.88.102", 9526));
            // 初始化根目录
            fileSystem.put("/", new FileInfo(null, "/", true, 0L, "root", 0L));

            // 初始化服务器套接字
            serverSocket = new ServerSocket(Config.META_SERVRE_PORT);
            log.info("Server socket initialized on port " + Config.META_SERVRE_PORT);
        } catch (IOException e) {
            log.error("Failed to initialize server socket: " + e.getMessage(), e);
            System.exit(1); // 如果服务器无法启动，直接退出
        }
    }


    //改进了老师原来写的Server方法
    private void serve() {
        System.out.println("┏======================================┓");
        System.out.println("|元数据服务已经启动|MetaServer is running|");
        System.out.println("┗======================================┛");
        isRunning = true;
        while (isRunning) {
            Socket clientSocket = null;
            try {
                // 等待客户端连接
                clientSocket = serverSocket.accept();
                System.out.println("Accepted connection from client: " + clientSocket.getRemoteSocketAddress());

                // 初始化输入输出流
                DataInputStream in = new DataInputStream(clientSocket.getInputStream());
                DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());

                // 读取客户端请求操作码
                MetaOpCode op = null;
                try {
                    op = MetaOpCode.read(in);
                    System.out.println("Received operation: " + op);
                } catch (Exception e) {
                    log.error("Error reading operation code from client: " + e.getMessage());
                    continue;
                }

                // 处理客户端请求
                if (op != null) {
                    process(clientSocket, op, in, out);
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

    //
    /**
     * 处理客户端元数据请求并返回响应,处理的信息包括：
     * 注册和心跳： 数据服务器需要在启动时向元数据服务器注册自己，告知自己的可用性。随后，数据服务器可以定期发送心跳消息，以通知元数据服务器它的状态。
     * 文件信息交换： 数据服务器可以定期向元数据服务器汇报文件信息，例如已存储的文件列表、文件大小等。元数据服务器可以根据这些信息维护文件目录和存储位置信息。
     * 文件副本管理： 元数据服务器可以指导数据服务器在不同的存储节点上创建文件的副本，以增加数据冗余和容错性。
     * 文件删除和迁移： 元数据服务器可以通知数据服务器删除特定文件，也可以指导文件从一个数据服务器迁移到另一个数据服务器。
     * 数据块分配： 元数据服务器可以负责指导数据服务器如何分配文件的不同数据块，以便实现数据的分布式存储和访问。
     * 一致性和同步： 当文件信息或状态发生变化时，确保元数据服务器和数据服务器之间的信息是一致的。你可能需要考虑使用分布式一致性协议（如 Paxos、Raft）来实现这一点。
     * @throws IOException
     */
    protected final void process(Socket clientSocket, MetaOpCode op, DataInputStream in, DataOutputStream out) {
        while (isRunning) {
            try {
                switch (op) {
                    case HEART_BEAT:
                        handleHeartBeat(in, out);
                        break;
                    case CREATE_FILE:
                        createFile(in, out);
                        break;
                    case RENAME_FILE:
                        renameFile(in, out);
                        break;
                    case DEL_FILE:
                        deleteFile(in, out);
                        break;
                    case LIST_FILE:
                        listFile(in, out);
                        break;
                    default:
                        log.warn("Unknown operation code: " + op);
                }
            } catch (Exception e) {  // 捕获更广义的异常
                log.error("Error processing request: " + e.getMessage(), e);
            } finally {
                try {
                    if (clientSocket != null) {
                        clientSocket.close();
                    }
                } catch (IOException e) {
                    log.error("Error closing client socket in process method: " + e.getMessage(), e);
                }
                break; // 处理完当前请求后退出循环
            }
        }
    }



    private void renameFile(DataInputStream in, DataOutputStream out) {
        try {
            String oldPath = in.readUTF(); // 读取旧路径
            String newPath = in.readUTF(); // 读取新路径

            if (fileSystem.containsKey(oldPath)) {
                FileInfo fileInfo = fileSystem.remove(oldPath);
                fileInfo.setFileName(newPath);
                fileSystem.put(newPath, fileInfo);
                out.writeInt(0); // 成功代码
                out.writeUTF("File renamed successfully from " + oldPath + " to " + newPath);
                log.info("File renamed from " + oldPath + " to " + newPath);
            } else {
                out.writeInt(-1); // 失败代码
                out.writeUTF("File/Directory not found: " + oldPath);
                log.info("Rename failed. File/Directory not found: " + oldPath);
            }
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
            try {
                out.writeInt(-1);
                out.writeUTF(e.getMessage());
                out.flush();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
    }

    private void deleteFile(DataInputStream in, DataOutputStream out) {
        try {
            String path = in.readUTF(); // 读取要删除的文件路径
            String requester = in.readUTF(); // 读取请求者

            if (fileSystem.containsKey(path)) {
                FileInfo fileInfo = fileSystem.get(path);
                String owner = fileInfo.getOwner();
                if (owner.equals(requester)) {
                    // 删除文件或目录
                    fileSystem.remove(path);
                    out.writeInt(0); // 成功代码
                    out.writeUTF((fileInfo.isDirectory() ? "Directory" : "File") + " " + path + " deleted by " + requester);
                    log.info((fileInfo.isDirectory() ? "Directory" : "File") + " " + path + " deleted by " + requester);
                } else {
                    out.writeInt(-1); // 权限不足
                    out.writeUTF("Permission denied. You are not the owner of " + path);
                    log.info("Permission denied. You are not the owner of " + path);
                }
            } else {
                out.writeInt(-1); // 文件未找到
                out.writeUTF("File/Directory not found: " + path);
                log.info("File/Directory " + path + " not found.");
            }
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
            try {
                out.writeInt(-1);
                out.writeUTF(e.getMessage());
                out.flush();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
    }


    private void createFile(DataInputStream in, DataOutputStream out) {
        try {
            String path = in.readUTF();
            String owner = in.readUTF();
            boolean isDir = in.readBoolean();

            FileInfo fileInfo = create(path, owner, isDir);
            List<String> replicaNodes = getReplicaStorageNodes(3);
            for (String node : replicaNodes) {
                String localFileId = UUID.randomUUID().toString();
                fileInfo.getLocations().add(node + ":" + localFileId);
            }

            out.writeInt(0);
            out.writeUTF(String.join(",", fileInfo.getLocations()));
            out.flush();
        } catch (IOException e) {
            log.error("Failed to create file: " + e.getMessage());
            try {
                out.writeInt(-1);
                out.writeUTF(e.getMessage());
            } catch (IOException ex) {
                log.error("Failed to send error response: " + ex.getMessage());
            }
        }
    }

    private List<String> getReplicaStorageNodes(int replicaCount) {
        List<String> nodes = new ArrayList<>(storageNodes.keySet());
        Collections.shuffle(nodes); // 随机选择存储节点
        return nodes.subList(0, Math.min(replicaCount, nodes.size()));
    }

    // 新增：处理副本查询请求
    private void handleGetReplicas(DataInputStream in, DataOutputStream out) {
        try {
            String path = in.readUTF();
            FileInfo fileInfo = getFileInfo(path);
            if (fileInfo != null && !fileInfo.getLocations().isEmpty()) {
                out.writeInt(fileInfo.getLocations().size());
                for (String location : fileInfo.getLocations()) {
                    out.writeUTF(location);
                }
            } else {
                out.writeInt(0);
            }
            out.flush();
        } catch (IOException e) {
            log.error("Failed to handle GET_REPLICAS request: " + e.getMessage());
        }
    }


//    // 添加文件元数据
//    public void addFileMetadata(String filePath, long fileSize, String storageNode) {
//        FileInfo fileInfo = new FileInfo(filePath, fileSize);
//        fileSystem.put(filePath, fileInfo);
//        fileToStorageNode.put(filePath, storageNode);
//    }

    // 创建文件或目录
    public FileInfo create(String path, String owner, boolean isDirectory) {
        FileInfo fileInfo = null;
        if (!fileSystem.containsKey(path)) {

            String parentPath = getParentPath(path);
            if (!fileSystem.containsKey(parentPath) && !parentPath.equals("/")) {
                System.out.println("Parent directory " + parentPath + " does not exist.");
                create(parentPath,  owner, true);
            }

            if (fileSystem.containsKey(parentPath) || parentPath.equals("/")) {
                FileInfo parentInfo = fileSystem.get(parentPath);
                fileInfo = new FileInfo(path, owner, isDirectory, parentInfo);
                fileSystem.put(path, fileInfo);
                System.out.println((isDirectory ? "Directory" : "File") + " " + path + " created by " + owner);
            }
            return fileInfo;
        } else {
            System.out.println((isDirectory ? "Directory" : "File") + " " + path + " already exists.");
            fileInfo = fileSystem.get(path);
            return fileInfo;
        }
    }


    // 删除文件或目录
    public void delete(String path, String requester) {
        if (fileSystem.containsKey(path)) {
            FileInfo fileInfo = fileSystem.get(path);
            String owner = fileInfo.getOwner();
            if (owner.equals(requester)) {
                // 删除文件或目录
                fileSystem.remove(path);
                log.info((fileInfo.isDirectory() ? "Directory" : "File") + " " + path + " deleted by " + requester);
            } else {
                log.info("Permission denied. You are not the owner of " + path);
            }
        } else {
            log.info("File/Directory " + path + " not found.");
        }
    }

    // 获取文件或目录信息
    public FileInfo getFileInfo(String path) {
        FileInfo fileInfo = null;
        if (fileSystem.containsKey(path)) {
            fileInfo = fileSystem.get(path);
            System.out.print("Path: " + path);
            System.out.print( " Owner: " + fileInfo.getOwner());
            System.out.print(" Is Directory: " + fileInfo.isDirectory());
        } else {
            System.out.print(" File/Directory " + path + " not found.");
        }
        return fileInfo;
    }

    // 获取父目录路径
    private String getParentPath(String path) {
        int lastSeparatorIndex = path.lastIndexOf('/');
        if (lastSeparatorIndex == -1) {
            return "/";
        } else {
            if(lastSeparatorIndex == 0){
                return "/";
            }
            return path.substring(0, lastSeparatorIndex);
        }
    }


    // 获取文件对应的存储节点
    public String getStorageNode(String filePath) {
        return fileToStorageNode.get(filePath);
    }

    public String getNewStorageNode(long fileSize) {
        Random random = new Random();
        int id = random.nextInt() % storageNodes.size();
        String sn = (String)storageNodes.keySet().toArray()[id];
        return sn;
    }

    public void setStorageNode(List<StorageNode> storageNodes) {
        for(StorageNode sn : storageNodes)
            this.storageNodes.put(sn.getName(),sn);
    }


    private void listFile(DataInputStream in, DataOutputStream out) {
        try {
            List<String> fileList = new ArrayList<String>();
            String cur_dir = in.readUTF();
            FileInfo fileInfo = getFileInfo(cur_dir);
            if(fileInfo != null && fileInfo.getChildren() != null){
                for(FileInfo ch:fileInfo.getChildren()){
                    fileList.add(ch.getFileName());
                }
            }
            int size = fileList.size();
            out.writeInt(size);
            System.out.println(" size:" + size);
            if(size > 0) {
                for (String name : fileList) {
                    out.writeUTF(name);
                }
                out.flush();
            }
            System.out.print("end of listfile.");
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void handleHeartBeat(DataInputStream in, DataOutputStream out) {
        try {
            String nodeName = in.readUTF(); // 数据服务器节点名称
            System.out.println(new Date().toString() + " received heartBeat from DataServer: " + nodeName);

            Long now = System.currentTimeMillis();
            if (storageNodes.containsKey(nodeName)) {
                storageNodesUpTime.put(nodeName, now); // 更新心跳时间
            } else {
                System.out.println("New DataServer registered: " + nodeName);
                storageNodes.put(nodeName, new StorageNode(nodeName, "dynamic-ip", Config.DATA_SERVRE_PORT));
                storageNodesUpTime.put(nodeName, now);
            }

            // 发送 ACK 确认信息
            out.writeInt(0); // 成功代码
            out.writeUTF("ACK from MetaServer for " + nodeName);
            out.flush();
        } catch (IOException e) {
            log.error("Failed to handle heartBeat: " + e.getMessage());
        }
    }


    public static void main(String[] args) {
        MetadataServer metaServer = new MetadataServer();
        metaServer.serve();
    }


}

