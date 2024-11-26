package cn.scs.client;

import cn.scs.common.Config;
import cn.scs.common.DataOpCode;
import cn.scs.common.FileInfo;
import cn.scs.common.MetaOpCode;
import cn.scs.impl.DataServer;
import cn.scs.impl.StorageNode;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import sun.tools.jar.CommandLine;

import java.io.*;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

public class DistributedFileSystemClient {
    private static Logger log = LogManager.getLogger(DataServer.class);

    private MetaServerClient metaDataClient;
    private List<StorageNode> storageNodes;
    private String cur_dir = "/";

    public DistributedFileSystemClient() throws IOException {
        metaDataClient = new MetaServerClient();
        storageNodes = new ArrayList();
        // 初始化存储节点列表
        storageNodes.add(new StorageNode("datanode1","192.168.88.101",9526));
        storageNodes.add(new StorageNode("datanode2","192.168.88.102",9526));
        // ...
    }


    public void disconnect() throws IOException {
        // 客户端连接关闭操作
        metaDataClient.close();
        //与存储节点断开连接
        disconnectFromStorageNodes();
        log.info("客户端已经成功断开连接！");
    }

    public StorageNode selectStorageNode(String fileId) {
        FileInfo fileInfo = metaDataClient.getFileInfo(fileId);
        if (fileInfo == null || fileInfo.getLocations().isEmpty()) {
            throw new RuntimeException("No available replicas found for file ID: " + fileId);
        }
        for (String location : fileInfo.getLocations()) {
            for (StorageNode node : storageNodes) {
                if (location.contains(node.getHost())) {
                    return node;
                }
            }
        }
        throw new RuntimeException("No suitable storage node found for file ID: " + fileId);
    }

    public DataInputStream openFile(String path) {
        try {
            String fileId = metaDataClient.getFileInfo(path).getFileId();
            if (fileId == null) {
                log.error("打开文件失败，文件路径未找到: " + path);
                return null;
            }

            StorageNode targetNode = selectStorageNode(fileId);
            Connection connection = new Connection(targetNode.getHost(), targetNode.getPort());
            DataOpCode.READ_FILE.write(connection.getOut()); // 发送读取文件操作码
            connection.writeUTF(fileId);
            connection.flush();

            int retCode = connection.readInt();
            if (retCode == 0) {
                return connection.getIn();
            } else {
                log.error("文件打开失败: " + connection.readUTF());
                connection.close();
            }
        } catch (IOException e) {
            log.error("打开文件时发生IO异常: ", e);
        }
        return null;
    }

    public byte[] readFile(String path, long offset, int len) {
        List<String> replicas = metaDataClient.getReplicas(path);
        for (String replica : replicas) {
            try {
                String[] parts = replica.split(":");
                String host = parts[0];
                int port = Integer.parseInt(parts[1]);

                Connection connection = new Connection(host, port);
                DataOpCode.READ_FILE.write(connection.getOut()); // 发送读取操作码
                connection.writeUTF(path);
                connection.writeLong(offset);
                connection.flush();

                // 读取数据
                byte[] buffer = new byte[len];
                connection.getIn().readFully(buffer);
                connection.close();
                return buffer;
            } catch (IOException e) {
                log.error("从副本读取失败: " + replica, e);
            }
        }
        return null;
    }


    /**
     * 将data数据写至分布式文件系统路径path
     * @param path
     * @param data
     * @return
     */
    public boolean writeFile(String path, byte[] data) {
        try {
            // 向元数据服务器发送新建文件请求，返回 fileId
            String fileId = metaDataClient.createFile(path);
            if (fileId == null) {
                log.error("创建文件失败: " + path);
                return false;
            }

            // 选择存储节点
            StorageNode targetNode = selectStorageNode(fileId);
            Connection connection = new Connection(targetNode.getHost(), targetNode.getPort());

            // 发送写文件请求
            DataOpCode.WRITE_FILE.write(connection.getOut());
            connection.writeUTF(fileId);
            connection.write(data);
            connection.flush();

            // 检查服务器返回的响应
            int retCode = connection.readInt();
            String msg = connection.readUTF();
            if (retCode == 0) {
                log.info("写入成功: " + msg);
                return true;
            } else {
                log.error("写入失败: " + msg);
            }
            connection.close();
        } catch (IOException e) {
            log.error("写入文件时发生IO异常: ", e);
        }
        return false;
    }

