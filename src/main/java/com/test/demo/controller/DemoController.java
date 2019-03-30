package com.test.demo.controller;

import com.test.demo.service.DemoService;
import com.test.myv1.anno.Autowired;
import com.test.myv1.anno.Controller;
import com.test.myv1.anno.Param;
import com.test.myv1.anno.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Controller
@RequestMapping("/demo")
public class DemoController {
    @Autowired
    private DemoService demoService;

    @RequestMapping("/add")
    public int add(@Param("a") int a, @Param("b")int b){
        return demoService.add(a,b);
    }
    //V2版本中支持 正则表达式访问
    @RequestMapping("/hello*")
    public void add(@Param("name") String name , HttpServletRequest res, HttpServletResponse resp){
        String msg =   demoService.hello(name);
        try {
            resp.getWriter().write(msg);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}
