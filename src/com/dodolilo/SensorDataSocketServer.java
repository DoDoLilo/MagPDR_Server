package com.dodolilo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

/**
 * TODO 单例模式？
 * 一个socketServer可开启多个Socket，每个Socket需要开一个线程接受数据，
 * 当然此次项目只需要一个服务器开一个socket连接。
 * 使用Socket长连接进行数据接收。
 */
public class SensorDataSocketServer {
    //当某个socket连接超过TIME_OUT （ms）未发送新数据时，服务器主动关闭该连接
    private static final int READ_TIME_OUT = 10000;
    //连接监听线程监听频率（ms）
    private static final long LISTEN_TIME = 1000;

    private int port;

    private ServerSocket server;

    private boolean serverAlive;

    /**
     * 目前一个socket服务器只允许连接一个手机，而一个SensorDataSocketServer实例认为就是一个socket服务器.
     * 为什么？因为如果一个socket服务器支持连接多个手机，则还需要多个MagPDR实例开启线程、多个公用sensorData数据池，
     * 太麻烦了，目前不要考虑实现这么麻烦的功能。
     */
    private Socket singleSocket;

    // TODO: 2022/9/9 数据存储变量sensorData，涉及多线程r/w，需要同步机制 or 使用concurrnet数据类?
    private StringBuilder sensorData;

    public SensorDataSocketServer() {
    }

    /**
     * 有参构造函数.
     * @param port 服务器socket端口
     * @param sensorData DI依赖注入，既然本类中的sensorData要供外部数据读取，那直接由外部提供该域引用
     */
    public SensorDataSocketServer(int port, StringBuilder sensorData) {
        try {
            this.server = new ServerSocket(port);
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.port = port;
        this.sensorData = sensorData;
        serverAlive = false;
    }

    /**
     * 开始服务器监听线程（监听频率1s）.
     * 本服务器类目前只支持连接一个socket，只有唯一的socket无连接时，才进行连接.
     */
    public void startListening() {
        serverAlive = true;
        //该线程由主线程开启，这里Runnable lambda表达式是一个匿名内部类，所以可以访问外部类SensorDataSocketServer的域
        new Thread(() -> {
            System.out.println("服务器开始监听socket请求，Port " + port + "...");
            while (serverAlive) {
                if (singleSocket == null || singleSocket.isClosed()) {
                    try {
                        singleSocket = server.accept();
                        System.out.println("建立socket连接...");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    //成功建立socket连接，使用该连接开启数据接收线程
                    new Thread(reciveSensorData).start();
                }

                try {
                    Thread.sleep(LISTEN_TIME);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    //结束服务器监听线程.关闭现有连接、关闭服务
    public void stopListening() {
        if (serverAlive) {
            //先关闭现有的socket连接：该socket曾经连接成功过 && 还没被关闭过
            if (singleSocket != null && singleSocket.isConnected() && !singleSocket.isClosed()) {
                try {
                    singleSocket.close();
                    System.out.println("断开socket连接...");
                } catch (IOException e) {
                    System.out.println("socket连接close失败！");
                    e.printStackTrace();
                }
            }
            //再关闭整个服务器监听
            serverAlive = false;
            try {
                server.close();
            } catch (IOException e) {
                System.out.println("server close失败！");
                e.printStackTrace();
            }
        }
    }

    /**
     * 传感器数据接收线程执行方法.
     * 客户端发送的数据格式为字符串，csv格式，每行以'\n'间隔，每列以','间隔。
     * 当socket相关Read操作阻塞超过 10s，则断开连接。
     * 逻辑保证进入该方法时singleSocket必已建立连接
     */
    private Runnable reciveSensorData = () -> {
        //由singleSocket读取字节流，转换为String，
        try (BufferedReader bufferedReader = new BufferedReader(
                new InputStreamReader(singleSocket.getInputStream())
        )) {
            //读取字节流数据，超过TIME_OUT没有新数据 or 收到的数据是"END\n"，退出循环
            singleSocket.setSoTimeout(READ_TIME_OUT);
            while (true) {
                String msg = bufferedReader.readLine();
                if (msg.equals("END")) {
                    System.out.println("发送端结束");
                    break;
                } else {
                    System.out.println(msg);
                    sensorData.append(msg);
                    sensorData.append('\n');
                }
            }
        } catch (SocketException ioE0) {
            System.out.println("长时间无新数据关闭连接 or 客户端主动断开连接...");
            ioE0.printStackTrace();
        } catch (IOException ioE1) {
            System.out.println("数据接收异常，断开socket连接...");
            ioE1.printStackTrace();
        } finally {
            //主动关闭该连接
            try {
                System.out.println("关闭当前socket连接...");
                singleSocket.close();
            } catch (IOException ioE2) {
                System.out.println("socket连接close失败！");
                ioE2.printStackTrace();
            }
        }
    };



}