    public boolean closeFile(String path) {
        try {
            String fileId = metaDataClient.getFileInfo(path).getFileId();
            if (fileId == null) {
                log.error("关闭文件失败，未找到元数据: " + path);
                return false;
            }

            StorageNode targetNode = selectStorageNode(fileId);
            Connection connection = new Connection(targetNode.getHost(), targetNode.getPort());
            DataOpCode.CLOSE_FILE.write(connection.getOut());
            connection.writeUTF(fileId);
            connection.flush();

            int retCode = connection.readInt();
            if (retCode == 0) {
                log.info("文件关闭成功: " + path);
                return true;
            } else {
                log.error("文件关闭失败: " + connection.readUTF());
            }
            connection.close();
        } catch (IOException e) {
            log.error("关闭文件时发生IO异常: ", e);
        }
        return false;
    }

    private void listFiles(String path) {
        List<String> fileList = metaDataClient.listFiles(path); // 假设有这个方法来获取文件列表

        if (fileList.isEmpty()) {
            System.out.println("No files found.");
        } else {
            System.out.println("Files in the system:");
            for (String file : fileList) {
                System.out.println(file);
            }
        }
    }

    // 获取文件大小
    private long getFileSize(String filePath) {
        // 实现获取文件大小的逻辑
        FileInfo fileInfo = getFileInfo(filePath);
        if (fileInfo != null) {
            return fileInfo.getFileSize();
        }
        return 0;
    }


    public boolean downloadFile(String remoteFilePath, String localFilePath) {
        // 实现下载文件逻辑
        byte[] fileData = readFile(remoteFilePath, 0, (int) getFileSize(remoteFilePath));
        if (fileData == null) {
            log.error("下载文件失败，未能读取文件内容: " + remoteFilePath);
            return false;
        }

        try (FileOutputStream fos = new FileOutputStream(localFilePath)) {
            fos.write(fileData);
            log.info("文件下载成功: " + localFilePath);
            return true;
        } catch (IOException e) {
            log.error("写入本地文件时发生IO异常: " + localFilePath, e);
        }
        return false;
    }


    public boolean deleteFile(String remoteFilePath) {
        // 实现删除文件逻辑
        try {
            MetaOpCode.DEL_FILE.write(metaDataClient.getConnection().getOut());
            metaDataClient.getConnection().writeUTF(remoteFilePath);
            metaDataClient.getConnection().flush();

            int retCode = metaDataClient.getConnection().readInt();
            if(retCode == 0){
                log.info("文件删除成功！"+remoteFilePath);
                return true;
            }else {
                log.error("文件删除失败！" + metaDataClient.getConnection().readUTF());
            }
        } catch (Exception e){
            log.error("文件删除时发生IO异常" + remoteFilePath,e);
        }
        return false;
    }


    public boolean createDirectory(String remoteDirectoryPath) {
        // 实现创建目录逻辑
        try {
            MetaOpCode.CREATE_FILE.write(metaDataClient.getConnection().getOut());
            metaDataClient.getConnection().writeUTF(remoteDirectoryPath);
            metaDataClient.getConnection().writeUTF(Config.USER);
            metaDataClient.getConnection().writeBoolean(true);

            int retCode = metaDataClient.getConnection().readInt();
            String msg = metaDataClient.getConnection().readUTF();
            if(retCode == 0){
                log.info("创建目录成功！" + msg);
                return true;
            }else {
                log.error("目录创建失败：" + msg);
            }
        }catch (Exception e){
            log.error("创建目录时发生IO异常: " + remoteDirectoryPath,e);
        }
        return false;
    }

    public boolean deleteDirectory(String remoteDirectoryPath) {
        // 实现删除目录逻辑
        try {
            MetaOpCode.DEL_FILE.write(metaDataClient.getConnection().getOut());
            metaDataClient.getConnection().writeUTF(remoteDirectoryPath);
            metaDataClient.getConnection().flush();

            int retCode = metaDataClient.getConnection().readInt();
            if(retCode == 0){
                log.info("目录删除成功: " + remoteDirectoryPath);
                return true;
            }else {
                log.error("目录删除失败: " + metaDataClient.getConnection().readUTF());
            }
        } catch (Exception e){
                log.error("删除目录时发生IO异常: " + remoteDirectoryPath, e);
        }
        return false;
    }

