package cn.scs.impl;

import cn.scs.client.Connection;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class StorageNode {

    private String name; // 存储节点的名称
    private String host; // 存储节点的主机名/IP
    private int port; // 存储节点的端口
    private Connection connection;
    private Map<String, byte[]> fileStorage; // 文件存储，文件路径到文件数据的映射

    // 修改构造函数，添加 name, host 和 port 参数
    public StorageNode(String name, String host, int port) {
        this.name = name; // 初始化存储节点名称
        this.host = host; // 初始化主机地址
        this.port = port; // 初始化端口
        fileStorage = new HashMap<>();
    }

    // 上传文件到存储节点
    public boolean uploadFile(String localFilePath, String remoteFilePath) {
        byte[] fileData = readFromFile(localFilePath);
        if (fileData != null) {
            fileStorage.put(remoteFilePath, fileData);
            return true;
        }
        return false;
    }

    // 下载文件数据
    public byte[] downloadFile(String remoteFilePath) {
        return fileStorage.get(remoteFilePath);
    }

    // 删除文件
    public boolean deleteFile(String remoteFilePath) {
        if (fileStorage.containsKey(remoteFilePath)) {
            fileStorage.remove(remoteFilePath);
            return true;
        }
        return false;
    }

    // 从本地读取文件数据
    private byte[] readFromFile(String localFilePath) {
        File file = new File(localFilePath);
        if (!file.exists()) {
            System.out.println("File not found: " + localFilePath);
            return null;
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            return data;
        } catch (IOException e) {
            System.out.println("Error reading file: " + e.getMessage());
        }
        return null;
    }

    // 添加 getName 方法
    public String getName() {
        return name;
    }

    // 添加 getHost 方法
    public String getHost() {
        return host;
    }

    // 添加 getPort 方法
    public int getPort() {
        return port;
    }

    // 断开与存储节点的连接
    public void disconnect() throws IOException {
        if (connection != null) {
            connection.close(); // 关闭网络连接
            System.out.println("Disconnected from storage node: " + name + " at " + host + ":" + port);
        }
    }
}