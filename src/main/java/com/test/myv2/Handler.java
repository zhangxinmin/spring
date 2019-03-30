package com.test.myv2;

import com.test.myv1.anno.Param;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.annotation.Annotation;
import java.util.*;

import java.lang.reflect.Method;
import java.util.regex.Pattern;

public class Handler {
    private Object controller;//处理请求的 controller 实例
    private Method method;//调用的方法
    private Pattern pattern;//请求的路径 支持正则
    private Map<String,Integer> paramIndexMapping;//保存方法参数顺序 简化调用逻辑


    public Handler(Method method, Object controller,Pattern pattern) {
        this.method = method;
        this.pattern = pattern;
        this.controller=controller;
        paramIndexMapping=new HashMap<String, Integer>();
        initMethodParam(method);
    }

    private void initMethodParam(Method method) {
        //提取方法中加了注解的参数  每个参数可能有多个注解 多以是二位数组
        Annotation[] [] pa = method.getParameterAnnotations();

        for(int i=0;i<pa.length;i++){
            for(Annotation anno:pa[i]){
                if(anno instanceof Param){
                    String pname= ((Param) anno).value();
                    if(!"".equals(pname)){
                        //将参数名 和 参数坐标 保存
                        paramIndexMapping.put(pname,i);
                    }
                }
            }
        }

        //由于req 和 res 这两个参数不是通过 浏览器传入的 所以无法通过名称来 获取值。所以这里使用 class名来确定位置
        Class [] paramTypes = method.getParameterTypes();
        for(int i=0;i<paramTypes.length;i++){
            Class clazz = paramTypes[i];
            if(clazz.equals(HttpServletRequest.class)||clazz.equals(HttpServletResponse.class)){
                paramIndexMapping.put(clazz.getName(),i);
            }
        }

    }

    public Object getController() {
        return controller;
    }

    public void setController(Object controller) {
        this.controller = controller;
    }

    public Method getMethod() {
        return method;
    }

    public void setMethod(Method method) {
        this.method = method;
    }

    public Pattern getPattern() {
        return pattern;
    }

    public void setPattern(Pattern pattern) {
        this.pattern = pattern;
    }

    public Map<String, Integer> getParamIndexMapping() {
        return paramIndexMapping;
    }

    public void setParamIndexMapping(Map<String, Integer> paramIndexMapping) {
        this.paramIndexMapping = paramIndexMapping;
    }
}
