package com.test.demo.service.impl;

import com.test.demo.service.DemoService;
import com.test.myv1.anno.Service;

@Service
public class DemoServiceImpl implements DemoService {
    public int add(int a, int b){
        return a+b;
    }

    @Override
    public String hello(String name) {
        return "Nice to meet you "+ name+" !";
    }
}
