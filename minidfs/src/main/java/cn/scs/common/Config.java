package cn.scs.common;

public class Config {
    //主节点的静态ip
    //public static String META_SERVRE_HOST = "192.168.88.100";
    public static String META_SERVRE_HOST = "127.0.0.1";
    public static int DATA_SERVRE_PORT = 9526;//HDFS端口号
    public static int META_SERVRE_PORT = 10001;//Hive的thrift端口号
    public static int TIMEOUT_OF_HEARTBEATS = 20;
    public static int HEARTBEAT_SECS = 5;

    public static String USER = "root";

    public static final String STORAGE_PATH = "/root/dfs"; //添加存储路径
}
