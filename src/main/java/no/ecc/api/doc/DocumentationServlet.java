package no.ecc.api.doc;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Path;

import no.ecc.api.doc.annotations.Api;

import org.apache.log4j.Logger;
import org.reflections.Reflections;
import org.reflections.scanners.FieldAnnotationsScanner;
import org.reflections.scanners.MemberUsageScanner;
import org.reflections.scanners.MethodAnnotationsScanner;
import org.reflections.scanners.MethodParameterNamesScanner;
import org.reflections.scanners.MethodParameterScanner;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;

import com.google.gson.stream.JsonWriter;

public class DocumentationServlet extends HttpServlet {

    /**
     * 
     */
    private static final long serialVersionUID = -844931992526985322L;
    
    private final static Logger log = Logger.getLogger(DocumentationServlet.class);
    
    private String url;
    private String scanLocation;

    @Override
    public void init(ServletConfig config) throws ServletException {        
        super.init(config);
        
        log.info("Documentation Servlet initialized");
        
        url = config.getInitParameter("url");
        scanLocation = config.getInitParameter("scan");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException,
            IOException {
        
        String context = req.getContextPath();
        
        resp.setContentType("application/json");
        JsonWriter out = new JsonWriter(resp.getWriter());
        out.beginObject();
        out.name("url").value(context + url);
        
        try {
        out.name("services");
        
        out.beginArray();
        
        List<ClassLoader> classLoadersList = new LinkedList<ClassLoader>();
        classLoadersList.add(ClasspathHelper.contextClassLoader());
        classLoadersList.add(ClasspathHelper.staticClassLoader());

        Reflections reflections = new Reflections(new ConfigurationBuilder()
        .setUrls(ClasspathHelper.forPackage(scanLocation))
        .filterInputsBy(new FilterBuilder().include(FilterBuilder.prefix(scanLocation)))
        .setScanners(
                new SubTypesScanner(false),
                new TypeAnnotationsScanner(),
                new FieldAnnotationsScanner(),
                new MethodAnnotationsScanner(),
                new MethodParameterScanner(),
                new MethodParameterNamesScanner(),
                new MemberUsageScanner()));
            
           

        Set<Class<?>> annotated = reflections.getTypesAnnotatedWith(Api.class, false);
        log.info("classes in " + scanLocation);
        
        for (Class c : annotated) {
            
            out.beginObject();
            
            Annotation annotation = c.getDeclaredAnnotation(Path.class);
            
            Method method = annotation.annotationType().getDeclaredMethod("value");
            String path = method.invoke(annotation, (Object[])null).toString();
            out.name("path").value(path);
            out.name("endpoint").value(context + path);
            out.name("name").value(path.substring(1).replace("/", "-"));
            
            Method[] methods = c.getMethods();
            
            out.name("endpoints");
            
            boolean isPost = false;
            
            out.beginArray();

            
            for (int i = 0; i < methods.length; i++) {
                Method m = methods[i];
                
                // only public methods allowed
                if (!Modifier.isPublic(m.getModifiers())) {
                    continue;
                }
                
                Annotation[] methodAnnotations = m.getDeclaredAnnotations();
                
                // must have annotations
                if (methodAnnotations.length == 0) {
                    continue;
                }
                
                out.beginObject();
                              
                for (int y = 0; y < methodAnnotations.length; y++) {
                    Annotation a = methodAnnotations[y];
                   
                    
                    String name = a.annotationType().getSimpleName();
                    String httpMethods = "GET,POST,PUT,OPTION,DELETE";
                    if (httpMethods.contains(name)) {
                        out.name("method").value(name);
                        isPost = name.equals("POST");
                    } else {
                        
                        Method declaredMethod = a.annotationType().getDeclaredMethod("value");
                        Object v = declaredMethod.invoke(a, (Object[])null);
                        
                        String value = "";
                        if (v instanceof Object[]) {
                            Object[] objArray = (Object[])v;
                            
                            String list = "";
                            for (Object o : objArray) {
                                if (list.length()>0) {
                                    list+=",";
                                }
                                
                                list += o.toString();
                            }
                            
                            value = list;
                        } else {
                            value = v.toString();
                        }
                        
                        out.name(name.toLowerCase()).value(value);
                    }
                    
                   
                    
                }
                
                out.name("parameters");
                out.beginArray();
                
                Class<?> requestType = null;
                
                for (int y = 0; y < m.getParameters().length; y++) {
                    out.beginObject();
                    Parameter p = m.getParameters()[y];
                    out.name("type").value(p.getType().getSimpleName());
                    
                    for (Annotation parameterAnnotation : p.getAnnotations()) {

                        out.name(parameterAnnotation.annotationType().getSimpleName());
                        Method declaredMethod = parameterAnnotation.annotationType().getDeclaredMethod("value");
                        Object v = declaredMethod.invoke(parameterAnnotation, (Object[])null);
                        out.value(v.toString());
                    }
                    
                    if (isPost && isGdsType(p.getType())) {
                        requestType = p.getType();
                        out.name("format");
                        createRequestFormat(out, requestType);
                    }
                    
                    out.endObject();
                }
                out.endArray();
                
                out.endObject();
                
            }
            
            out.endArray();
            
            out.endObject();
        }
        
        out.endArray();
        
        } catch (NoSuchMethodException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (SecurityException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        out.endObject();
        
        out.close();
    }

    private void createRequestFormat(JsonWriter writer, Class<?> type) throws IOException {
        writer.beginObject();
        
        for (Field f : type.getDeclaredFields()) {
            writer.name(f.getName()).value(f.getType().getSimpleName());
        }
        
        writer.endObject();
        
    }

    private boolean isGdsType(Class<?> type) {
        return type.getName().contains("no.ecc.gds");
    }
    
}
