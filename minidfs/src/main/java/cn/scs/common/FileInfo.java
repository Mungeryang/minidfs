package cn.scs.common;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FileInfo {

    private String fileName;
    private long fileSize;
    private long creationTime;
    private String path;
    private String owner;
    private String group;
    private boolean isDirectory;
    private FileInfo parent;
    private List<FileInfo> children = new ArrayList(); // 子节点
    private List<String> locations = new ArrayList(); // 存储位置
    private List<Integer> status = new ArrayList();  // 副本状态
    private String fileId; // 文件的唯一标识符

    // 修改后的构造函数，包含 fileId 初始化
    public FileInfo(String fileName, String path, boolean isDirectory, long fileSize, String owner, long creationTime) {
        this.fileName = fileName;
        this.path = path;
        this.isDirectory = isDirectory;
        this.fileSize = fileSize;
        this.owner = owner;
        this.creationTime = creationTime;
        this.fileId = UUID.randomUUID().toString(); // 初始化 fileId
    }

    public FileInfo(String path, String owner, boolean isDirectory, FileInfo parentInfo) {
        this.path = path;
        this.owner = owner;
        this.isDirectory = isDirectory;
        this.fileName = getFileName(path);
        this.parent = parentInfo;
        this.fileSize = -1;
        this.creationTime = System.currentTimeMillis();
        this.fileId = UUID.randomUUID().toString(); // 初始化 fileId
        this.parent.getChildren().add(this);
    }


    // 静态方法：解析字符串为 FileInfo 对象
    public static FileInfo parse(String fileInfostr){
        String[] parts = fileInfostr.split(",");
        if (parts.length < 5) {
            throw new IllegalArgumentException("Invalid file info string: " + fileInfostr);
        }
        String fileName = parts[0].trim();
        String path = parts[1].trim();
        boolean isDirectory = Boolean.parseBoolean(parts[2].trim());
        long fileSize = Long.parseLong(parts[3].trim());
        String owner = parts[4].trim();
        long creationTime = Long.parseLong(parts[5].trim());
        FileInfo fileInfo = new FileInfo(fileName,path,isDirectory,fileSize,owner,creationTime);
        return fileInfo;
    }
    // 获取父目录路径
    private String getFileName(String path) {
        path = path.trim();
        int lastSeparatorIndex = path.lastIndexOf('/');
        if (lastSeparatorIndex == path.length() - 1) {
            path = path.substring(0, path.length() - 1);
        }
        if (lastSeparatorIndex == -1) {
            return "/";
        } else {
            return path.substring(lastSeparatorIndex + 1);
        }
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(long creationTime) {
        this.creationTime = creationTime;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    public void setDirectory(boolean directory) {
        isDirectory = directory;
    }

    public FileInfo getParent() {
        return parent;
    }

    public void setParent(FileInfo parent) {
        this.parent = parent;
    }

    public List<FileInfo> getChildren() {
        return children;
    }

    public void setChildren(List<FileInfo> children) {
        this.children = children;
    }

    public List<String> getLocations() {
        return locations;
    }

    public void setLocations(List<String> locations) {
        this.locations = locations;
    }

    public List<Integer> getStatus() {
        return status;
    }

    public void setStatus(List<Integer> status) {
        this.status = status;
    }

    // 添加 getFileId 方法
    public String getFileId() {
        return fileId;
    }

    // 添加 setFileId 方法
    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    @Override
    public String toString() {
        return "FileInfo{" +
                "fileName='" + fileName + '\'' +
                ", fileSize=" + fileSize +
                ", creationTime=" + creationTime +
                ", path='" + path + '\'' +
                ", owner='" + owner + '\'' +
                ", group='" + group + '\'' +
                ", isDirectory=" + isDirectory +
                ", parent=" + parent +
                '}';
    }
}
