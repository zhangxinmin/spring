package com.test.demo.controller;

import com.test.demo.service.DemoService;
import com.test.myv1.anno.Autowired;
import com.test.myv1.anno.Controller;
import com.test.myv1.anno.RequestMapping;

@Controller
@RequestMapping("/demo")
public class DemoController {
    @Autowired
    private DemoService demoService;

    @RequestMapping("/add")
    public int add(int a,int b){
        return demoService.add(a,b);
    }


}
