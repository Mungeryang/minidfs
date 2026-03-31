# <p align="center"> miniDFS </p>

## 中国科学院大学网络空间安全学院 大数据技术课程 节课大作业

<img align="center" src="https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTD0LpkXqKO5WIQfWEvAb3nGwrxS_AWAyU20g&s">

<p align="center"> ![ucas-logo](https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTD0LpkXqKO5WIQfWEvAb3nGwrxS_AWAyU20g&s) </p>

![ucas-logo](https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTD0LpkXqKO5WIQfWEvAb3nGwrxS_AWAyU20g&s)

### 授课教师：中国科学院信息工程研究所 - 黄晶老师、钟进文老师

### 代码编写：中国科学院大学网络空间安全学院 - 杨桂淼、程邯畅

### 日期：2024.12.12

> 模拟实现分布式文件系统 - Mini Distributed File System

> 设计一个简单的分布式文件系统,包含文件元数据管理和数据块存储。

要求:

1. 设计文件系统命名空间,支持目录树。

2. 实现一个元数据服务,负责文件名到数据块映射。

3. 数据和元数据服务可以部署在不同节点。

4. 提供读写接口访问文件系统。（以上为基本要求）

5. 实现数据块存储服务,管理数据块的存储。

6. 支持多副本,磁盘或者节点失败后读取另外副本。

7. 支持主节点日志持久化与容错,主节点失败后恢复。

步骤:

1. 定义文件系统命名空间,文件元数据结构。

2. 设计元数据服务,存储文件名到数据块映射关系。

3. 设计数据服务,进行数据块的存储和访问。

4. 使用RPC或REST API实现两个服务之间通信。

5. 提供文件系统抽象接口,封装元数据和数据操作逻辑。

6. 实现容错逻辑,如元数据副本,数据修复等。

7. 编写示例客户端代码访问文件系统。

8. 测试文件读取写全流程,模拟节点故障场景。

## Contact Us: mungerygm@gmail.com




