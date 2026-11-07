package dev.mcpmcp.magic;

import java.lang.reflect.*;
import java.util.*;

/** Only internal, fixed target-JAR symbol names reach this helper. Never accepts MCP class/method names. */
final class Reflect {
    static Class<?> type(String name) throws ClassNotFoundException {return Class.forName(name,false,Reflect.class.getClassLoader());}
    static boolean is(Object o,String name) throws ClassNotFoundException{return o!=null&&type(name).isInstance(o);}
    static Object field(Object o,String name)throws ReflectiveOperationException{
        Class<?> c=o instanceof Class<?> t?t:o.getClass();return c.getField(name).get(o instanceof Class<?>?null:o);
    }
    static Object call(Object o,String name,Object... args)throws ReflectiveOperationException{
        Class<?> c=o instanceof Class<?> t?t:o.getClass();
        Method found=null;
        for(Method m:c.getMethods())if(m.getName().equals(name)&&m.getParameterCount()==args.length){
            Class<?>[] types=m.getParameterTypes();boolean match=true;
            for(int i=0;i<types.length;i++)if(args[i]!=null&&!boxed(types[i]).isInstance(args[i]))match=false;
            if(match){if(found!=null)throw new NoSuchMethodException("ambiguous_target_api_"+name);found=m;}
        }
        if(found==null)throw new NoSuchMethodException(c.getName()+"."+name);
        return found.invoke(o instanceof Class<?>?null:o,args);
    }
    private static Class<?> boxed(Class<?> c){if(c==int.class)return Integer.class;if(c==boolean.class)return Boolean.class;if(c==double.class)return Double.class;if(c==float.class)return Float.class;return c;}
    static int integer(Object o,String name)throws ReflectiveOperationException{return ((Number)call(o,name)).intValue();}
    static double number(Object o,String name)throws ReflectiveOperationException{return ((Number)call(o,name)).doubleValue();}
    static boolean bool(Object o,String name)throws ReflectiveOperationException{return (Boolean)call(o,name);}
    static List<?> list(Object o)throws ReflectiveOperationException{if(o instanceof Collection<?> c)return new ArrayList<>(c);if(o instanceof Object[] a)return Arrays.asList(a);throw new ReflectiveOperationException("expected_collection");}
    private Reflect(){}
}
