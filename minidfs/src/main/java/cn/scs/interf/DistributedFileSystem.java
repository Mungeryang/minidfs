package cn.scs.interf;

import cn.scs.common.FileInfo;

import java.io.DataInputStream;
import java.util.List;

public interface DistributedFileSystem {

    // 客户端上传文件
    boolean uploadFile(String localFilePath, String remoteFilePath);

    // 客户端下载文件
    boolean downloadFile(String remoteFilePath, String localFilePath);

    // 客户端删除文件
    boolean deleteFile(String remoteFilePath);

    // 列出指定目录下的文件和子目录
    List<String> listFiles(String remoteDirectoryPath);

    // 创建目录
    boolean createDirectory(String remoteDirectoryPath);

    // 删除目录（如果目录不为空，则可能需要递归删除）
    boolean deleteDirectory(String remoteDirectoryPath);

    // 获取文件信息（如大小、创建时间等）
    FileInfo getFileInfo(String remoteFilePath);

    // 复制文件
    boolean copyFile(String sourcePath, String destinationPath);

    // 移动文件
    boolean moveFile(String sourcePath, String destinationPath);

    // 获取存储节点信息
    List<String> getStorageNodes();

    // 打开文件
    DataInputStream openFile(String remoteFilePath);

    // 关闭文件
    boolean closeFile(String remoteFilePath);

    // 写入文件
    boolean writeFile(String remoteFilePath, byte[] data);

    // 读取文件
    byte[] readFile(String remoteFilePath, long offset, int length);

    // 获取文件大小
    long getFileSize(String remoteFilePath);

    // 关闭文件系统连接
    void close();
}