    public FileInfo getFileInfo(String remoteFilePath) {
        // 实现获取文件信息逻辑
        try {
            MetaOpCode.LIST_FILE.write(metaDataClient.getConnection().getOut());
            metaDataClient.getConnection().writeUTF(remoteFilePath);
            metaDataClient.getConnection().flush();

            int retCode = metaDataClient.getConnection().readInt();
            if(retCode == 0){
                String fileInfoStr = metaDataClient.getConnection().readUTF();
                FileInfo fileInfo = FileInfo.parse(fileInfoStr);
                return fileInfo;
            }
            else {
                log.error("获取文件信息失败: " + metaDataClient.getConnection().readUTF());
            }
        }catch (Exception e){
            log.error("获取文件信息时发生IO异常: " + remoteFilePath, e);
        }
        return null;
    }
    public boolean copyFile(String sourcePath, String destinationPath) {
        // 实现复制文件逻辑
        try {
            byte[] fileData = readFile(sourcePath,0,(int)getFileSize(sourcePath));
            if(fileData == null){
                log.error("复制文件失败，未能读取到相关内容：" + sourcePath);
                return false;
            }
            return writeFile(destinationPath,fileData);
        } catch (Exception e){
            log.error("复制文件时发生异常: " + sourcePath + " 到 " + destinationPath, e);
        }
        return false;
    }
   public boolean moveFile(String sourcePath, String destinationPath) {
        // 实现移动文件逻辑
       if (copyFile(sourcePath, destinationPath)) {
           return deleteFile(sourcePath);
       }
       return false;
    }


    // 与存储节点断开连接
    private void disconnectFromStorageNodes() {
        // 实现断开连接逻辑
        // 遍历所有存储节点，断开连接
        for (StorageNode node : storageNodes) {
            try {
                node.disconnect();
            } catch (IOException e) {
                log.error("断开存储节点连接时发生异常: " + node.getHost(), e);
            }
        }
    }

    // 其他辅助方法的实现...

