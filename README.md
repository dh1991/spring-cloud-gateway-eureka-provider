# spring-cloud-gateway-eureka-provider
springcloudgateway 路由


```主要功能: 同一接口 经过gateway+eureka 通过某一特定请求参数 分发到不同服务
例子: POST http://127.0.0.1:9083/providerA/get
      Content-Type: application/json
      { "type":"provider-b" }
      返回结果 {"testId":"{\n \"type\":\"provider-b\"\n}","value":"providerB"}
      请求落到 provider-b 
      当type = provider-a 时
      返回结果 {"testId":"{\n \"type\":\"provider-a\"\n}","value":"providerA"}
      请求落到 provider-a
```      
![描述](http://assets.processon.com/chart_image/5facfb2607912977fd4fff39.png?_=1611417462549)


