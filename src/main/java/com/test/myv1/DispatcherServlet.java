package com.test.myv1;

import com.test.myv1.anno.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.util.*;

public class DispatcherServlet extends HttpServlet {
    //保存配置内容
    private Properties contextConfig = new Properties();
    //扫描需要
    List<String> classNames = new ArrayList<>();
     //bean 容器
    Map<String,Object> ioc = new HashMap<String,Object>();

    Map<String,Method> handlerMappings = new HashMap<String,Method>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doDispatch(req, resp);
    }

    /**
     * 逻辑处理方法
     * @param req
     * @param resp
     */
    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String url= req.getRequestURI();
        String contextPath = req.getContextPath();
        url=url.replaceAll(contextPath,"").replaceAll("/+","");
        if(!this.handlerMappings.containsKey(url)){
            resp.getWriter().write("404 not fund");
            return;
        }
        //获取到方法然后传参执行方法
        Method method = this.handlerMappings.get(url);
         //获取到方法所有的参数
         Parameter [] params =method.getParameters();
         //获取到传入的所有参数
         Map<String,String[]> paramMap = req.getParameterMap();
         //创建数组保存调用方法需要的参数
         Object[] args = new Object[params.length];
         for(int i=0;i<params.length;i++){
             Parameter param = params[i];
             //如果是req 或 resp 参数 则 直接 赋值
             if(param.getType().equals(HttpServletRequest.class)){
                 args[i]=req;
                 continue;
             }
             if(param.getType().equals(HttpServletResponse.class)){
                 args[i]=resp;
                 continue;
             }
             //获取参数名 并将将参数值保存到args 的相应位置
             String name = param.getName();
             if(param.isAnnotationPresent(Param.class)){
                 String  pname = param.getAnnotation(Param.class).value();
                 if(!"".equals(pname)){
                     name = pname;
                 }
             }
             String[] paraval=paramMap.get(name);
             args[i]=convert(param.getType(),Arrays.toString(paraval)
                     .replaceAll("\\[|\\]","")
                     .replaceAll("\\s",""));

         }
         //执行方法
         String beanName = toLowerFirstCase(method.getDeclaringClass().getSimpleName());
        Object res= null;
        try {
            res = method.invoke(ioc.get(beanName),args);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
        if(null!=res){
             resp.getWriter().write(res.toString());
         }
    }
    private Object convert(Class clazz,String value){
        if(Integer.class.equals(clazz)){
            return Integer.parseInt(value);
        }else if(String.class.equals(clazz)){
            return value;
        }
        return null;
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        //加载配置文件
        loadConfig(config.getInitParameter("contextConfigLocation"));

        //扫描相关的类
        scanClasses(contextConfig.getProperty("scanPackages"));

        //bean实例化
        initInstance();
        //bean 注入
        autowired();
        //初始化 HandlerMapping 
        initHandlerMapping();

    }

    private void autowired() {
        if(ioc.isEmpty())return;
        for(Map.Entry<String,Object> entry:ioc.entrySet()){
            Field[] fields= entry.getValue().getClass().getDeclaredFields();
            //如果属性被autowired注解修饰则 为属性赋值
            for(Field field:fields){
                if(!field.isAnnotationPresent(Autowired.class)){
                    continue;
                }
                //先从注解中获取要注入的实例名称，如果没有就适用 字段的类型作为名称 到容器中获取实例
                Autowired autowired=field.getAnnotation(Autowired.class);
                String beanName= autowired.value();
                if("".equals(beanName)){
                    beanName=field.getType().getName();
                }
                field.setAccessible(true);
                try {
                    //为属性赋值
                    field.set(entry.getValue(),ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }

    }
    private void initHandlerMapping() {
        if(ioc.isEmpty())return;
        for(Map.Entry<String,Object> entry:ioc.entrySet()){
            Class clazz= entry.getValue().getClass();
            if(!clazz.isAnnotationPresent(Controller.class)){
                continue;
            }
            //从 controller 的 RequestMapping 中 获取根路径信息
            String baseUrl="";
            if(clazz.isAnnotationPresent(RequestMapping.class)){
                RequestMapping controller = (RequestMapping) clazz.getAnnotation(RequestMapping.class);
                baseUrl=controller.value();
            }

            //获取所有方法 如果有 requstMapping  则 将方法保存
            for(Method method:clazz.getMethods()){
                if(!method.isAnnotationPresent(RequestMapping.class)){
                    continue;
                }
                RequestMapping requestMapping= method.getAnnotation(RequestMapping.class);
                String methodUrl = requestMapping.value();
                String url = (baseUrl+"/"+methodUrl).replaceAll("/+","/");
                handlerMappings.put(url,method);
                System.out.println("mapped :【"+url+":"+method+"】");
            }
        }
    }
    //根据扫描的class 实例化对象
    private void initInstance() {
        if(classNames.isEmpty())return;
        try {
            for(String className:classNames){
                Class clazz= Class.forName(className);
                if(clazz.isAnnotationPresent(Controller.class)){
                    Object ins=clazz.newInstance();
                    String beanName=toLowerFirstCase(clazz.getSimpleName());
                    ioc.put(beanName,ins);
                }else if(clazz.isAnnotationPresent(Service.class)){
                    Service service = (Service) clazz.getAnnotation(Service.class);
                    //从 service 标签中 获取 示例的名字 如果没有则适用 类名首字母小写
                    String beanName= service.value();
                    if("".equals(beanName)){
                        beanName=toLowerFirstCase(clazz.getSimpleName());
                    }
                    Object ins= clazz.newInstance();
                    ioc.put(beanName,ins);
                    //因为注入时往往是根据接口注入的，所以 还需要 将改bean和她实现的接口做关联
                    for(Class i:clazz.getInterfaces()){
                        if(ioc.containsKey(i.getName())){
                            throw new Exception("name 【"+i.getName()+"】已经存在");
                        }
                        ioc.put(i.getName(),ins);
                    }
                }else{
                    continue;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /*
    //之所以加，是因为大小写字母的ASCII 码相差32，
    // 而且大写字母的ASCII 码要小于小写字母的ASCII 码
    //在Java 中，对char 做算学运算，实际上就是对ASCII 码做算学运算
    * */
    private String toLowerFirstCase(String simpleName) {
        if(null==simpleName||simpleName.length()==0)return "";
        char[] chars= simpleName.toCharArray();
        chars[0]+=32;
        return String.valueOf(chars);
    }

    //扫描需要实例化的类 并保存
    private void scanClasses(String scanPackages) {
        URL url =  this.getClass().getClassLoader().getResource(scanPackages.replaceAll("\\.","/"));
        File root = new File(url.getFile());
        for(File file:root.listFiles()){
            if(file.isDirectory()){
                scanClasses(scanPackages+"."+file.getName());
            }else{
                if(!file.getName().endsWith(".class")){
                    continue;
                }
                String className =(scanPackages+"."+file.getName().replace(".class",""));
                classNames.add(className);
            }
        }
    }

    //加载配置文件
    private void loadConfig(String contextConfigLocation) {
        InputStream is= this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
        try {
            contextConfig.load(is);
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            if(is!=null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
