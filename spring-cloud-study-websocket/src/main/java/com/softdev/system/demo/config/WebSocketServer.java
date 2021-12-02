package com.softdev.system.demo.config;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.springframework.stereotype.Component;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;


/**
 * WebSocketServer
 *
 * @author zhengkai.blog.csdn.net
 */
@ServerEndpoint("/imserver/{userId}")   // 每个页面端上来的时候，每个用户通过 userId 建立一个 websocket 连接客户端，然后通过 userId 作区分，发送给对应的客户端
@Component
public class WebSocketServer {

    private static final Log log = LogFactory.get(WebSocketServer.class);

    /**
     * 静态变量，用来记录当前在线连接数。应该把它设计成线程安全的。
     */
    private static int onlineCount = 0;

    /**
     * concurrent包的线程安全Set，用来存放每个客户端对应的MyWebSocket对象。
     * 单机版本使用 map，集群使用其他容器，参考 rrpc，本地 map 容器先判断有没有，本地没有通过 redis跨机器回调
     */
    private static final ConcurrentHashMap<String, WebSocketServer> SOCKET_CONTAINER = new ConcurrentHashMap<>();

    /**
     * 与某个客户端的连接会话，需要通过它来给客户端发送数据
     */
    private Session session;

    /**
     * 接收userId
     */
    private String userId = "";

    /**
     * 连接建立成功调用的方法
     * 页面端打开某个页面，点击开启 socket，然后就连接 socket 服务器，程序执行就到这里
     * 项目的根路径 + @ServerEndpoint("/imserver/{userId}") -> http://localhost:9999/demo/imserver/{userId}
     * 这个每个用户就建立了一个独立的 socket 连接
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("userId") String userId) {
        this.session = session;
        this.userId = userId;
        if (SOCKET_CONTAINER.containsKey(userId)) {
            SOCKET_CONTAINER.remove(userId);
            SOCKET_CONTAINER.put(userId, this);
            //加入set中
        } else {
            SOCKET_CONTAINER.put(userId, this);
            //加入set中
            addOnlineCount();
            //在线数加1
        }

        log.info("用户连接:" + userId + ",当前在线人数为:" + getOnlineCount());

        try {
            sendMessage("连接成功");   // 连接上来的时候，向 socket 发送提示 conn suc
        } catch (IOException e) {
            log.error("用户:" + userId + ",网络异常!!!!!!");
        }
    }

    /**
     * 连接关闭调用的方法
     * 页面关闭程序就执行到这里
     */
    @OnClose
    public void onClose() {
        if (SOCKET_CONTAINER.containsKey(userId)) {
            SOCKET_CONTAINER.remove(userId);
            //从set中删除
            subOnlineCount();
        }
        log.info("用户退出:" + userId + ",当前在线人数为:" + getOnlineCount());
    }

    /**
     * 收到客户端消息后调用的方法
     *
     * @param message 客户端发送过来的消息
     */
    @OnMessage
    public void onMessage(String message, Session session) {
        log.info("用户消息:" + userId + ",报文:" + message);
        //可以群发消息
        //消息保存到数据库、redis
        if (StrUtil.isNotBlank(message)) {
            try {
                //解析发送的报文
                JSONObject jsonObject = JSON.parseObject(message);
                //追加发送人(防止串改)
                jsonObject.put("fromUserId", this.userId);
                String toUserId = jsonObject.getString("toUserId");
                //传送给对应toUserId用户的websocket
                if (StrUtil.isNotBlank(toUserId) && SOCKET_CONTAINER.containsKey(toUserId)) {
                    SOCKET_CONTAINER.get(toUserId).sendMessage(jsonObject.toJSONString());
                } else {
                    log.error("请求的userId:" + toUserId + "不在该服务器上");   //否则不在这个服务器上，发送到mysql或者redis
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * @param session
     * @param error
     */
    @OnError
    public void onError(Session session, Throwable error) {
        log.error("用户错误:" + this.userId + ",原因:" + error.getMessage());
        error.printStackTrace();
    }

    /**
     * 实现服务器主动推送
     */
    public void sendMessage(String message) throws IOException {
        this.session.getBasicRemote().sendText(message);
    }


    /**
     * 实现服务器主动推送
     * try{} catch{}
     */
    public void sendMessage2(String message) {
        try {
            this.session.getBasicRemote().sendText(message);
        } catch (IOException e) {
            log.error(String.format("send error content:%s", message));
            log.error(e);
        }
    }

    /**
     * 实现服务器主动推送
     * obj 发送
     */
    public void sendObjMessage(Object object) throws IOException, EncodeException {
        this.session.getBasicRemote().sendObject(object);
    }

    /**
     * 发送自定义消息
     */
    public static void sendInfo(String message, @PathParam("userId") String userId) throws IOException {
        log.info("发送消息到:" + userId + "，报文:" + message);
        if (StrUtil.isNotBlank(userId) && SOCKET_CONTAINER.containsKey(userId)) {
            SOCKET_CONTAINER.get(userId).sendMessage(message);
        } else {
            log.error("用户" + userId + ",不在线！");
        }
    }

    public static synchronized int getOnlineCount() {
        return onlineCount;
    }

    public static synchronized void addOnlineCount() {
        WebSocketServer.onlineCount++;
    }

    public static synchronized void subOnlineCount() {
        WebSocketServer.onlineCount--;
    }
}