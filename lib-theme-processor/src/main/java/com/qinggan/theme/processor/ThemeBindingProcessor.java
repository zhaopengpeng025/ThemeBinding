package com.qinggan.theme.processor;

import com.qinggan.theme.annotation.BindColor;
import com.qinggan.theme.annotation.BindDrawable;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;

public class ThemeBindingProcessor extends AbstractProcessor {

  private final static String SUFFIX = "_ThemeBinding";

  private static final String CONTEXT = "android.content.Context";
  private static final String fieldContext = "mContext";
  private static final String fieldTarget = "mTarget";

  private static final String methodUpdateTheme = "updateTheme";
  private static final String methodInitVariable = "initVariable";
  private static final String methodUnBind = "unbind";

  private ClassName contextName = ClassName.get("android.content", "Context");
  private ClassName skinApplicationName = ClassName.get("com.qinggan.theme", "SkinQGApplication");
  private ClassName skinResourceName = ClassName.get("com.qinggan.theme", "SkinResourceManager");
  private ClassName iSkinUpdateName = ClassName.get("com.qinggan.theme", "ISkinUpdate");
  private ClassName unbinderName = ClassName.get("com.qinggan.theme.annotation", "Unbinder");
  private ClassName callSuperName = ClassName.get("androidx.annotation", "CallSuper");
  private ClassName logName = ClassName.get("android.util","Log");

  private Filer filer;
  private String tag;

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    filer = processingEnv.getFiler();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

    for (Element rootElement : roundEnv.getRootElements()) {
      processElement(rootElement,false);
    }
    return false;
  }

  private void processElement(Element rootElement,boolean isInnerClass) {
    Set<FieldSpec> fieldSpecs = new LinkedHashSet<>();
    Set<MethodSpec> methodSpecs = new LinkedHashSet<>();
    boolean hasBinding = false;
    String packageStr = rootElement.getEnclosingElement().toString();
    String classStr = rootElement.getSimpleName().toString();
    ClassName bindingClassName = ClassName.get(packageStr, classStr + SUFFIX);
    tag = bindingClassName.simpleName();

    //创建属性
    FieldSpec contextSpec = FieldSpec.builder(contextName, fieldContext,
                                              Modifier.PRIVATE).build();
    FieldSpec targetSpec = FieldSpec.builder(ClassName.get(packageStr, classStr),
                                             fieldTarget,
                                             Modifier.PRIVATE).build();

    //创建方法
    MethodSpec.Builder constructorBuilder = createConstructor(packageStr, classStr);
    MethodSpec.Builder updateThemeSBuild = MethodSpec.methodBuilder(methodUpdateTheme)
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC);
    //              .addStatement("$T.d($S,$S, new $T())",logName,tag,"updateTheme",Throwable.class);
    MethodSpec.Builder unbindBuilder = createMethodUnBind();
    unbindBuilder.addStatement("$T target = mTarget", ClassName.get(packageStr, classStr))
            .addStatement("if (target == null) throw new $T($S)", IllegalStateException.class,
                          "Bindings already cleared.");
    for (Element enclosedElement : rootElement.getEnclosedElements()) {
      if(enclosedElement.getKind() == ElementKind.CLASS){
        processElement(enclosedElement,true);
      }
      if (enclosedElement.getKind() == ElementKind.FIELD) {
        BindDrawable bindDrawable = enclosedElement.getAnnotation(BindDrawable.class);
        BindColor bindColor = enclosedElement.getAnnotation(BindColor.class);
        if (bindDrawable != null || bindColor != null) {
          hasBinding = true;
          Name targetField = enclosedElement.getSimpleName();
          TypeMirror typeMirror = enclosedElement.asType();
          boolean isLiveData = typeMirror.toString().contains("LiveData");
          if (isLiveData) {
            constructorBuilder.addStatement("target.$N = new $T()", targetField, typeMirror);
          }
          String statement;
          int resValue;
          if (bindColor != null) {
            statement = "$T.getInstance($N).getColor($L)";
            resValue = bindColor.value();
          } else {
            statement = "$T.getInstance($N).getDrawable($L)";
            resValue = bindDrawable.value();
          }
          if (isLiveData) {
            updateThemeSBuild.addStatement("$N.$N.setValue(" + statement + ")", targetSpec,
                                           targetField, skinResourceName,
                                           contextSpec, resValue);
          } else {
            updateThemeSBuild.addStatement("$N.$N = " + statement, targetSpec, targetField,
                                           skinResourceName,
                                           contextSpec, resValue);
          }
          if(typeMirror.getKind() == TypeKind.DECLARED){
            unbindBuilder.addStatement("target.$N = null", targetField);
          }else {
            unbindBuilder.addStatement("target.$N = 0", targetField);
          }
        }
      }
    }
    if (hasBinding) {
      assembleSpecs(fieldSpecs, methodSpecs, contextSpec, targetSpec, constructorBuilder,
                    updateThemeSBuild,unbindBuilder);
      if(isInnerClass){
        packageStr = packageStr.substring(0,packageStr.lastIndexOf("."));
      }
      createThemeBidingType(packageStr, bindingClassName, methodSpecs, fieldSpecs);
    }
  }

  private void assembleSpecs(Set<FieldSpec> fieldSpecs, Set<MethodSpec> methodSpecs,
                             FieldSpec contextSpec, FieldSpec targetSpec,
                             MethodSpec.Builder constructorBuilder,
                             MethodSpec.Builder updateThemeSBuild,
                             MethodSpec.Builder unbindSBuild) {
    fieldSpecs.add(contextSpec);
    fieldSpecs.add(targetSpec);
    constructorBuilder.addStatement("$N = context", contextSpec);
    constructorBuilder.addStatement("$N = target", targetSpec);
    constructorBuilder.addStatement("$N()", updateThemeSBuild.build());
    constructorBuilder.addStatement("$T.getInstance().addThemeListener(this)",
                                    skinApplicationName);

    methodSpecs.add(constructorBuilder.build());
    methodSpecs.add(updateThemeSBuild.build());
    methodSpecs.add(unbindSBuild.build());
  }

  private MethodSpec.Builder createMethodUnBind() {
    return MethodSpec.methodBuilder(methodUnBind)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override.class)
            .addAnnotation(callSuperName)
            .addStatement("$T.getInstance().removeThemeListener(this)", skinApplicationName);
  }

  private void createThemeBidingType(String packageStr,
                                     ClassName bindingClassName,
                                     Set<MethodSpec> methodSpecs,
                                     Set<FieldSpec> fieldSpecs) {
    try {
      TypeSpec builtClass = TypeSpec.classBuilder(bindingClassName)
              .addModifiers(Modifier.PUBLIC)
              .addFields(fieldSpecs)
              .addMethods(methodSpecs)
              .addSuperinterface(iSkinUpdateName)
              .addSuperinterface(unbinderName)
              .build();
      JavaFile.builder(packageStr, builtClass)
              .build().writeTo(filer);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }


  private MethodSpec.Builder createConstructor(String packageStr, String classStr) {
    return MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(ClassName.get(packageStr, classStr), "target")
            .addParameter(contextName, "context");
  }

  @Override
  public Set<String> getSupportedAnnotationTypes() {
    Set<String> set = new HashSet<>();
    set.add(BindColor.class.getCanonicalName());
    set.add(BindDrawable.class.getCanonicalName());
    return set;
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.RELEASE_7;
  }

  public static void main(String[] args) {
    String a = "aaa$ddd";

    System.out.println(a.lastIndexOf("$"));
  }
}