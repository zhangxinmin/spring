package com.test.myv2;

import com.test.myv1.anno.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
**spring mvc 版本1 实现功能
**1、自定义注解Controller Service Param Autowired RequestMapping
**2、根据配置文件扫描包下的类 如果有注解（Controller Service ） 则 将类型保存
**3、将第二步中的类进行实例化，并保存在实例容器中
**4、根据类的属性进行注入,如果有 Autowired 注解则 进行注入
**5、有Controller 注解的类的方法 如果有 RequestMapping 注解 则 将RequestMapping 中配置的路径 和方法对应关系 保存在容器中
**6、将该servlet配置到 web.xml 中，这样当有请求过来时就根据 访问路径 到 容器中 找到 对应的方法 ，然后解析参数执行 并将结果返回即可。
**spring mvc 版本2 实现功能
** 1、RequestMapping 路径支持正则
  * 2、url 参数支持强制类型转换
  *3、优化方法调用逻辑
  *
  * *
**/
public class DispatcherServlet extends HttpServlet {
    //保存配置内容
    private Properties contextConfig = new Properties();
    //扫描需要
    List<String> classNames = new ArrayList<>();
     //bean 容器
    Map<String,Object> ioc = new HashMap<String,Object>();
    //解析路径和方法关系容器  V1 版本使用 Map 保存 访问路径和执行方法的关系
    //Map<String,Method> handlerMappings = new HashMap<String,Method>();
    //V2版本使用List 保存 访问路径和执行方法的关系
     List<Handler> handlerMappings = new ArrayList<>();
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
        //获取到需要执行的方法
        Handler handler= getHandler(req);
        if(null==handler){
            resp.getWriter().write("404 not fund ");
            return;
        }
        Class[] paramTypes = handler.getMethod().getParameterTypes();
        //创建调用方法参数值数组
        Object[] args =new Object[paramTypes.length];
        Map<String,String[]> params=req.getParameterMap();
        //根据出入的参数 名 将 取值 转化为 对应的类型 并保存到 参数数组中的响应位置
        for(Map.Entry<String,String[]> entry:params.entrySet()){
            String value = Arrays.toString(entry.getValue()).replaceAll("\\[|\\]","")
                    .replaceAll("\\s","");
            String name = entry.getKey();
            if(handler.getParamIndexMapping().containsKey(name)){
                int index =handler.getParamIndexMapping().get(name);
                args[index]=convert(paramTypes[index],value);
            }
        }
        //如果方法需要 req he  res  则 传入
        if(handler.getParamIndexMapping().containsKey(HttpServletResponse.class.getName())){
            args[handler.getParamIndexMapping().get(HttpServletResponse.class.getName())]=resp;
        }
        if(handler.getParamIndexMapping().containsKey(HttpServletRequest.class.getName())){
            args[handler.getParamIndexMapping().get(HttpServletRequest.class.getName())]=req;
        }





         //执行方法
        Object res= null;
        try {
            res = handler.getMethod().invoke(handler.getController(),args);
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
        }else if(int.class.equals(clazz)){
            return Integer.parseInt(value);
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
    //根据请求 找到对应的 请求处理handler
    private Handler getHandler(HttpServletRequest req){
        String url= req.getRequestURI();
        String contextPath = req.getContextPath();
        url=url.replaceAll(contextPath,"").replaceAll("/+","/");
        for(Handler handler:handlerMappings){
            Matcher macher= handler.getPattern().matcher(url);
            if(!macher.matches()){
                continue;
            }
            return handler;
        }
        return null;
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
                Pattern pattern =Pattern.compile(url);
                handlerMappings.add(new Handler(method,entry.getValue(),pattern));
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



