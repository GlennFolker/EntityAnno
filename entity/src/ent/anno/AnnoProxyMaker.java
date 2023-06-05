package ent.anno;

import arc.func.*;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import sun.reflect.annotation.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;
import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.*;

/**
 * Reliably generates a proxy handling an annotation type from model elements.
 * @author Anuke
 */
@SuppressWarnings("unchecked")
public class AnnoProxyMaker{
    private final Attribute.Compound anno;
    private final Class<? extends Annotation> type;

    private AnnoProxyMaker(Attribute.Compound anno, Class<? extends Annotation> type){
        this.anno = anno;
        this.type = type;
    }

    public static <A extends Annotation> A generate(Attribute.Compound anno, Class<A> type){
        if(anno == null) return null;
        return type.cast(new AnnoProxyMaker(anno, type).generate());
    }

    private Annotation generate(){
        return AnnotationParser.annotationForMap(type, getAllReflectedValues());
    }

    private Map<String, Object> getAllReflectedValues(){
        Map<String, Object> res = new LinkedHashMap<>();

        for(var entry : getAllValues().entrySet()){
            var meth = entry.getKey();
            var value = generateValue(meth, entry.getValue());
            if(value != null){
                res.put(meth.name.toString(), value);
            }
        }

        return res;
    }

    private Map<MethodSymbol, Attribute> getAllValues(){
        Map<MethodSymbol, Attribute> map = new LinkedHashMap<>();
        var cl = (ClassSymbol)anno.type.tsym;

        try{
            var entryClass = Class.forName("com.sun.tools.javac.code.Scope$Entry");
            var siblingField = entryClass.getField("sibling");
            var symField = entryClass.getField("sym");

            var members = cl.members();
            var field = members.getClass().getField("elems");
            var elems = field.get(members);

            for(var currEntry = elems; currEntry != null; currEntry = siblingField.get(currEntry)){
                handleSymbol((Symbol)symField.get(currEntry), map);
            }
        }catch(Throwable e){
            try{
                var lookupClass = Class.forName("com.sun.tools.javac.code.Scope$LookupKind");
                var nonRecField = lookupClass.getField("NON_RECURSIVE");
                var nonRec = nonRecField.get(null);

                var scope = cl.members();
                var getSyms = scope.getClass().getMethod("getSymbols", lookupClass);
                var it = (Iterable<Symbol>)getSyms.invoke(scope, nonRec);
                for(Symbol symbol : it){
                    handleSymbol(symbol, map);
                }
            }catch(Throwable death){
                throw new RuntimeException(death);
            }
        }

        for(var pair : anno.values){
            map.put(pair.fst, pair.snd);
        }

        return map;
    }

    private <T extends Symbol> void handleSymbol(Symbol sym, Map<T, Attribute> map){
        if(sym.getKind() == ElementKind.METHOD){
            var meth = (MethodSymbol)sym;

            var def = meth.getDefaultValue();
            if(def != null) map.put((T)meth, def);
        }
    }

    private Object generateValue(MethodSymbol meth, Attribute attrib){
        return new ValueVisitor(meth).getValue(attrib);
    }

    private class ValueVisitor implements Attribute.Visitor{
        private final MethodSymbol meth;
        private Class<?> returnClass;
        private Object value;

        ValueVisitor(MethodSymbol meth){
            this.meth = meth;
        }

        Object getValue(Attribute attrib){
            Method meth;
            try{
                meth = type.getMethod(this.meth.name.toString());
            }catch(NoSuchMethodException e){
                return null;
            }

            returnClass = meth.getReturnType();
            attrib.accept(this);

            if(!(value instanceof ExceptionProxy) && !AnnotationType.invocationHandlerReturnType(returnClass).isInstance(value)) typeMismatch(meth, attrib);
            return value;
        }

        @Override
        public void visitConstant(Attribute.Constant constant){
            value = constant.getValue();
        }

        @Override
        public void visitClass(Attribute.Class type){
            value = mirrorProxy(type.classType);
        }

        @Override
        public void visitArray(Attribute.Array arr){
            Name name = ((Type.ArrayType)arr.type).elemtype.tsym.getQualifiedName();

            if(name.equals(name.table.names.java_lang_Class)){
                ListBuffer<Type> list = new ListBuffer<>();
                for(var attrib : arr.values){
                    Type type = attrib instanceof Attribute.UnresolvedClass ? ((Attribute.UnresolvedClass)attrib).classType :
                    ((Attribute.Class)attrib).classType;

                    list.append(type);
                }

                value = mirrorProxy(list.toList());
            }else{
                Class<?> arrType = returnClass;
                returnClass = returnClass.getComponentType();

                try{
                    var inst = Array.newInstance(returnClass, arr.values.length);
                    for(int i = 0; i < arr.values.length; i++){
                        arr.values[i].accept(this);
                        if(value == null || value instanceof ExceptionProxy) return;

                        try{
                            Array.set(inst, i, value);
                        }catch(IllegalArgumentException e){
                            value = null;
                            return;
                        }
                    }

                    value = inst;
                }finally{
                    returnClass = arrType;
                }
            }
        }

        @Override
        @SuppressWarnings("rawtypes")
        public void visitEnum(Attribute.Enum enumType){
            if(returnClass.isEnum()){
                var name = enumType.value.toString();
                try{
                    value = Enum.valueOf((Class)returnClass, name);
                }catch(IllegalArgumentException e){
                    value = proxify(() -> new EnumConstantNotPresentException((Class)returnClass, name));
                }
            }else{
                value = null;
            }
        }

        @Override
        public void visitCompound(Attribute.Compound anno){
            try{
                var type = returnClass.asSubclass(Annotation.class);
                value = generate(anno, type);
            }catch(ClassCastException e){
                value = null;
            }
        }

        @Override
        public void visitError(Attribute.Error err){
            if(err instanceof Attribute.UnresolvedClass){
                value = mirrorProxy(((Attribute.UnresolvedClass)err).classType);
            }else{
                value = null;
            }
        }

        private void typeMismatch(Method meth, Attribute attrib){
            value = proxify(() -> new AnnotationTypeMismatchException(meth, attrib.type.toString()));
        }
    }

    private static Object mirrorProxy(Type t){
        return proxify(() -> new MirroredTypeException(t));
    }

    private static Object mirrorProxy(List<Type> t){
        return proxify(() -> new MirroredTypesException(t));
    }

    private static <T extends Throwable> Object proxify(Prov<T> prov){
        try{
            return new ExceptionProxy(){
                private static final long serialVersionUID = 1L;

                @Override
                protected RuntimeException generateException(){
                    return (RuntimeException)prov.get();
                }
            };
        }catch(Throwable t){
            throw new RuntimeException(t);
        }
    }
}
