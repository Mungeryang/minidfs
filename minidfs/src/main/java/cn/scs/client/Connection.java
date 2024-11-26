package cn.scs.client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class Connection {

    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;
    private String host;
    private int port;

    public Connection(String host, int port) throws IOException {
        this.host = host;
        this.port = port;
        socket = new Socket(host, port);

        // 获取输入流和输出流
        in = new DataInputStream(socket.getInputStream());
        out = new DataOutputStream(socket.getOutputStream());
    }

    public DataInputStream getIn() {
        return in;
    }

    public DataOutputStream getOut() {
        return out;
    }

    public void writeUTF(String str) throws IOException {
        out.writeUTF(str);
    }

    public void write(byte[] data) throws IOException {
        out.write(data);
    }

    public int readInt() throws IOException {
        return in.readInt();
    }

    public long readLong() throws IOException {
        return in.readLong();
    }

    public boolean readBoolean() throws IOException {
        return in.readBoolean();
    }

    public void writeBoolean(boolean b) throws IOException {
        out.writeBoolean(b);
    }

    public String readUTF() throws IOException {
        return in.readUTF();
    }

    public void writeLong(long value) throws IOException {
        out.writeLong(value); // 添加 writeLong 方法
    }

    public void writeInt(int value) throws IOException {
        out.writeInt(value); // 添加 writeInt 方法
    }

    public void close() throws IOException {
        socket.close();
    }

    public void flush() throws IOException {
        out.flush();
    }
}