package com.softdev.system.demo.controller;

import com.softdev.system.demo.config.WebSocketServer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import java.io.IOException;

/**
 * WebSocketController
 * @author zhengkai.blog.csdn.net
 */
@RestController
public class DemoController {

    @GetMapping("index")
    public ResponseEntity<String> index(){
        return ResponseEntity.ok("请求成功");
    }

    @GetMapping("page")
    public ModelAndView page(){
        return new ModelAndView("websocket");
    }

    @RequestMapping("/push/{toUserId}")
    public ResponseEntity<String> pushToWeb(@RequestParam String message, @PathVariable String toUserId) throws IOException {
        WebSocketServer.sendInfo(message,toUserId);
        return ResponseEntity.ok("MSG SEND SUCCESS");
    }
}
