package com.example.demo.controller;

import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/providerB")
public class TestController {
    Logger logger = LoggerFactory.getLogger(TestController.class);
    @PostMapping("/get")
    public String get(@RequestBody String id){
        logger.info("有请求进来:"+id);
        JSONObject object = new JSONObject();
        object.put("testId",id);
        object.put("value","providerB");
        return object.toJSONString();
    }
}