    //获取副本
    public List<String> getReplicas(String filePath) {
        try {
            metaDataClient.getConnection().writeUTF("GET_REPLICAS");
            metaDataClient.getConnection().writeUTF(filePath);
            metaDataClient.getConnection().flush();

            int count = metaDataClient.getConnection().readInt();
            List<String> replicas = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                replicas.add(metaDataClient.getConnection().readUTF());
            }
            return replicas;
        } catch (IOException e) {
            log.error("Failed to get replicas: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    public static void main(String[] args) throws IOException {
        DistributedFileSystemClient client = new DistributedFileSystemClient();

        Scanner scanner = new Scanner(System.in);

        boolean running = true;
        System.out.println("┏===================================================┓");
        System.out.println("|++++++++++++++++++++Mini-DFS+++++++++++++++++++++++|");
        System.out.println("|    ^_^Welcome to our Distributed-File-System^_^   |");
        System.out.println("|                       ●v0.1●                      |");
        System.out.println("|              2024.11.24@Big-data-tech             |");
        System.out.println("|+++++++++++++++++++++++++++++++++++++++++++++++++++|");
        System.out.println("┗===================================================┛");
        System.out.println();
        while (running) {
            System.out.println("操作列表：(open, cd, read, write, close, ls, cat, copy, move, download, delete, mkdir, rmdir,exit):");
            String command = scanner.nextLine();

            switch (command.toLowerCase()) {
                case "open":
                    System.out.println("Enter file path:");
                    String path = scanner.nextLine();
                    DataInputStream fileStream = client.openFile(path);
                    if (fileStream != null) {
                        System.out.println("File opened.");
                    } else {
                        System.out.println("Failed to open file.");
                    }
                    break;
                case "read":
                    // 实现从文件读取数据的逻辑
                    System.out.println("Enter file path:");
                    String readPath = scanner.nextLine();
                    System.out.println("Enter offset:");
                    long offset = Long.parseLong(scanner.nextLine());
                    System.out.println("Enter length:");
                    int len = Integer.parseInt(scanner.nextLine());
                    byte[] data = client.readFile(readPath,offset,len);
                    if(data != null){
                        System.out.println("Data read" + new String(data));
                    }else {
                        System.out.println("Failed to read file...");
                    }
                    break;
                case "write":
                    // 实现向文件写入数据的逻辑
                    System.out.println("Enter file path:");
                    String writePath = scanner.nextLine();
                    System.out.println("Enter data to write:");
                    String inputData = scanner.nextLine();
                    if(client.writeFile(writePath,inputData.getBytes())){
                        System.out.println("Data written successfully.");
                    } else {
                        System.out.println("Failed to write data.");
                    }
                    break;
                case "cd":
                    System.out.println("Enter path:");
                    String relPath = scanner.nextLine().trim();
                    if (relPath.startsWith("/")) {
                        client.cur_dir = relPath;
                    } else {
                        client.cur_dir = client.cur_dir + (client.cur_dir.endsWith("/") ? "" : "/") + relPath;
                    }
                    System.out.println("Current directory: " + client.cur_dir);
                    break;

                case "ls":
                    client.listFiles(client.cur_dir);
                    break;
                case "cat":
                    //获取文件内容逻辑
                    System.out.println("Enter file path:");
                    String f = scanner.nextLine();
                    byte[] content = client.readFile(f,0,0);
                    //System.out.println(new String(content));
                    if (content != null) {
                        System.out.println(new String(content));
                    } else {
                        System.out.println("Failed to read file.");
                    }
                    break;
                case "download":
                    //下载文件的逻辑
                    System.out.println("Enter remote file path:");
                    String remotePath = scanner.nextLine();
                    System.out.println("Enter local file path:");
                    String localPath = scanner.nextLine();
                    if (client.downloadFile(remotePath, localPath)) {
                        System.out.println("File downloaded successfully.");
                    } else {
                        System.out.println("Failed to download file.");
                    }
                    break;

                case "delete":
                    //删除文件逻辑
                    System.out.println("Enter file path to delete:");
                    String deletePath = scanner.nextLine();
                    if (client.deleteFile(deletePath)) {
                        System.out.println("File deleted successfully.");
                    } else {
                        System.out.println("Failed to delete file.");
                    }
                    break;
                case "mkdir":
                    // 实现创建文件夹的逻辑
                    System.out.println("Enter directory path to create:");
                    String mkdirPath = scanner.nextLine();
                    if(client.createDirectory(mkdirPath)){
                        System.out.println("Directory created successfully.");
                    }else {
                        System.out.println("Failed to create directory.");
                    }
                    break;
                case "rmdir":
                    // 删除文件夹的逻辑
                    System.out.println("Enter directory path to delete:");
                    String rmdirPath = scanner.nextLine();
                    if(client.deleteDirectory(rmdirPath)){
                        System.out.println("Directory deleted successfully.");
                    } else {
                        System.out.println("Failed to delete directory.");
                    }
                    break;
                case "copy":
                    // 实现复制文件的逻辑
                    System.out.println("Enter source file path:");
                    //源路径
                    String sourcePath = scanner.nextLine();
                    System.out.println("Enter destination file path:");
                    //目标路径
                    String destPath = scanner.nextLine();
                    if(client.copyFile(sourcePath,destPath)){
                        System.out.println("File copied successfully.");
                    }else {
                        System.out.println("Failed to copy file.");
                    }
                    break;
                case "mv":
                    // 实现剪切文件的逻辑
                    System.out.println("Enter source file path:");
                    String moveSourcePath = scanner.nextLine();
                    System.out.println("Enter destination file path:");
                    String moveDestPath = scanner.nextLine();
                    if(client.moveFile(moveSourcePath,moveDestPath)){
                        System.out.println("File moved successfully.");
                    }else {
                        System.out.println("Failed to move file.");
                    }
                    break;
                case "close":
                    // 实现关闭文件的逻辑
                    System.out.println("Enter file path to close:");
                    String closePath = scanner.nextLine();
                    if(client.closeFile(closePath)){
                        System.out.println("File closed successfully.");
                    }else {
                        System.out.println("Failed to close file.");
                    }
                    break;

                case "exit":
                    System.out.println("拜拜~");
                    running = false;
                    break;

                default:
                    System.out.println("Invalid command.");
                    break;
            }
        }

        client.disconnect();
        scanner.close();
    }
}

